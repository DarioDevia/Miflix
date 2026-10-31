package ar.com.miflix.admin

import android.app.Application
import androidx.compose.runtime.*
import com.google.gson.*
import kotlinx.coroutines.*

class CollectionController(app:Application,private val scope:CoroutineScope,
    private val config:()->AdminConfig?,private val catalog:()->JsonObject?) {
    private val sagaCache=SagaCache(CollectionFileStore(app,"collections-tmdb-cache.json"))
    internal var sagaProviderFactory:(()->SagaProvider)?=null
    private var sagaProvider:SagaProvider?=null
    var sagaProgress by mutableStateOf(SagaProgress());private set
    var sagaProposals by mutableStateOf<List<SagaProposal>>(emptyList());private set
    var selectedSagas by mutableStateOf<Set<Long>>(emptySet())
    var ignoredSagas by mutableStateOf<Set<Long>>(emptySet());private set
    private var sagaReviewedCatalog:JsonObject?=null
    private var sagaReviewedData:JsonObject?=null
    var sagaConfirmation by mutableStateOf<JsonObject?>(null);private set
    private val repo=CollectionRepository(CollectionFileStore(app))
    var data by mutableStateOf(repo.data());private set
    var draft by mutableStateOf(repo.draft());private set
    var editingId by mutableStateOf(repo.editingId);private set
    var page by mutableStateOf("list");private set
    var message by mutableStateOf(repo.error);private set
    var busy by mutableStateOf(false);private set
    var query by mutableStateOf("")
    var language by mutableStateOf("es-AR")
    var results by mutableStateOf<List<JsonObject>>(emptyList());private set
    var searchPage by mutableStateOf(1);private set
    var searchPages by mutableStateOf(1);private set
    var details by mutableStateOf<JsonObject?>(null);private set
    var detailsLanguage by mutableStateOf("es-AR");private set
    var matches by mutableStateOf<List<CollectionMatch>>(emptyList());private set
    var selectedMembers by mutableStateOf<Set<String>>(emptySet())
    var selectedFields by mutableStateOf<Set<String>>(emptySet())
    var images by mutableStateOf<List<CollectionImage>>(emptyList());private set
    var restorePreview by mutableStateOf<JsonObject?>(null);private set
    val writable get()=repo.writable && !busy
    val hasBackup get()=repo.hasBackup
    val catalogItems get()=catalog()?.let {CatalogRules.items(it)}.orEmpty()
    internal var providerFactory:(()->CollectionProvider)?=null
    private var provider:CollectionProvider?=null
    private var job:Job?=null
    private var generation=0
    private fun fail(e:Exception) {message=e.message ?: "No se pudo completar la operación de colecciones."}
    private fun mutate(block:()->Unit) {if(busy) return;try {block();message=""} catch(e:Exception) {fail(e)}}
    private fun stage(next:JsonObject) {repo.stage(next,editingId);draft=next}
    fun openList() {cancelRequest();page="list";data=repo.data();if(!repo.writable) message=repo.error}
    fun begin(mode:String="manual")=mutate {
        check(repo.writable) {repo.error};check(draft==null) {"Hay un borrador de colección. Continuá o descartá antes de crear otra."}
        val fresh=CollectionRules.fresh();repo.stage(fresh,null);editingId=null;draft=fresh
        page=if(mode=="manual") "edit" else "tmdb";details=null;results=emptyList();query="";language="es-AR"
    }
    fun edit(id:String)=mutate {
        check(repo.writable) {repo.error};check(draft==null) {"Continuá o descartá el borrador de colección anterior."}
        val row=CollectionRules.list(data).single {it.text("id")==id}.deepCopy()
        repo.stage(row,id);editingId=id;draft=row;page="edit"
    }
    fun resume() {if(draft!=null) page="edit"}
    fun discard()=mutate {repo.stage(null,null);draft=null;editingId=null;page="list"}
    fun field(key:String,value:String)=mutate {
        require(key in setOf("nombre","descripcion","poster_url","backdrop_url","orden"))
        val next=draft!!.deepCopy()
        if(key=="orden") {
            val n=value.toLongOrNull();require(n!=null && n in 0..1000000) {"Orden inválido (0 a 1000000)."};next.addProperty(key,n)
        } else next.addProperty(key,value.take(when(key) {"nombre" ->200;"descripcion" ->12000;else ->2000}))
        stage(next)
    }
    fun visible(value:Boolean)=mutate {stage(draft!!.deepCopy().apply {addProperty("visible",value)})}
    fun add(ids:Set<String>)=mutate {stage(CollectionRules.add(draft!!,ids,catalog() ?: error("Descargá el catálogo para elegir títulos.")))}
    fun remove(id:String)=mutate {stage(CollectionRules.members(draft!!,CollectionRules.ids(draft!!).filter {it!=id}))}
    fun move(index:Int,delta:Int)=mutate {stage(CollectionRules.move(draft!!,index,delta))}
    fun preview()=mutate {CollectionRules.validate(draft!!);page="preview"}
    fun backToEdit() {cancelRequest();page="edit"}
    fun save()=mutate {repo.save(draft!!,editingId);draft=null;editingId=null;data=repo.data();page="list"}
    fun delete(id:String)=mutate {repo.delete(id);data=repo.data()}
    fun undo()=mutate {check(draft==null) {"Descartá o terminá el borrador antes de restaurar el estado anterior."};repo.undo();data=repo.data()}
    fun export():String=repo.export()
    fun readRestore(raw:String)=mutate {restorePreview=CollectionRules.parse(raw)}
    fun cancelRestore() {restorePreview=null}
    fun confirmRestore()=mutate {
        val parsed=restorePreview ?: return@mutate
        repo.restore(parsed.toString());data=repo.data();draft=null;editingId=null;restorePreview=null;page="list"
    }
    fun notify(text:String) {message=text}
    private fun work(task:()->Unit,success:()->Unit) {
        if(busy) return
        val epoch=++generation;busy=true;message=""
        job=scope.launch {
            try {withContext(Dispatchers.IO) {task()};if(epoch==generation) success()}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {if(epoch==generation) fail(e)}
            finally {if(epoch==generation) busy=false}
        }
    }
    fun pageSyncInfo() {if(!busy) page="collectionSync"}
    fun showSagas() {if(!busy) page="sagas"}
    fun detectSagas(refresh:Boolean=false) {
        if(busy) return
        try {
            check(repo.writable) {repo.error}
            val snapshot=catalog()?.deepCopy() ?: error("Descargá primero el catálogo.")
            if(refresh) sagaCache.clear()
            val p=sagaProviderFactory?.invoke() ?: TmdbSagaProvider(HttpMetadata(config() ?: error("Configurá TMDB.")))
            sagaProvider=p;val epoch=++generation;busy=true;page="sagas";message="";selectedSagas=emptySet();ignoredSagas=emptySet();sagaProposals=emptyList();sagaProgress=SagaProgress()
            val collections=data.deepCopy()
            job=scope.launch {
                try {
                    withContext(Dispatchers.IO) {SagaDetection(p,sagaCache).scan(snapshot,collections) {progress,proposals ->
                        withContext(Dispatchers.Main) {if(epoch==generation) {sagaProgress=progress;sagaProposals=proposals}}
                    }}
                    if(epoch==generation) message="Revisá las propuestas. No se modificó ninguna película ni colección. Los campos TMDB están en el idioma solicitado es-AR; pueden contener texto original."
                } catch(e:CancellationException) {throw e}
                catch(e:Exception) {if(epoch==generation) {fail(e);sagaProgress=sagaProgress.copy(complete=false,phase="Análisis interrumpido; reutilizá la caché para reanudar")}}
                finally {if(epoch==generation) busy=false}
            }
        } catch(e:Exception) {fail(e)}
    }
    fun ignoreSaga(id:Long) {if(!busy) {ignoredSagas=ignoredSagas+id;selectedSagas=selectedSagas-id}}
    fun reviewSagas()=mutate {
        check(draft==null) {"Terminá o descartá el borrador antes de crear/actualizar propuestas."}
        val next=SagaDetection.approve(repo.data(),catalog() ?: error("Catálogo no disponible."),sagaProposals,selectedSagas-ignoredSagas)
        sagaReviewedCatalog=catalog()?.deepCopy();sagaReviewedData=repo.data();sagaConfirmation=next
    }
    fun cancelSagaConfirmation() {sagaConfirmation=null}
    fun confirmSagas()=mutate {
        val approved=sagaConfirmation ?: return@mutate
        check(repo.data()==sagaReviewedData && catalog()==sagaReviewedCatalog) {"Los datos cambiaron después del resumen; revisá las propuestas otra vez."}
        repo.replaceApproved(approved);data=repo.data();sagaConfirmation=null;selectedSagas=emptySet()
        sagaProposals=sagaProposals.map {SagaDetection.proposal(it.detail,catalog()!!,data)}
    }
    fun editSaga(id:Long)=mutate {
        check(draft==null) {"Terminá o descartá el borrador anterior."}
        val p=sagaProposals.single {it.id==id}
        val current=SagaDetection.proposal(p.detail,catalog() ?: error("Catálogo no disponible."),data)
        check(current.existing.size<=1) {"Hay varias colecciones con este ID; resolvé el conflicto manualmente."}
        val before=current.current ?: CollectionRules.fresh()
        repo.stage(before,current.current?.text("id"));editingId=current.current?.text("id");draft=before
        details=p.detail.deepCopy();detailsLanguage="es-AR";matches=CollectionImport.match(p.detail,catalog()!!)
        selectedMembers=emptySet();selectedFields=emptySet();page="importPreview"
    }
    fun startTmdb() {if(draft!=null && !busy) {page="tmdb";details=null;results=emptyList();query=draft!!.number("tmdb_collection_id")?.toString().orEmpty();language="es-AR"}}
    fun search(targetPage:Int=1) {
        if(busy) return
        try {
            val p=providerFactory?.invoke() ?: TmdbCollections(HttpMetadata(config() ?: error("Configurá TMDB en Configuración del servidor.")))
            provider=p;val text=query;val lang=language;var found:CollectionSearch?=null
            work({found=p.search(text,targetPage,lang)},{results=found!!.results;searchPage=found!!.page;searchPages=found!!.pages;message=if(results.isEmpty()) "TMDB no encontró colecciones." else "Elegí la saga correcta. Ningún título se agregó."})
        } catch(e:Exception) {fail(e)}
    }
    fun retrieve(id:Long) {
        val p=provider ?: return;val lang=language
        var detail:JsonObject?=null;var mapped:List<CollectionMatch> = emptyList()
        val snapshot=catalog()?.deepCopy() ?: run {message="Descargá el catálogo para cruzar las películas.";return}
        work({detail=p.detail(id,lang);mapped=CollectionImport.match(detail!!,snapshot)},{
            details=detail;detailsLanguage=lang;matches=mapped
            // Every title, including an exact ID match, needs an explicit selection.
            selectedMembers=emptySet()
            val before=draft!!
            selectedFields=setOf("nombre","descripcion","poster_url","backdrop_url").filter {field ->
                before.text(field).isBlank() && proposed(field).isNotBlank()
            }.toSet();page="importPreview"
        })
    }
    fun proposed(field:String):String = details?.let {d ->when(field) {
        "nombre" -> d.text("name")
        "descripcion" -> MetadataRecovery.clean(d.text("overview"))
        "poster_url" -> TmdbCollections.url(d.text("poster_path"),field)
        "backdrop_url" -> TmdbCollections.url(d.text("backdrop_path"),field)
        else -> ""
    }}.orEmpty()
    fun applyImport()=mutate {
        val fresh=CollectionImport.apply(draft!!,details ?: error("Consultá primero una colección."),selectedFields,selectedMembers,
            catalog() ?: error("Catálogo no disponible."),detailsLanguage)
        stage(fresh);page="edit";details=null
    }
    fun loadImages() {
        val id=draft?.number("tmdb_collection_id") ?: run {message="Importá una colección TMDB para consultar sus imágenes. También podés usar una URL personalizada.";return}
        try {
            val p=providerFactory?.invoke() ?: TmdbCollections(HttpMetadata(config() ?: error("Configurá TMDB.")))
            provider=p;var found:List<CollectionImage> = emptyList()
            work({found=p.images(id)},{images=found;page="images";message=if(found.isEmpty()) "La colección no tiene imágenes disponibles." else "Elegí una imagen. La selección solo cambia el borrador."})
        } catch(e:Exception) {fail(e)}
    }
    fun chooseImage(image:CollectionImage)=mutate {require(image in images);stage(draft!!.deepCopy().apply {addProperty(image.field,image.url)});page="edit"}
    fun cancelRequest() {if(busy && page=="sagas") {sagaProgress=sagaProgress.copy(complete=false,phase="Cancelado; los resultados consultados quedan en caché");message="Análisis cancelado sin guardar propuestas."};generation++;sagaProvider?.cancel();sagaProvider=null;provider?.cancel();job?.cancel();provider=null;busy=false}
    fun close() {cancelRequest()}
}
