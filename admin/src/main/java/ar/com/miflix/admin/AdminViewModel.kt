package ar.com.miflix.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import com.google.gson.*

class AdminViewModel(app: Application): AndroidViewModel(app) {
    private val secure=SecureConfig(app)
    var providerStatus by mutableStateOf("Conexión sin comprobar."); private set
    var testingProvider by mutableStateOf(false); private set
    private var providerTestJob: Job?=null
    private var providerTestGeneration=0
    private var providerTestCancel: (() -> Unit)?=null
    var credentialCleanupNotice by mutableStateOf("");private set
    fun testProvider(name: String) {
        if(busy || testingProvider) return
        if(name !in setOf("TMDB","TVmaze")) {providerStatus="Proveedor no disponible.";return}
        val epoch=++providerTestGeneration;testingProvider=true;providerStatus="$name: comprobando conexión…"
        val tmdb=if(name=="TMDB" && config!=null) HttpMetadata(config!!) else null
        val maze=if(name=="TVmaze") TvmazeRecoveryProvider() else null
        providerTestCancel=when(name) {"TMDB" -> ({tmdb?.cancelRecovery();Unit});"TVmaze" -> ({maze?.cancel();Unit});else -> ({Unit})}
        providerTestJob=viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {when(name) {
                    "TMDB" -> (tmdb ?: error("Configurá primero el servidor o TMDB.")).recoveryRequest("/movie/550","es-MX")
                    "TVmaze" -> maze!!.search(JsonObject().apply {addProperty("tipo","serie")},"Frontera Verde")
                    else -> error("Proveedor desconocido.")
                }}
                if(epoch==providerTestGeneration) providerStatus="$name: conexión verificada. El catálogo no cambió."
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {if(epoch==providerTestGeneration) providerStatus=e.message ?: "$name: no se pudo comprobar la conexión."}
            finally {if(epoch==providerTestGeneration) testingProvider=false}
        }
    }
    fun leaveProviders() {providerTestGeneration++;providerTestJob?.cancel();providerTestCancel?.invoke();providerTestCancel=null;testingProvider=false;screen="config"}
    val collections=CollectionController(app,viewModelScope,{config},{catalog})
    private val repo=AdminRepository(AndroidStateStore(app))
    var config by mutableStateOf(secure.load()); private set
    private val persistenceScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val persistence=DraftPersistence(persistenceScope,save={value,paths -> repo.persistDraft(value,paths)},onError={error ->
        persistenceScope.launch(Dispatchers.Main.immediate) {message="No se pudo conservar el borrador: ${error.message.orEmpty()}. La edición sigue en memoria; reintentá antes de cerrar."}
    })
    internal val metadataSearch=MetadataSearchController(viewModelScope,{metadata()})
    private var screenState by mutableStateOf("startup")
    var screen: String
        get()=screenState
        set(value) {if(value!=screenState) {if(screenState=="search") metadataSearch.pause();persistence.flushSoon()};screenState=value}
    private var categoryState by mutableStateOf("pelicula")
    var category:String
        get()=categoryState
        set(value) {if(value!=categoryState) {metadataSearch.invalidate();categoryState=value}}
    var busy by mutableStateOf(false);private set
    var message by mutableStateOf("");private set
    var revision by mutableStateOf(0);private set
    var draft by mutableStateOf(repo.draft(),referentialEqualityPolicy());private set
    var selected by mutableStateOf<JsonObject?>(null)
    var duplicate by mutableStateOf<JsonObject?>(null);private set
    var deletion by mutableStateOf<DeletionRequest?>(null);private set
    var enrichment by mutableStateOf<JsonObject?>(null);private set
    val results get()=metadataSearch.results
    val searching get()=metadataSearch.loading
    var query:String
        get()=metadataSearch.query
        set(value) {metadataSearch.query=value}
    var source:String
        get()=metadataSearch.source
        set(value) {metadataSearch.source=value}
    var searchYear:String
        get()=metadataSearch.year
        set(value) {metadataSearch.year=value}
    var original by mutableStateOf("");private set
    var sharedText by mutableStateOf("");private set
    var isNew by mutableStateOf(true);private set
    var updateMultimedia by mutableStateOf(false);private set
    var seasonNumber by mutableStateOf(1)
    var recoverySource by mutableStateOf("TMDB"); private set
    var recoveryLanguage by mutableStateOf("en-US")
    var recoveryQuery by mutableStateOf("")
    var recoverySeasons by mutableStateOf<Set<Long>>(emptySet())
    var recoveryCandidates by mutableStateOf<List<RecoveryCandidate>>(emptyList()); private set
    var recoveryPlan by mutableStateOf<RecoveryPlan?>(null); private set
    var recoverySelection by mutableStateOf<Set<String>>(emptySet())
    var recoveryMappings by mutableStateOf<Set<String>>(emptySet())
    var recoveryLicense by mutableStateOf(false)
    internal var recoveryProviderFactory: ((String)->RecoveryProvider)?=null
    private var recoveryProvider: RecoveryProvider?=null
    private var recoveryJob: Job?=null
    private var recoveryGeneration=0
    val canUndoRecovery get()=draft?.let {repo.canUndoRecovery(it)}==true
    val catalog get()=repo.catalog
    fun checkpoint(type: String)=repo.checkpoint(type)
    fun checkpointLink(type: String)=repo.checkpointLink(type)
    val unclassifiedCheckpoint get()=repo.unclassifiedCheckpoint
    val pending get()=repo.pending
    val writable get()=repo.writable && !busy
    val lastSync get()=repo.lastSync
    private fun remote(): AdminRemote=HttpAdminRemote(config ?: error("Configurá el servidor."))
    private fun metadata(): Metadata=HttpMetadata(config ?: error("Configurá el servidor."))
    val cleanupIssues get()=repo.cleanupIssues
    val cleanupBackups get()=repo.cleanupBackups
    fun privateCleanupBackup(id:String)=repo.privateCleanupBackup(id)
    fun editCleanupItem(id:String) {
        val item=catalog?.let {CatalogRules.items(it).find {row->row.text("id")==id}} ?: return
        edit(item)
    }
    init {
        try {RetiredCredentials.remove(app)} catch(_:Exception) {credentialCleanupNotice="No se pudo terminar de retirar la credencial del proveedor eliminado. La configuración Cloudflare/TMDB permanece intacta; reintentá al abrir Admin."}
        if(config==null) screen="config" else refresh()
    }
    private fun work(task: () -> Unit,success: () -> Unit = {}) {
        if(busy) return
        busy=true;message=""
        viewModelScope.launch {
            try { persistence.command {task()};revision++;success() }
            catch(e:Exception) {message=e.message ?: "No se pudo completar la operación.";revision++}
            finally {busy=false;if(screen=="startup") screen="home"}
        }
    }
    fun configure(base: String,token: String,tmdb: String) {
        if(busy) return
        try {
            val next=AdminConfig(base,token,tmdb).normalized()
            require(!pending || config?.baseUrl==next.baseUrl) {"Sincronizá o descartá explícitamente los pendientes antes de cambiar de servidor."}
            secure.save(next);metadataSearch.invalidate();config=next;refresh()
        } catch(e:Exception) {message=e.message.orEmpty()}
    }
    fun refresh(discard: Boolean=false) {
        if(config==null) {screen="config";return}
        screen="startup"
        var notice=""
        work({notice=repo.refresh(remote(),discard)},{
            screen="home";message="${repo.recoveryNotice} Catálogo actualizado · ${CatalogRules.items(repo.catalog!!).size} títulos. $notice".trim()
        })
    }
    fun sync() {
        work({repo.publish(remote())},{message="Sincronización del catálogo confirmada."})
    }
    fun openCategory(type: String) {category=type;screen="list"}
    fun select(item: JsonObject) {selected=item;screen="detail"}
    fun newItem() {
        if(!writable) return
        if(draft!=null) {message="Hay una revisión guardada. Continuá o descartá ese borrador antes de crear otro.";screen="home";return}
        draft=JsonObject().apply {addProperty("tipo",category);add("generos",JsonArray());add("temporadas",JsonArray())}
        isNew=true;updateMultimedia=false;original="";query="";metadataSearch.invalidate();searchYear="";source=if(category=="anime") "AniList" else "TMDB"
        persist();screen="search"
    }
    fun edit(item: JsonObject) {
        if(!writable) return
        if(draft!=null) {message="Hay una revisión guardada. Continuá o descartá ese borrador antes de editar otra ficha.";screen="home";return}
        metadataSearch.invalidate();searchYear="";category=item.text("tipo");draft=item.deepCopy();isNew=false;updateMultimedia=false;original="";query=item.text("titulo");source=if(category=="anime" && item.number("anilist_id")!=null) "AniList" else "TMDB";persist();screen="review"
    }
    fun resumeDraft() {
        draft=draft ?: repo.draft() ?: return
        original=draft?.text("_admin_original").orEmpty();isNew=draft?.text("_admin_is_new")!="false";updateMultimedia=draft?.text("_admin_update_version")=="true"
        category=draft!!.text("tipo");query=draft!!.text("titulo");source=if(category=="anime") "AniList" else "TMDB";screen="review"
    }
    private fun persist(verifiedPaths:Set<String> = emptySet()) {
        val copy=draft?.let(::draftEnvelope)
        draft=copy;persistence.offer(copy,verifiedPaths)
    }
    private fun draftEnvelope(item: JsonObject)=EditorDraft.root(item).apply {addProperty("_admin_original",original);addProperty("_admin_is_new",isNew);addProperty("_admin_update_version",updateMultimedia)}
    fun startRecovery(source: String,season: Long?=null,language: String="en-US") {
        if(!writable || draft==null) return
        if(source !in setOf("TMDB","TVmaze")) {message="Proveedor de recuperación no disponible.";return}
        recoverySource=source;recoveryLanguage=language;recoveryQuery=draft!!.text("titulo")
        recoverySeasons=draft!!.array("temporadas").mapNotNull {it.asJsonObject.number("numero")}.filter {it>=0 && (season==null || it==season)}.take(20).toSet()
        recoveryCandidates=emptyList();recoveryPlan=null;recoverySelection=emptySet();recoveryMappings=emptySet();recoveryLicense=false
        recoveryProvider=null;message="";screen="recoverySearch"
    }
    private fun recoveryWork(task: ()->Unit, success: ()->Unit) {
        if(busy) return
        val generation=++recoveryGeneration;busy=true;message=""
        recoveryJob=viewModelScope.launch {
            try {persistence.command {task()};if(generation==recoveryGeneration) {success();revision++}}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {if(generation==recoveryGeneration) message=e.message ?: "No se pudo recuperar información."}
            finally {if(generation==recoveryGeneration) busy=false}
        }
    }
    fun searchRecovery() {
        val item=draft?.deepCopy() ?: return;val query=recoveryQuery
        val provider=recoveryProviderFactory?.invoke(recoverySource) ?: when(recoverySource) {
            "TVmaze" -> TvmazeRecoveryProvider()
            else -> TmdbRecoveryProvider(HttpMetadata(config ?: return))
        }
        if(busy) return
        recoveryCandidates=emptyList();recoveryPlan=null
        recoveryProvider=provider;var found:List<RecoveryCandidate> = emptyList()
        recoveryWork({found=provider.search(item,query)},{recoveryCandidates=found;message=if(found.isEmpty()) "La fuente no encontró resultados. El catálogo no cambió." else "Elegí la ficha correcta por título, año y fuente."})
    }
    fun retrieveRecovery(candidate: RecoveryCandidate) {
        val provider=recoveryProvider ?: return;val before=draft?.deepCopy() ?: return
        val seasons=recoverySeasons.toSet();val language=recoveryLanguage;var plan:RecoveryPlan?=null
        recoveryWork({plan=MetadataRecovery.compare(before,provider.retrieve(candidate,before,seasons,language))},{
            recoveryPlan=plan;recoverySelection=plan!!.changes.filter {it.initiallySelected}.map {it.path.key}.toSet()
            recoveryMappings=emptySet();recoveryLicense=false;screen="recoveryPreview"
        })
    }
    fun cancelRecovery() {
        recoveryGeneration++;recoveryProvider?.cancel();recoveryJob?.cancel();recoveryProvider=null;busy=false
        recoveryPlan=null;recoveryCandidates=emptyList();screen="review";message="Consulta cancelada. La revisión y el catálogo no cambiaron."
    }
    fun confirmRecoveryMapping(key: String, checked: Boolean) {
        recoveryMappings=if(checked) recoveryMappings+key else recoveryMappings-key
        recoveryPlan?.changes?.filter {"${it.path.season}/${it.path.episode}"==key && !it.safe}?.forEach {c ->
            recoverySelection=if(checked && !c.manualSelection && MetadataRecovery.missing(c.path.field,c.before)) recoverySelection+c.path.key else recoverySelection-c.path.key
        }
    }
    fun applyRecovery() {
        if(!writable) return
        try {
            val plan=recoveryPlan ?: return
            require(draft==plan.before) {"La revisión cambió durante la consulta. Volvé a comparar."}
            val result=MetadataRecovery.apply(plan,recoverySelection,recoveryMappings,recoveryLicense)
            val backup=MetadataRecovery.backup(plan,recoverySelection,result)
            if(result.text("id").isBlank()) {
                val token=java.util.UUID.randomUUID().toString()
                result.addProperty("_admin_metadata_recovery",token)
                backup.getAsJsonObject("before").addProperty("_admin_metadata_recovery",token)
                backup.add("after",result.deepCopy())
            }
            val envelope=draftEnvelope(result)
            val paths=plan.selected(recoverySelection).map {it.path.key}.toSet()
            work({repo.stageRecovery(envelope,backup,paths)},{
                draft=repo.draft();recoveryPlan=null;recoveryProvider=null;screen="review"
                message="Datos seleccionados aplicados a la revisión. Falta confirmar y guardar; no se sincronizó. Respaldo local disponible."
            })
        } catch(e:Exception) {message=e.message ?: "No se pudo aplicar. Conservamos la revisión anterior."}
    }
    fun undoRecovery() {
        if(!writable) return
        val envelope=draftEnvelope(draft ?: return)
        var restored:JsonObject?=null
        work({restored=repo.undoRecovery(envelope)},{draft=restored;message="Recuperación revertida en la revisión. Confirmá y guardá para actualizar el catálogo; no se sincronizó."})
    }

    fun changeOriginal(value: String) {original=value;persist()}
    fun changeTelegramLink(value: String) {
        if(!writable) return
        draft=draft?.let {EditorDraft.field(it,"telegram_url",value).apply {addProperty("_admin_original",value)}}
        original=value;persist()
    }
    fun requestItemDeletion() {
        if(!writable) return
        val item=selected ?: return
        deletion=DeletionRequest(item.text("id"),item.text("titulo"),clearsDraft=draft?.text("id")==item.text("id"))
    }
    fun requestPartDeletion(season: Long,episode: Long?=null) {
        if(!writable) return
        val item=draft ?: return
        if(item.text("tipo") !in setOf("serie","anime") || !AdminEdits.hasPart(item,season,episode)) return
        val count=item.array("temporadas").first {it.asJsonObject.number("numero")==season}.asJsonObject.array("episodios").size()
        deletion=DeletionRequest(item.text("id"),item.text("titulo"),season,episode,count)
    }
    fun cancelDeletion() {deletion=null}
    fun confirmDeletion() {
        if(!writable) return
        val request=deletion ?: return
        var changedCatalog=false
        work({changedCatalog=request.confirm(repo)},{
            draft=repo.draft()
            if(request.season==null) {selected=null;screen="list"}
            else selected=catalog?.let {CatalogRules.items(it).firstOrNull {item ->item.text("id")==request.itemId}}
            message=if(changedCatalog) "Eliminado del catálogo local. Cambios pendientes: tocá Sincronizar." else "Eliminado del borrador. Guardá la revisión para actualizar el catálogo."
            deletion=null
        })
    }

    fun field(key: String,value: String) {
        if(!writable) return
        draft=draft?.let {EditorDraft.field(it,key,value)};persist()
    }
    fun episodeField(season: Long,episode: Long,key: String,value: String) {
        if(!writable || draft?.let {AdminEdits.hasPart(it,season,episode)}!=true) return
        draft=draft?.let {EditorDraft.episode(it,season,episode,key,value)}
        if(key=="telegram_url" && draft?.text("tipo") in setOf("serie","anime")) original=value
        persist()
    }
    fun seasonField(number: Long,key: String,value: String) {
        if(!writable) return
        draft=draft?.let {EditorDraft.season(it,number,key,value)};persist()
    }

    fun addSeason(number: Int) {
        if(!writable) return
        try {
            require(number>=0) {"La temporada debe ser 0 o mayor."}
            require(draft!!.array("temporadas").none {it.asJsonObject.number("numero")==number.toLong()}) {"Esa temporada ya existe."}
            draft=CatalogRules.mergeSeason(draft!!,JsonObject().apply {addProperty("numero",number);addProperty("nombre","Temporada $number");add("episodios",JsonArray())});persist()
        } catch(e:Exception) {message=e.message.orEmpty()}
    }
    fun addEpisode(season: Long,number: Int) {
        if(!writable) return
        try {
            require(number>=1) {"Indicá un número de episodio mayor que cero."}
            val item=draft!!.deepCopy();val s=item.array("temporadas").first {it.asJsonObject.number("numero")==season}.asJsonObject
            require(s.array("episodios").none {it.asJsonObject.number("numero")==number.toLong()}) {"Ese episodio ya existe."}
            val eps=s.array("episodios");eps.add(JsonObject().apply {addProperty("numero",number);addProperty("temporada",season);addProperty("titulo","Episodio $number")});s.add("episodios",eps)
            draft=item;persist()
        } catch(e:Exception) {message=e.message.orEmpty()}
    }
    fun fetchSeason() {
        val id=draft?.number("tmdb_id") ?: run {message="Vinculá un resultado TMDB para consultar episodios; también podés agregarlos manualmente.";return}
        val number=seasonNumber;var result:JsonObject?=null
        if(draft!!.array("temporadas").any {it.asJsonObject.number("numero")==number.toLong() && it.asJsonObject.array("episodios").size()>0}) {
            startRecovery("TMDB",number.toLong(),"es-MX");searchRecovery();return
        }
        work({result=metadata().season(id,number)},{draft=CatalogRules.mergeSeason(draft!!,result!!);draft!!.addProperty("fuente_episodios","TMDB");val provided=JsonObject().apply {add("temporadas",JsonArray().apply {add(result!!)})};persist(RetiredMetadata.freshPaths(provided,draft!!));message="Temporada $number obtenida; enlaces anteriores conservados."})
    }
    fun search() {if(writable) metadataSearch.search(category)}
    fun loadMoreSearch() {if(writable) metadataSearch.loadMore()}
    fun use(result: SearchResult) {
        if(!writable || results.none {it.source==result.source && it.id==result.id}) return
        metadataSearch.pause()
        val base=draft?.deepCopy() ?: return;var detail:JsonObject?=null
        work({detail=metadata().detail(result,category)},{
            val candidate=detail!!
            if(category=="anime" && result.source=="TMDB" && (base.number("anilist_id")!=null || base.text("fuente_principal")=="AniList")) {
                enrichment=candidate;screen="enrichment";return@work
            }
            val existing=if(isNew && category!="pelicula") CatalogRules.items(catalog!!).firstOrNull {it.text("id")==candidate.text("id")} else null
            val merged=(existing ?: base).deepCopy()
            if(existing!=null) {
                isNew=false
                base.text("telegram_url").takeIf {it.isNotBlank()}?.let {merged.addProperty("telegram_url",it)}
            }
            candidate.entrySet().forEach {(key,value) ->
                if(key=="id" && base.text("id").isNotBlank()) return@forEach
                if(key=="temporadas") return@forEach
                if(!value.isJsonNull && value.toString() !in setOf("\"\"","[]")) merged.add(key,value.deepCopy())
            }
            candidate.array("temporadas").forEach {s -> merged.add("temporadas",CatalogRules.mergeSeason(merged,s.asJsonObject).array("temporadas"))}
            val freshPaths=RetiredMetadata.freshPaths(candidate,merged)
            // A general lookup must not erase field-level credits for untouched episodes or missing fields.
            val previous=(existing ?: base)
            val previousRecords=previous.get("fuentes_metadata")?.takeIf {it.isJsonObject}?.asJsonObject?.get("recuperacion_selectiva")?.takeIf {it.isJsonObject}?.asJsonObject?.deepCopy()
            if(previousRecords!=null) {
                freshPaths.forEach {path->
                    val parts=path.split('/')
                    val oldValue=runCatching {MetadataRecovery.target(previous,RecoveryPath(parts[0].toLongOrNull(),parts[1].toLongOrNull(),parts[2])).get(parts[2])}.getOrNull()
                    val newValue=runCatching {MetadataRecovery.target(merged,RecoveryPath(parts[0].toLongOrNull(),parts[1].toLongOrNull(),parts[2])).get(parts[2])}.getOrNull()
                    if(oldValue!=newValue) previousRecords.remove(path)
                }
                if(previousRecords.size()>0) {
                    val sources=merged.get("fuentes_metadata")?.takeIf {it.isJsonObject}?.asJsonObject ?: JsonObject().also {merged.add("fuentes_metadata",it)}
                    sources.add("recuperacion_selectiva",previousRecords)
                }
            }
            draft=merged;persist(freshPaths);screen="review"
            duplicate=if(category=="pelicula") CatalogRules.duplicate(catalog!!,merged,if(isNew) null else base.text("id")) else null
        })
    }
    fun applyEnrichment(groups: Set<String>) {
        if(!writable) return
        val base=draft!!.deepCopy();val candidate=enrichment!!.deepCopy();var result:JsonObject?=null
        work({
            val full=if("episodios" in groups) Enrichment.withEpisodes(metadata(),candidate) else candidate
            result=Enrichment.apply(base,full,groups)
        },{
            draft=result;enrichment=null
            val paths=Enrichment.groups.filterKeys {it in groups}.values.flatten().filter {base.get(it)!=result!!.get(it)}.map {"title/season/$it"}.toMutableSet()
            if("episodios" in groups) result!!.array("temporadas").forEach {season->season.asJsonObject.array("episodios").forEach {ep->MetadataRecovery.episodeFields.forEach {f->if(ep.asJsonObject.has(f)) paths.add("${season.asJsonObject.text("numero")}/${ep.asJsonObject.text("numero")}/$f")}}}
            persist(paths);screen="review";message="Enriquecimiento aplicado a la revisión; todavía no está guardado."
        })
    }
    fun cancelEnrichment() {enrichment=null;screen="review"}
    fun cancelDuplicate() {duplicate=null;message="Operación cancelada; catálogo y checkpoint conservados.";screen="search"}
    fun keepDuplicate() {
        if(!writable) return
        val checkpoint=TelegramLinks.checkpoint(duplicate ?: return,original.ifBlank {draft?.text("telegram_url").orEmpty()})
        work({repo.setCheckpoint(checkpoint);repo.persistDraft(null)},{duplicate=null;draft=null;screen="home";message="Versión actual conservada. Avance de Películas guardado en este teléfono."})
    }

    fun updateDuplicate() {
        val old=duplicate ?: return
        draft=old.deepCopy().apply {
            addProperty("telegram_url",draft?.text("telegram_url").orEmpty())
            listOf("audio","calidad").forEach {key ->draft?.text(key)?.takeIf {it.isNotBlank()}?.let {addProperty(key,it)}}
        }
        isNew=false;updateMultimedia=true;duplicate=null;persist();screen="review"
    }
    fun save() {
        if(!writable) return
        try {
            var item=draft!!.deepCopy()
            item.entrySet().map {it.key}.filter {it.startsWith("_admin_")}.forEach {item.remove(it)}
            CatalogRules.duplicate(catalog!!,item,if(isNew) null else item.text("id"))?.let {duplicate=it;return}
            item.text("telegram_url").takeIf {it.isNotBlank()}?.let {require(TelegramLinks.valid(it)!=null) {"El enlace Telegram no es una publicación válida."};TelegramLinks.messageId(it)?.let {id ->item.addProperty("telegram_message_id",id)}}
            item.array("temporadas").forEach {s ->s.asJsonObject.array("episodios").forEach {e ->
                val link=e.asJsonObject.text("telegram_url")
                require(link.isBlank() || TelegramLinks.valid(link)!=null) {"Hay un enlace de episodio inválido."}
                if(link.isNotBlank()) TelegramLinks.messageId(link)?.let {e.asJsonObject.addProperty("telegram_message_id",it)}
            }}
            if(updateMultimedia) item=CatalogRules.updateVersion(item,item.text("telegram_url"),item.text("audio"),item.text("calidad"))
            val cp=AdminCheckpoints.forSave(item,original,isNew,updateMultimedia)
            work({repo.saveItem(item,cp);repo.persistDraft(null)},{draft=null;screen="home";message="Guardado en el teléfono. Cambios pendientes: tocá Sincronizar."})
        } catch(e:Exception) {message=e.message.orEmpty()}
    }
    fun discardDraft() {
        if(busy) return
        work({repo.persistDraft(null)},{draft=null;screen="home"})
    }
    fun background() {persistence.flushSoon()}
    internal suspend fun awaitLocalWrites() {persistence.command {}}
    fun receive(text: String) {sharedText=text.take(20000)}
    fun consumeShared(link: String) {
        if(!writable) return
        if(draft==null) newItem()
        category=draft!!.text("tipo")
        changeTelegramLink(link);sharedText="";screen="search"
    }
    fun dismissShare() {sharedText=""}
    override fun onCleared() {metadataSearch.pause();persistence.finish();collections.close();recoveryProvider?.cancel();providerTestCancel?.invoke();super.onCleared()}
}
