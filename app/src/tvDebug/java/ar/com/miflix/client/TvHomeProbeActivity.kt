package ar.com.miflix.client

import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Manual debug-only diagnostic. Stable MainActivity/navigation/playback are untouched. */
class TvHomeProbeActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MiFlixHomeProbe"
        private val writes = Mutex()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var output: TextView
    private val controls = mutableListOf<Button>()
    private val prefs by lazy { getSharedPreferences("miflix_home_probe", MODE_PRIVATE) }
    private var currentChannel: Long? = null
    private val confirm = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        status("CONFIRMACION_RESULTADO code=${result.resultCode}; no demuestra visibilidad en Home")
        runProbe { inspect() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(32, 20, 32, 20); setBackgroundColor(Color.rgb(11,13,18))
        }
        output = TextView(this).apply { textSize = 18f; setTextColor(Color.WHITE) }
        val scroll = ScrollView(this).apply { addView(output) }
        root.addView(TextView(this).apply {
            text="MiFlix — sonda Home (sólo debug)"; textSize=24f;setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text="Registrar/publicar NO prueba aparición en LauncherX. Las tarjetas abren MiFlix, no la ficha.";setTextColor(Color.LTGRAY)
        })
        fun action(label: String, callback: () -> Unit) {
            val button=Button(this).apply { text=label;setOnClickListener { callback() } }
            controls += button; root.addView(button,ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        action("Publicar / actualizar 5 tarjetas") { runProbe { publish() } }
        action("Consultar canal y programas desde MiFlix") { runProbe { inspect() } }
        action("Solicitar visibilidad (API)") { requestVisibility(false) }
        action("Solicitar confirmación del sistema") { requestVisibility(true) }
        action("Abrir Home del sistema") {
            runCatching { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) }
                .onFailure { status("HOME_NO_DISPONIBLE tipo=${it.javaClass.simpleName}") }
        }
        action("Abrir MiFlix (prueba intermedia)") { startActivity(appIntent()) }
        root.addView(scroll,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        setContentView(root); controls.first().requestFocus()
        status("SONDA_INICIADA api=${android.os.Build.VERSION.SDK_INT}; sin acceso a credenciales")
        when(intent.getStringExtra("probe_action")) {
            "publish" -> runProbe { publish() }
            "request" -> requestVisibility(false)
            "confirm" -> requestVisibility(true)
            else -> runProbe { inspect() }
        }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    private fun status(message: String) {
        Log.i(TAG,message)
        output.append(message+"\n")
    }
    private fun runProbe(block: suspend () -> String) {
        if (controls.any { !it.isEnabled }) return
        controls.forEach { it.isEnabled=false }
        scope.launch {
            try { status(withContext(Dispatchers.IO) { writes.withLock { block() } }) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                // Never log exception messages, URLs, raw catalog, title IDs or Telegram data.
                status("SONDA_ERROR tipo=${failure.javaClass.simpleName}; no implica borrado de canal")
            } finally { controls.forEach { it.isEnabled=true } }
        }
    }
    private fun appIntent(cardKey: String? = null): Intent = Intent(this,MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        .apply { if(cardKey!=null) putExtra("miflix_home_probe_key",cardKey) }

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
        val eligible=channels.filter { it.marker==HOME_PROBE_CHANNEL || it.name=="MiFlix" }
        val saved=prefs.getLong("channel_id",-1)
        return eligible.firstOrNull { it.id==saved } ?: eligible.sortedWith(
            compareBy<OwnChannel> { it.marker!=HOME_PROBE_CHANNEL }.thenBy { it.id }).firstOrNull()
    }
    private fun ensureChannel(): Pair<Long,Boolean> {
        val all=ownChannels()
        val old=findChannel(all)
        val values=Channel.Builder().setType(TvContractCompat.Channels.TYPE_PREVIEW).setDisplayName("MiFlix")
            .setAppLinkIntentUri(Uri.parse(appIntent().toUri(Intent.URI_INTENT_SCHEME))).build().toContentValues()
            .apply { put("internal_provider_id",HOME_PROBE_CHANNEL) }
        val id=if(old!=null) old.id else {
            // Don't create a second channel if the unseen local prototype used an unknown name.
            check(all.isEmpty()) { "Unrecognized own channel; inspect before adopting" }
            ContentUris.parseId(contentResolver.insert(TvContractCompat.Channels.CONTENT_URI,values)
                ?: error("No channel URI"))
        }
        if(old!=null && old.marker!=HOME_PROBE_CHANNEL)
            check(contentResolver.update(TvContractCompat.buildChannelUri(id),values,null,null)==1)
        prefs.edit().putLong("channel_id",id).apply()
        if(prefs.getLong("logo_channel",-1)!=id) {
            val image=Bitmap.createBitmap(240,240,Bitmap.Config.ARGB_8888)
            try {
                Canvas(image).apply { drawColor(Color.rgb(20,24,32));drawText("MiFlix",22f,135f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.RED;textSize=58f }) }
                if(ChannelLogoUtils.storeChannelLogo(applicationContext,id,image))
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
    private suspend fun cards(): Pair<List<HomeProbeCard>,String> {
        val cached=CatalogRepository(applicationContext).loadCached()
        if(cached!=null && cached.items.orEmpty().isNotEmpty()) return homeProbeCards(cached.items) to "CACHE_LOCAL"
        val downloaded=try {
            val client=OkHttpClient.Builder().callTimeout(20,TimeUnit.SECONDS).build()
            val raw=client.newCall(Request.Builder().url(CatalogRepository.DEFAULT_URL).build()).execute().use {
                check(it.isSuccessful);it.body?.string() ?: error("Empty response")
            }
            require(raw.length<=6*1024*1024)
            CatalogRepository.parseCatalog(raw)
        } catch(cancelled:CancellationException) { throw cancelled }
          catch(_:Exception) { null }
        return homeProbeCards(downloaded?.items.orEmpty()) to if(downloaded!=null) "CATALOGO_PUBLICO" else "SOLO_FIXTURES"
    }
    private fun image(card: HomeProbeCard): Uri {
        card.image?.let { return Uri.parse(it) }
        val folder=File(filesDir,"tv_home_probe_art").apply { check(isDirectory || mkdirs()) }
        val filename=homeProbeHash(card.key+":"+card.title+":"+card.synthetic)+".png"
        val file=File(folder,filename)
        if(!file.isFile) {
            val bitmap=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888)
            try {
                Canvas(bitmap).apply {
                    drawColor(Color.rgb(17,23,34));val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;textSize=42f }
                    drawText("MiFlix · Prueba de Home",40f,140f,paint)
                    drawText(card.title.take(32),40f,270f,paint)
                    paint.textSize=26f;paint.color=Color.LTGRAY
                    drawText(if(card.synthetic)"DATOS SINTÉTICOS" else "Imagen de prueba · sin fondo publicado",40f,380f,paint)
                }
                val pending=File(folder,filename+".tmp")
                pending.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
                check(pending.renameTo(file))
            } finally { bitmap.recycle() }
        }
        return Uri.Builder().scheme("content").authority(packageName+".homeprobe.images").appendPath(filename).build()
    }
    private suspend fun publish(): String {
        val (cards,source)=cards()
        currentCoroutineContext().ensureActive()
        val (channel,created)=ensureChannel()
        val values=cards.mapIndexed { index, card -> PreviewProgram.Builder().setChannelId(channel)
            .setType(if(card.type=="pelicula")TvContractCompat.PreviewPrograms.TYPE_MOVIE else TvContractCompat.PreviewPrograms.TYPE_TV_SERIES)
            .setTitle(card.title).setDescription(card.description).setPosterArtUri(image(card))
            .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
            .setIntentUri(Uri.parse(appIntent(card.key).toUri(Intent.URI_INTENT_SCHEME)))
            .setInternalProviderId(card.key).setWeight(5-index).build().toContentValues() }
        val desired=values.mapIndexed { index, value -> HomeProbeDesired(cards[index].key,
            value.valueSet().associate { it.key to it.value?.toString() }) }
        val columns=desired.flatMap { it.payload.keys }.toSet()
        val plan=homeProbePlan(programRows(channel,columns),desired)
        for(change in plan.upserts) {
            currentCoroutineContext().ensureActive()
            val value=values[desired.indexOf(change.desired)]
            if(change.existingId==null) check(contentResolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI,value)!=null)
            else check(contentResolver.update(TvContractCompat.buildPreviewProgramUri(change.existingId),value,null,null)==1)
        }
        for(id in plan.deleteIds) {
            currentCoroutineContext().ensureActive()
            check(contentResolver.delete(TvContractCompat.buildPreviewProgramUri(id),null,null)==1)
        }
        val after=programRows(channel,columns)
        val remaining=homeProbePlan(after,desired)
        check(remaining.upserts.isEmpty() && remaining.deleteIds.isEmpty())
        // Remove obsolete generated images only after a successful provider reconciliation.
        val retained=after.mapNotNull { it.payload["poster_art_uri"] }.mapNotNull { raw ->
            Uri.parse(raw).takeIf { it.authority==packageName+".homeprobe.images" }?.lastPathSegment
        }.toSet()
        File(filesDir,"tv_home_probe_art").listFiles().orEmpty().filter {
            Regex("[a-f0-9]{64}\\.png").matches(it.name) && it.name !in retained
        }.forEach { it.delete() }
        val count=after.count { it.key?.startsWith(HOME_PROBE_PREFIX)==true };check(count==5)
        val visible=ownChannels().firstOrNull { it.id==channel }?.browsable
        currentChannel=channel
        return "${if(created)"CANAL_CREADO" else "CANAL_EXISTENTE"} id=$channel\n"+
            "PROGRAMAS_VERIFICADOS propios=$count reales=${cards.count { !it.synthetic }} sinteticos=${cards.count { it.synthetic }} fuente=$source\n"+
            "CAMBIOS upserts=${plan.upserts.size} eliminadosSonda=${plan.deleteIds.size} browsable=$visible\n"+
            "NIVEL_3 sólo si estos registros fueron verificados; Home y tarjeta no observados."
    }
    private fun inspect(): String {
        val channels=ownChannels();val channel=findChannel(channels)
            ?: return "CONSULTA_PROPIA canalesPreview=${channels.size}; canal reconocido ausente (no se borra ni crea nada)"
        currentChannel=channel.id
        val rows=programRows(channel.id)
        val owned=rows.filter { it.key?.startsWith(HOME_PROBE_PREFIX)==true }
        return "CANAL_EXISTENTE id=${channel.id}\nCONSULTA_PROPIA canalesPreview=${channels.size} browsable=${channel.browsable}"+
            " programasSonda=${owned.size} duplicados=${owned.size-owned.map { it.key }.distinct().size}"+
            " otrosProgramas=${rows.size-owned.size}\nVISIBILIDAD_EN_HOME=NO_OBSERVADA"
    }
    private fun requestVisibility(interactive:Boolean) {
        if(controls.any { !it.isEnabled })return
        controls.forEach { it.isEnabled=false }
        scope.launch {
            try {
                val channel=withContext(Dispatchers.IO) { writes.withLock { findChannel(ownChannels())?.id } }
                    ?: run { status("SOLICITUD_OMITIDA: publicar o recuperar el canal primero");return@launch }
                currentChannel=channel
                if(interactive) {
                    try { confirm.launch(Intent(TvContractCompat.ACTION_REQUEST_CHANNEL_BROWSABLE)
                        .putExtra(TvContractCompat.EXTRA_CHANNEL_ID,channel)) }
                    catch(_:android.content.ActivityNotFoundException) { status("CONFIRMACION_SIN_HANDLER en este launcher") }
                } else {
                    TvContractCompat.requestChannelBrowsable(applicationContext,channel)
                    status("VISIBILIDAD_SOLICITADA id=$channel; la API no devuelve confirmación visual")
                }
            } catch(cancelled:CancellationException) { throw cancelled }
              catch(failure:Exception) { status("SOLICITUD_ERROR tipo=${failure.javaClass.simpleName}") }
            finally { controls.forEach { it.isEnabled=true } }
        }
    }
}
