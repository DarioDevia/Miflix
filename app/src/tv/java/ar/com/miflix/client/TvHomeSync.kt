package ar.com.miflix.client

import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.media.tv.TvContract
import android.util.Log
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

import android.content.Context
import com.google.gson.Gson
import java.time.LocalDate
/** App-private service object, not an Android Service. Only TV references this implementation. */
internal object TvHomeSync {
    private val writes=Mutex()
    private val gate=HomeSyncGate()
    private var verifiedChannel:Long?=null
    suspend fun sync(context:Context,catalog:Catalog):HomeSyncResult = withContext(Dispatchers.IO) {
        writes.withLock {
            // The catalog was already downloaded/validated by the normal repository. No HTTP here.
            val today=LocalDate.now()
            var attemptKey=homeProbeHash(Gson().toJson(catalog)+today)
            if(!gate.shouldAttempt(attemptKey,System.currentTimeMillis()))
                return@withLock HomeSyncResult(verifiedChannel,"SIN_CAMBIOS_O_EN_ESPERA")
            try {
                val file=File(context.filesDir,"catalogo.json")
                check(file.length()<=6L*1024*1024)
                val raw=file.readText()
                attemptKey=homeProbeHash(raw+today)
                if(!gate.shouldAttempt(attemptKey,System.currentTimeMillis()))
                    return@withLock HomeSyncResult(verifiedChannel,"SIN_CAMBIOS_O_EN_ESPERA")
                withTimeout(45_000L) {
                    val snapshot=homeProbeParseCatalog(raw)
                    check(snapshot.titles==catalog.items.orEmpty()) { "Snapshot changed; keep published set" }
                    val selection=homeProbeCards(snapshot,today)
                    if(selection.cards.isEmpty()) {
                        Log.i("MiFlixHome","SELECCION_VACIA_CONSERVADA")
                        gate.failed(attemptKey,System.currentTimeMillis())
                        return@withTimeout HomeSyncResult(null,"SELECCION_VACIA_CONSERVADA")
                    }
                    val result=TvHomePublisher(context).publish(selection)
                    verifiedChannel=result.requestVisibility
                    gate.succeeded(attemptKey)
                    result
                }
            } catch(timeout:TimeoutCancellationException) {
                gate.failed(attemptKey,System.currentTimeMillis())
                Log.i("MiFlixHome","SINCRONIZACION_ESPERA_AGOTADA; noSeAutorizaLimpiezaAdicional=true")
                HomeSyncResult(null,"ESPERA_AGOTADA")
            } catch(cancelled:CancellationException) { throw cancelled }
              catch(failure:Exception) {
                gate.failed(attemptKey,System.currentTimeMillis())
                Log.i("MiFlixHome","SINCRONIZACION_OMITIDA tipo=${failure.javaClass.simpleName}; noSeAutorizaLimpiezaAdicional=true")
                HomeSyncResult(null,"PROVEEDOR_O_CATALOGO_NO_DISPONIBLE")
            }
        }
    }
    fun requestVisibility(context:Context,id:Long) {
        try {
            val prefs=context.getSharedPreferences("miflix_home_probe",Context.MODE_PRIVATE)
            if(runCatching { prefs.getLong("visibility_requested",-1) }.getOrDefault(-1)==id) return
            // One standard API attempt per channel; never fight the user's later choice to hide it.
            prefs.edit().putLong("visibility_requested",id).apply()
            TvContractCompat.requestChannelBrowsable(context,id)
            Log.i("MiFlixHome","VISIBILIDAD_SOLICITADA id=$id; presentacionControladaPorLauncher=true")
        } catch(failure:Exception) {
            Log.i("MiFlixHome","VISIBILIDAD_NO_DISPONIBLE tipo=${failure.javaClass.simpleName}")
        }
    }

}
internal data class HomeSyncResult(val requestVisibility:Long?,val status:String)
private class TvHomePublisher(private val context:Context) {
    private val packageName get()=context.packageName
    private val filesDir get()=context.filesDir
    private val contentResolver get()=context.contentResolver
    private val prefs=context.getSharedPreferences("miflix_home_probe",Context.MODE_PRIVATE)
    private fun appIntent(titleId: String? = null): Intent = Intent(context,MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .apply { if(titleId!=null) {
            check(homeEntryId(HOME_DETAIL_ACTION,titleId)!=null)
            action=HOME_DETAIL_ACTION
            putExtra(HOME_DETAIL_EXTRA,titleId)
        } }

    private data class OwnChannel(val id:Long,val name:String?,val marker:String?,val browsable:Boolean)
    private fun ownChannels(): List<OwnChannel> {
        val result=mutableListOf<OwnChannel>()
        val columns=arrayOf("_id","package_name","type","display_name","internal_provider_id","browsable")
        val cursor=contentResolver.query(TvContractCompat.Channels.CONTENT_URI,columns,null,null,null)
            ?: error("Provider returned no cursor")
        cursor.use { c -> while(c.moveToNext()) {
            if(c.getString(1)==packageName && c.getString(2)==TvContractCompat.Channels.TYPE_PREVIEW)
                result += OwnChannel(c.getLong(0),c.getString(3),c.getString(4),c.getInt(5)==1)
        } }
        return result
    }
    private fun findChannel(channels: List<OwnChannel>): OwnChannel? {
        val saved=runCatching { prefs.getLong("channel_id",-1) }.getOrDefault(-1)
        val selected=homeProbeChannel(channels.map {
            HomeProbeChannel(it.id,it.name,it.marker,packageName,true)
        },packageName,saved)
        return channels.firstOrNull { it.id==selected?.id }
    }
    private fun ensureChannel(): Pair<Long,Boolean> {
        val all=ownChannels()
        val old=findChannel(all)
        val values=Channel.Builder().setType(TvContractCompat.Channels.TYPE_PREVIEW).setDisplayName(HOME_PROBE_DISPLAY_NAME)
            .setAppLinkIntentUri(Uri.parse(appIntent().toUri(Intent.URI_INTENT_SCHEME))).build().toContentValues()
            .apply { put("internal_provider_id",HOME_PROBE_CHANNEL) }
        val id=if(old!=null) old.id else {
            // Don't create a second channel if the unseen local prototype used an unknown name.
            check(all.isEmpty()) { "Unrecognized own channel; inspect before adopting" }
            ContentUris.parseId(contentResolver.insert(TvContractCompat.Channels.CONTENT_URI,values)
                ?: error("No channel URI"))
        }
        if(old!=null && (old.marker!=HOME_PROBE_CHANNEL || old.name!=HOME_PROBE_DISPLAY_NAME))
            check(contentResolver.update(TvContractCompat.buildChannelUri(id),values,null,null)==1)
        if(prefs.getLong("channel_id",-1)!=id) prefs.edit().putLong("channel_id",id).apply()
        if(old==null || prefs.getLong("logo_channel",-1)!=id) {
            val image=Bitmap.createBitmap(240,240,Bitmap.Config.ARGB_8888)
            try {
                Canvas(image).apply { drawColor(Color.rgb(20,24,32));drawText("MiFlix",22f,135f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.RED;textSize=58f }) }
                if(ChannelLogoUtils.storeChannelLogo(context,id,image))
                    prefs.edit().putLong("logo_channel",id).apply()
            } finally { image.recycle() }
        }
        return id to (old==null)
    }
    private fun programRows(channel:Long, payloadColumns:Set<String> = emptySet()): List<HomeProbeStored> {
        val columns=(listOf("_id","package_name","internal_provider_id") + payloadColumns).distinct().toTypedArray()
        val result=mutableListOf<HomeProbeStored>()
        val cursor=contentResolver.query(TvContractCompat.buildPreviewProgramsUriForChannel(channel),columns,null,null,null)
            ?: error("Provider returned no cursor")
        cursor.use { c -> while(c.moveToNext()) {
            if(c.getString(c.getColumnIndexOrThrow("package_name"))!=packageName)continue
            result += HomeProbeStored(c.getLong(c.getColumnIndexOrThrow("_id")),
                c.getString(c.getColumnIndexOrThrow("internal_provider_id")),columns.associateWith { name ->
                    val index=c.getColumnIndexOrThrow(name);if(c.isNull(index))null else c.getString(index)
                })
        } }
        return result
    }

    suspend fun publish(selection:HomeProbeSelection):HomeSyncResult {
        val cards=selection.cards
        check(cards.isNotEmpty())
        check(cards.all { homeEntryId(HOME_DETAIL_ACTION,it.titleId)!=null })
        currentCoroutineContext().ensureActive()
        val (channel,created)=ensureChannel()
        val artwork=TvHomeArtwork(context)
        val existing=programRows(channel,setOf(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI))
        artwork.clean(existing.mapNotNull { it.payload[TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI] }.toSet())
        val values=cards.mapIndexed { index,card ->
            currentCoroutineContext().ensureActive()
            PreviewProgram.Builder().setChannelId(channel).setWeight(HOME_PROBE_LIMIT-index).build().toContentValues().apply {
                put(TvContract.PreviewPrograms.COLUMN_TYPE,if(card.type=="pelicula") TvContract.PreviewPrograms.TYPE_MOVIE else TvContract.PreviewPrograms.TYPE_TV_SERIES)
                put(TvContract.PreviewPrograms.COLUMN_TITLE,card.title)
                put(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION,card.description)
                put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,artwork.image(card).toString())
                put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,TvContract.PreviewPrograms.ASPECT_RATIO_16_9)
                put(TvContract.PreviewPrograms.COLUMN_INTENT_URI,appIntent(requireNotNull(card.titleId)).toUri(Intent.URI_INTENT_SCHEME))
                put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID,card.key)
            }
        }
        val desired=values.mapIndexed { index,value -> HomeProbeDesired(cards[index].key,value.valueSet().associate { it.key to it.value?.toString() }) }
        val columns=desired.flatMap { it.payload.keys }.toSet()+TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI
        val coroutine=currentCoroutineContext()
        val applied=homeApplyVerified(object:HomeProgramStore {
            override fun rows()=programRows(channel,columns)
            override fun upsert(change:HomeProbeUpsert) {
                val value=values[desired.indexOf(change.desired)]
                if(change.existingId==null) check(contentResolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI,value)!=null)
                else check(contentResolver.update(TvContractCompat.buildPreviewProgramUri(change.existingId),value,null,null)==1)
            }
            override fun delete(id:Long) { check(contentResolver.delete(TvContractCompat.buildPreviewProgramUri(id),null,null)==1) }
        },desired) { coroutine.ensureActive() }
        artwork.clean(applied.rows.mapNotNull { it.payload[TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI] }.toSet())
        Log.i("MiFlixHome","CANAL id=$channel creado=$created; PROGRAMAS_VERIFICADOS propios=${cards.size} faltan=${selection.missingSlots}; CAMBIOS upserts=${applied.plan.upserts.size} eliminados=${applied.plan.deleteIds.size}")
        return HomeSyncResult(channel,"PUBLICADO")
    }
}
