package ar.com.miflix.admin

import com.google.gson.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

interface CollectionsRemote {
    fun download():JsonObject
    fun publish(document:JsonObject):String
    fun cancel() {}
}

/** Only the production GET/PUT /admin/colecciones contract; no invented revision protocol. */
class HttpCollectionsRemote(config:AdminConfig,private val client:OkHttpClient=OkHttpClient.Builder()
    .callTimeout(25,TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()):CollectionsRemote {
    private val url=config.baseUrl+"/admin/colecciones"
    private val token=config.token
    @Volatile private var call:Call?=null
    override fun cancel() {call?.cancel()}
    private fun request(document:JsonObject?=null):JsonObject {
        val builder=Request.Builder().url(url).header("Authorization","Bearer $token").header("Cache-Control","no-cache")
        if(document!=null) builder.put(document.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        try {
            val active=client.newCall(builder.build());call=active
            return active.execute().use {r ->
                check(r.isSuccessful) {when(r.code) {
                    401,403 -> "Cloudflare rechazó la credencial administrativa (HTTP ${r.code})."
                    400 -> "Cloudflare rechazó el esquema de colecciones (HTTP 400)."
                    404 -> "El Worker no tiene la ruta de colecciones (HTTP 404)."
                    409 -> "Primero debe existir un catálogo normal válido en Cloudflare (HTTP 409)."
                    413 -> "Las colecciones o el catálogo final superan el límite del Worker (HTTP 413)."
                    503 -> "Cloudflare no pudo guardar las colecciones (HTTP 503)."
                    else -> "Cloudflare respondió HTTP ${r.code}."
                }}
                val stream=r.body?.byteStream() ?: error("Respuesta vacía de Cloudflare.")
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(out.size()<=CollectionRules.MAX_BYTES) {
                    val n=stream.read(buffer,0,minOf(buffer.size,CollectionRules.MAX_BYTES+1-out.size()))
                    if(n<0) break
                    out.write(buffer,0,n)
                }
                check(out.size()<=CollectionRules.MAX_BYTES) {"Respuesta de colecciones demasiado grande; no se modificó la copia local."}
                try {JsonParser.parseString(String(out.toByteArray(),Charsets.UTF_8)).asJsonObject}
                catch(_:Exception) {error("Respuesta JSON de colecciones inválida.")}
            }
        } catch(e:IllegalStateException) {throw e}
        catch(_:Exception) {error("No se pudo confirmar la conexión con Cloudflare. Conservamos las colecciones locales. Si estabas publicando, consultá el remoto antes de reintentar: el servidor puede haber recibido la solicitud.")}
        finally {call=null}
    }
    override fun download()=CollectionSync.document(request())
    override fun publish(document:JsonObject):String {
        val payload=CollectionSync.document(document);val ack=request(payload)
        check(ack.get("ok")?.let {it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean}==true &&
            ack.get("colecciones")?.let {it.isJsonPrimitive && it.asJsonPrimitive.isNumber && it.asString==CollectionRules.list(payload).size.toString()}==true &&
            runCatching {java.time.Instant.parse(ack.text("updated_at"))}.isSuccess) {
            "Cloudflare no confirmó correctamente la publicación. Consultá el remoto antes de reintentar; la copia local se conservó."
        }
        return ack.text("updated_at")
    }
}

data class CollectionChange(val id:String,val before:JsonObject?,val after:JsonObject?) {
    val kind get()=when {before==null -> "Agregar";after==null -> "Eliminar";else -> "Cambiar"}
    val fields get()=(before?.keySet().orEmpty()+after?.keySet().orEmpty()).filter {before?.get(it)!=after?.get(it)}.sorted()
}
data class CollectionSyncReview(val local:JsonObject,val remote:JsonObject,val changes:List<CollectionChange>) {
    val orderChanged get()=CollectionRules.list(local).map {it.text("id")}.let {ids ->
        val old=CollectionRules.list(remote).map {it.text("id")};ids.toSet()==old.toSet() && ids!=old
    }
    val destructive get()=changes.any {it.before!=null} || orderChanged
    val empty get()=CollectionRules.list(local).isEmpty()
}

object CollectionSync {
    fun document(root:JsonObject):JsonObject {
        val valid=CollectionRules.parse(root.toString())
        // JavaScript's Number.isSafeInteger plus production Worker's URL rule.
        CollectionRules.list(valid).forEach {row ->
            row.get("tmdb_collection_id")?.takeUnless {it.isJsonNull}?.let {
                require(it.asLong<=9007199254740991L) {"ID TMDB fuera del rango seguro del Worker."}
            }
            for(field in listOf("poster_url","backdrop_url")) {
                val url=row.text(field)
                require(url.isEmpty() || Regex("https://[^\\s/@]+(?:/[^\\s]*)?").matches(url)) {"$field no cumple el formato del Worker."}
            }
        }
        return JsonObject().apply {addProperty("format",CollectionRules.FORMAT);addProperty("version",1);add("colecciones",valid.get("colecciones").deepCopy())}
    }
    fun review(local:JsonObject,remote:JsonObject):CollectionSyncReview {
        val l=document(local);val r=document(remote)
        val proposed=CollectionRules.list(l).associateBy {it.text("id")};val old=CollectionRules.list(r).associateBy {it.text("id")}
        val changes=(old.keys+proposed.keys).distinct().mapNotNull {id ->
            val before=old[id];val after=proposed[id]
            if(before==after) null else CollectionChange(id,before?.deepCopy(),after?.deepCopy())
        }
        // Root array order is preserved too, even if every individual object is identical.
        return CollectionSyncReview(l,r,changes)
    }
    fun authorize(review:CollectionSyncReview,current:JsonObject,destructive:Boolean,empty:Boolean):JsonObject {
        check(document(current)==review.local) {"Las colecciones locales cambiaron; consultá y revisá de nuevo."}
        check(!review.destructive || destructive) {"Confirmá explícitamente el reemplazo o eliminación de colecciones remotas."}
        check(!review.empty || empty) {"Confirmá explícitamente la publicación vacía: elimina todas las colecciones remotas."}
        return review.local.deepCopy()
    }
    fun merge(local:JsonObject,remote:JsonObject,selected:Set<String>):JsonObject {
        val l=document(local);val r=document(remote);val rows=CollectionRules.list(r).associateBy {it.text("id")}
        require(selected.isNotEmpty() && selected.all {it in rows}) {"Seleccioná colecciones remotas válidas para recuperar."}
        val localRows=CollectionRules.list(l);val ids=localRows.map {it.text("id")}.toSet()
        l.add("colecciones",JsonArray().apply {
            localRows.forEach {add(if(it.text("id") in selected) rows.getValue(it.text("id")).deepCopy() else it.deepCopy())}
            CollectionRules.list(r).filter {it.text("id") in selected && it.text("id") !in ids}.forEach {add(it.deepCopy())}
        })
        return document(l)
    }
}

/** Collections are exclusively server-managed on the ordinary audiovisual publishing route. */
internal object CatalogCollections {
    fun without(root:JsonObject)=root.deepCopy().apply {remove("colecciones");remove("colecciones_version")}
    fun fingerprint(root:JsonObject)=CatalogRules.fingerprint(without(root))
    fun carry(root:JsonObject,remote:JsonObject)=without(root).apply {
        for(key in listOf("colecciones","colecciones_version")) remote.get(key)?.let {add(key,it.deepCopy())}
    }
}
