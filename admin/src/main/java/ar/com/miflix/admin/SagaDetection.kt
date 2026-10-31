package ar.com.miflix.admin

import com.google.gson.*
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Bulk queries are deliberately serial (one in flight). A cache stores only bounded public TMDB data. */
interface SagaProvider {
    fun membership(movieId:Long):Long?
    fun detail(collectionId:Long):JsonObject
    fun cancel()
}
class SagaRequestFailure(val status:Int=0,val retryAfterMs:Long?=null):IllegalStateException(
    when(status) {401,403 -> "TMDB rechazó la credencial.";429 -> "TMDB pidió esperar; se puede reanudar más tarde.";else -> "No se pudo consultar TMDB${if(status>0) " (HTTP $status)" else ""}."})
class TmdbSagaProvider(private val metadata:HttpMetadata):SagaProvider {
    override fun membership(movieId:Long):Long? {
        val result=metadata.sagaRequest("/movie/$movieId")
        check(SagaDetection.positiveId(result,"id")==movieId && result.has("belongs_to_collection")) {"TMDB devolvió una película incompleta o distinta."}
        val belongs=result.get("belongs_to_collection")
        if(belongs.isJsonNull) return null
        check(belongs.isJsonObject) {"Pertenencia TMDB inválida."}
        return SagaDetection.positiveId(belongs.asJsonObject,"id") ?: error("Colección TMDB sin ID válido.")
    }
    override fun detail(collectionId:Long)=metadata.sagaRequest("/collection/$collectionId").also {SagaDetection.validateDetail(it,collectionId)}
    override fun cancel()=metadata.cancelRecovery()
}
data class SagaProgress(val total:Int=0,val analyzed:Int=0,val pending:Int=0,val missing:Int=0,val failed:Int=0,
    val detected:Int=0,val reused:Int=0,val phase:String="",val complete:Boolean=false)
data class SagaProposal(val detail:JsonObject,val matches:List<CollectionMatch>,val existing:List<JsonObject>) {
    val id get()=detail.number("id")!!
    val exact get()=matches.filter {it.exact.size==1}.map {it.exact.single()}.distinct()
    val ambiguous get()=matches.filter {it.exact.size>1}
    val missing get()=matches.filter {it.exact.isEmpty()}
    val current get()=existing.singleOrNull()
    val additions get()=exact.filter {it !in current?.let(CollectionRules::ids).orEmpty()}
    val actionable get()=existing.size<=1 && (if(current==null) exact.isNotEmpty() else additions.isNotEmpty())
    val status get()=when {existing.size>1 -> "Conflicto: varias colecciones locales con este ID TMDB";current==null -> "Nueva";additions.isNotEmpty() -> "Existente · parcialmente completada";else -> "Existente · sin integrantes nuevos seguros"}
}
class SagaCache(private val store:StateStore,private val now:()->Long={System.currentTimeMillis()}) {
    private var root=try {store.read()?.takeIf {it.text("version")=="1" && it.toString().toByteArray().size<=6*1024*1024} ?: JsonObject()} catch(_:Exception) {JsonObject()}
    private fun get(key:String):JsonElement? {
        val row=root.get(key)?.takeIf {it.isJsonObject}?.asJsonObject ?: return null
        val time=row.number("at") ?: return null
        if(time>now() || now()-time>7L*24*60*60*1000) return null
        return row.get("value")?.deepCopy()
    }
    @Synchronized fun remainingCooldown()=(root.number("not_before") ?: 0L).let {(it-now()).coerceAtLeast(0)}
    @Synchronized fun cooldown(ms:Long) {
        val next=root.deepCopy().apply {addProperty("version",1);addProperty("not_before",now()+ms.coerceIn(0,Long.MAX_VALUE-now()))};store.write(next);root=next
    }
    @Synchronized fun clear() {val next=JsonObject().apply {addProperty("version",1);root.get("not_before")?.let {add("not_before",it.deepCopy())}};store.write(next);root=next}
    @Synchronized fun movie(id:Long):Pair<Boolean,Long?> {
        val v=get("movie-$id") ?: return false to null
        if(v.isJsonNull) return true to null
        val n=runCatching {v.asString.toLong()}.getOrNull()?.takeIf {it>0} ?: return false to null
        return true to n
    }
    @Synchronized fun detail(id:Long):JsonObject? = get("collection-$id")?.takeIf {it.isJsonObject}?.asJsonObject?.let {value ->runCatching {SagaDetection.validateDetail(value,id);value}.getOrNull()}
    @Synchronized private fun put(key:String,value:JsonElement) {
        val next=root.deepCopy().apply {addProperty("version",1);add(key,JsonObject().apply {addProperty("at",now());add("value",value.deepCopy())})}
        // Cache eviction never affects catalog/collections or their backup. Keep a bounded recent working set.
        while(next.size()>10001 || next.toString().toByteArray().size>6*1024*1024) {
            val oldest=next.entrySet().filter {it.key!="version" && it.key!="not_before"}.minByOrNull {it.value.asJsonObject.number("at") ?: 0} ?: break
            next.remove(oldest.key)
        }
        store.write(next);root=next
    }
    fun movie(id:Long,collection:Long?)=put("movie-$id",collection?.let {JsonPrimitive(it)} ?: JsonNull.INSTANCE)
    fun detail(value:JsonObject)=put("collection-${value.number("id")}",value)
}
class SagaDetection(private val provider:SagaProvider,private val cache:SagaCache,
    private val pause:suspend(Long)->Unit={delay(it)}) {
    fun cancel()=provider.cancel()
    private suspend fun <T> request(block:()->T):T {
        repeat(3) {attempt ->
            currentCoroutineContext().ensureActive()
            try {pause(300);return block()}
            catch(e:CancellationException) {throw e}
            catch(e:SagaRequestFailure) {
                if(e.status in setOf(401,403)) throw e
                if(e.status!=0 && e.status!=429 && e.status !in 500..599) throw e
                val wait=maxOf(1000L*(1L shl attempt),e.retryAfterMs ?: 0L)
                if(e.status==429) cache.cooldown(wait)
                // A long Retry-After is NOT shortened: mark the query failed and let the user resume later.
                if(attempt==2 || wait>60000) throw e
                pause(wait)
            }
        };error("Consulta agotada.")
    }
    suspend fun scan(catalog:JsonObject,collections:JsonObject,onProgress:suspend(SagaProgress,List<SagaProposal>)->Unit):List<SagaProposal> {
        val cooldown=cache.remainingCooldown()
        if(cooldown>60000) throw SagaRequestFailure(429,cooldown)
        if(cooldown>0) pause(cooldown)
        val snapshot=catalog.deepCopy();val movies=CatalogRules.items(snapshot).filter {it.text("tipo")=="pelicula"}
        val valid=movies.mapNotNull {positiveId(it,"tmdb_id")}.distinct()
        val noId=movies.count {positiveId(it,"tmdb_id")==null}
        val groups=linkedSetOf<Long>();val found=mutableListOf<SagaProposal>()
        var analyzed=0;var failed=0;var reused=0
        suspend fun report(phase:String,complete:Boolean=false) {onProgress(SagaProgress(movies.size,analyzed,(movies.size-noId-analyzed).coerceAtLeast(0),noId,failed,groups.size,reused,phase,complete),found.toList())}
        report("Consultando pertenencia de películas")
        for(id in valid) {
            currentCoroutineContext().ensureActive()
            val count=movies.count {positiveId(it,"tmdb_id")==id}
            try {
                val cached=cache.movie(id)
                val member=if(cached.first) {reused++;cached.second} else request {provider.membership(id)}.also {cache.movie(id,it)}
                if(member!=null) groups.add(member)
            } catch(e:CancellationException) {throw e} catch(e:SagaRequestFailure) {
                if(e.status in setOf(401,403,429)) throw e
                failed+=count
            } catch(_:Exception) {failed+=count}
            analyzed+=count;report("Consultando pertenencia de películas")
        }
        for(id in groups) {
            currentCoroutineContext().ensureActive()
            try {
                val old=cache.detail(id)
                val detail=old?.also {reused++} ?: request {provider.detail(id)}.also {validateDetail(it,id);cache.detail(it)}
                found.add(proposal(detail,snapshot,collections))
            } catch(e:CancellationException) {throw e} catch(e:SagaRequestFailure) {if(e.status in setOf(401,403,429)) throw e;failed++} catch(_:Exception) {failed++}
            report("Recuperando detalles de sagas")
        }
        report(if(failed==0) "Análisis terminado" else "Análisis parcial: hay consultas fallidas",failed==0)
        return found
    }
    companion object {
        fun positiveId(obj:JsonObject,key:String):Long?=obj.get(key)?.takeIf {it.isJsonPrimitive && it.asString.matches(Regex("[1-9][0-9]*"))}?.asString?.toLongOrNull()?.takeIf {it<=Int.MAX_VALUE}
        fun retryAfter(value:String?,now:Instant=Instant.now()):Long? {
            if(value==null) return null
            value.toLongOrNull()?.takeIf {it>=0}?.let {return if(it>Long.MAX_VALUE/1000) Long.MAX_VALUE else it*1000}
            return runCatching {(ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-now.toEpochMilli()).coerceAtLeast(0)}.getOrNull()
        }
        fun validateDetail(d:JsonObject,id:Long) {
            require(positiveId(d,"id")==id && d.text("name").isNotBlank() && d.get("parts")?.isJsonArray==true) {"Detalle TMDB incompleto o identidad distinta."}
            val parts=d.array("parts")
            require(parts.all {it.isJsonObject && positiveId(it.asJsonObject,"id")!=null}) {"Integrantes TMDB incompletos."}
            require(parts.map {it.asJsonObject.number("id")}.distinct().size==parts.size()) {"TMDB devolvió integrantes duplicados."}
        }
        fun proposal(detail:JsonObject,catalog:JsonObject,collections:JsonObject):SagaProposal {
            validateDetail(detail,detail.number("id") ?: 0)
            // Title similarity is intentionally NOT used by bulk detection.
            val matches=CollectionImport.match(detail,catalog).map {it.copy(possible=emptyList())}
            return SagaProposal(detail.deepCopy(),matches,CollectionRules.list(collections).filter {it.number("tmdb_collection_id")==detail.number("id")}.map {it.deepCopy()})
        }
        /** Revalidate against current catalog and collections at confirmation; append only, never remove/reorder/overwrite. */
        fun approve(root:JsonObject,catalog:JsonObject,proposals:List<SagaProposal>,selected:Set<Long>):JsonObject {
            require(selected.isNotEmpty());require(selected.all {id->proposals.count {it.id==id}==1}) {"Propuesta desconocida o duplicada."}
            var next=root.deepCopy()
            for(p in proposals.filter {it.id in selected}) {
                val current=proposal(p.detail,catalog,next)
                require(current.actionable) {"La saga cambió, es ambigua o no tiene integrantes nuevos seguros. Revisá nuevamente."}
                val existing=current.current
                val draft=if(existing!=null) CollectionRules.add(existing,current.additions.toSet(),catalog) else {
                    CollectionImport.apply(CollectionRules.fresh().apply {addProperty("orden",CollectionRules.list(next).size)},current.detail,
                        setOf("nombre","descripcion","poster_url","backdrop_url").filter {field ->when(field) {"nombre" ->current.detail.text("name").isNotBlank();"descripcion" ->MetadataRecovery.clean(current.detail.text("overview")).isNotBlank();else ->TmdbCollections.url(current.detail.text(if(field=="poster_url") "poster_path" else "backdrop_path"),field).isNotBlank()}}.toSet(),current.exact.toSet(),catalog,"es-AR")
                }
                next=CollectionRules.save(next,draft,existing?.text("id"))
            }
            return next
        }
    }
}
