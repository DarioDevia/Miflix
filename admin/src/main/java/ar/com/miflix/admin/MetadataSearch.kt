package ar.com.miflix.admin

import androidx.compose.runtime.*
import kotlinx.coroutines.*

data class MetadataQuery(val title:String,val type:String,val source:String,val year:Int?=null) {
    companion object {
        fun create(title:String,type:String,source:String,year:String):MetadataQuery {
            require(title.trim().isNotEmpty()) {"Escribí un título."}
            require(title.length<=200) {"La búsqueda es demasiado larga."}
            require(type in setOf("pelicula","serie","anime") && source in setOf("TMDB","AniList") && (source!="AniList" || type=="anime")) {"Fuente no disponible para este contenido."}
            require(year.isEmpty() || year.matches(Regex("[1-9][0-9]{3}"))) {"El año debe tener cuatro dígitos, entre 1000 y 9999, o quedar vacío."}
            return MetadataQuery(title.trim(),type,source,year.toIntOrNull())
        }
    }
    fun identity(result:SearchResult)="$source/$type/${result.id}"
}
data class MetadataPage(val items:List<SearchResult>,val page:Int,val hasNextPage:Boolean)

/** One session, API pages buffered in full; cursor changes only after validation succeeds. */
internal class MetadataPager(val query:MetadataQuery,private val metadata:Metadata) {
    private var nextPage=1
    private var remoteMore=true
    private var buffer=emptyList<SearchResult>()
    private val seen=mutableSetOf<String>()
    val hasMore get()=buffer.isNotEmpty() || remoteMore
    fun load():List<SearchResult> {
        if(!hasMore) return emptyList()
        if(buffer.isEmpty()) {
            val page=metadata.searchPage(query,nextPage)
            check(page.page==nextPage) {"La fuente devolvió otra página. Reintentá; se conservaron los resultados."}
            check(page.items.all {it.source==query.source && it.id>0}) {"La página contiene identificadores incompatibles."}
            val fresh=page.items.distinctBy(query::identity).filter {query.identity(it) !in seen}
            check(fresh.isNotEmpty() || (!page.hasNextPage && page.items.isEmpty())) {"La fuente repitió una página o devolvió una página vacía sin terminar. Reintentá."}
            // No state is advanced before every check above succeeds.
            buffer=fresh;remoteMore=page.hasNextPage;nextPage++
            fresh.forEach {seen.add(query.identity(it))}
        }
        val batch=buffer.take(10);buffer=buffer.drop(batch.size)
        return batch
    }
    fun cancel()=metadata.cancelSearch()
}

/** Search-only state: never receives a catalog repository or a persistence callback. */
internal class MetadataSearchController(
    private val scope:CoroutineScope,private val factory:()->Metadata,
    private val dispatcher:CoroutineDispatcher=Dispatchers.IO
) {
    private var queryState by mutableStateOf("")
    var query:String
        get()=queryState
        set(value) {if(queryState!=value) {queryState=value;invalidate()}}
    private var yearState by mutableStateOf("")
    var year:String
        get()=yearState
        set(value) {if(yearState!=value) {yearState=value;invalidate()}}
    private var sourceState by mutableStateOf("TMDB")
    var source:String
        get()=sourceState
        set(value) {if(sourceState!=value) {sourceState=value;invalidate()}}
    var results by mutableStateOf<List<SearchResult>>(emptyList());private set
    var loading by mutableStateOf(false);private set
    var hasMore by mutableStateOf(false);private set
    var completed by mutableStateOf(false);private set
    var error by mutableStateOf("");private set
    private var session:MetadataPager?=null
    private var generation=0
    private var job:Job?=null
    fun pause() {
        generation++;job?.cancel();session?.cancel()
        if(loading) {session=null;hasMore=false;completed=false;error="Búsqueda interrumpida. Tocá Buscar para comenzar otra vez."}
        loading=false
    }
    fun invalidate() {pause();session=null;results=emptyList();hasMore=false;completed=false;error=""}
    fun search(type:String) {
        invalidate()
        try {session=MetadataPager(MetadataQuery.create(query,type,source,year),factory());loadMore()}
        catch(e:Exception) {error=e.message ?: "No se pudo iniciar la búsqueda."}
    }
    fun loadMore() {
        if(loading) return
        val pager=session ?: return
        if(completed && !hasMore) return
        val epoch=generation
        loading=true;error=""
        job=scope.launch {
            try {
                val batch=withContext(dispatcher) {pager.load()}
                if(epoch==generation) {results=results+batch;hasMore=pager.hasMore;completed=true}
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {if(epoch==generation) error=e.message ?: "No se pudieron cargar los resultados. Reintentá."}
            finally {if(epoch==generation) loading=false}
        }
    }
}
