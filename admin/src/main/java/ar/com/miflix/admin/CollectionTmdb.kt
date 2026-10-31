package ar.com.miflix.admin

import com.google.gson.*
import java.util.concurrent.atomic.AtomicBoolean

interface CollectionProvider {
    fun search(query:String,page:Int,language:String):CollectionSearch
    fun detail(id:Long,language:String):JsonObject
    fun images(id:Long):List<CollectionImage>
    fun cancel()
}
data class CollectionSearch(val results:List<JsonObject>,val page:Int,val pages:Int)
data class CollectionImage(val field:String,val url:String,val preview:String,val language:String)
data class CollectionMatch(val part:JsonObject,val exact:List<String>,val possible:List<String>)
class TmdbCollections(private val metadata:HttpMetadata):CollectionProvider {
    private val cancelled=AtomicBoolean()
    private fun active() {check(!cancelled.get()) {"Consulta de colección cancelada."}}
    override fun cancel() {cancelled.set(true);metadata.cancelRecovery()}
    override fun search(query:String,page:Int,language:String):CollectionSearch {
        active()
        query.trim().toLongOrNull()?.takeIf {it>0}?.let {id ->return CollectionSearch(listOf(detail(id,language)),1,1)}
        val response=metadata.collectionRequest("/search/collection",language,query,page);active()
        check(response.get("results")?.isJsonArray==true) {"TMDB: búsqueda de colecciones incompleta."}
        return CollectionSearch(response.array("results").filter {it.isJsonObject}.map {it.asJsonObject}
            .filter {it.number("id")?.let {id->id>0}==true && it.text("name").isNotBlank()},page,(response.number("total_pages") ?: 1).coerceIn(1,500).toInt())
    }
    override fun detail(id:Long,language:String):JsonObject {
        require(id>0);active();val result=metadata.collectionRequest("/collection/$id",language);active()
        check(result.number("id")==id && result.text("name").isNotBlank() && result.get("parts")?.isJsonArray==true) {"TMDB: detalle de colección incompleto o identidad distinta."}
        return result
    }
    override fun images(id:Long):List<CollectionImage> {
        require(id>0);active();val result=metadata.collectionRequest("/collection/$id/images");active()
        check(result.number("id")==id) {"TMDB: imágenes de otra colección."}
        return listOf("posters" to "poster_url","backdrops" to "backdrop_url").flatMap {(array,field) ->
            result.array(array).filter {it.isJsonObject}.map {it.asJsonObject}.mapNotNull {image ->
                val path=image.text("file_path")
                if(!validPath(path)) null else CollectionImage(field,url(path,field),"https://image.tmdb.org/t/p/${if(field=="poster_url") "w342" else "w780"}$path",image.text("iso_639_1").ifBlank {"sin idioma"})
            }
        }.distinctBy {it.field to it.url}
    }
    companion object {
        fun validPath(path:String)=path.matches(Regex("/[A-Za-z0-9_.-]+\\.(?:jpg|jpeg|png|webp)")) && !path.contains("..")
        fun url(path:String,field:String):String = if(validPath(path)) "https://image.tmdb.org/t/p/${if(field=="poster_url") "w500" else "w1280"}$path" else ""
    }
}
object CollectionImport {
    private fun key(s:String)=java.text.Normalizer.normalize(s.lowercase(java.util.Locale.ROOT),java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"),"").replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
    fun match(details:JsonObject,catalog:JsonObject):List<CollectionMatch> {
        val movies=CatalogRules.items(catalog).filter {it.text("tipo")=="pelicula"}
        return details.array("parts").filter {it.isJsonObject}.map {it.asJsonObject}.filter {it.number("id")?.let {id->id>0}==true}.distinctBy {it.number("id")}.map {part ->
            val id=part.number("id")
            val exact=movies.filter {it.number("tmdb_id")==id}.map {it.text("id")}
            val name=key(part.text("title"));val original=key(part.text("original_title"));val year=part.text("release_date").take(4)
            val possible=movies.filter {m ->m.number("tmdb_id")==null && year.isNotBlank() && (m.text("anio").ifBlank {m.text("year")}==year) &&
                (key(m.text("titulo")) in setOf(name,original).filter {it.isNotBlank()} || key(m.text("titulo_original")).let {it.isNotBlank() && it in setOf(name,original)})}.map {it.text("id")}
            CollectionMatch(part.deepCopy(),exact,possible)
        }
    }
    /** Never automatic: UI submits an explicit subset of metadata fields, and confirmed existing members. */
    fun apply(draft:JsonObject,details:JsonObject,fields:Set<String>,members:Set<String>,catalog:JsonObject,language:String):JsonObject {
        require(fields.all {it in setOf("nombre","descripcion","poster_url","backdrop_url")}) {"Campo de colección no permitido."}
        require(details.number("id")?.let {it>0}==true) {"Colección TMDB sin identidad."}
        val result=CollectionRules.add(draft,members,catalog)
        val values=mapOf("nombre" to details.text("name"),"descripcion" to MetadataRecovery.clean(details.text("overview")),
            "poster_url" to TmdbCollections.url(details.text("poster_path"),"poster_url"),"backdrop_url" to TmdbCollections.url(details.text("backdrop_path"),"backdrop_url"))
        fields.forEach {field ->val value=values.getValue(field);require(value.isNotBlank()) {"La fuente no devuelve $field; conservamos el valor anterior."};result.addProperty(field,value)}
        if(fields.isNotEmpty() || members.isNotEmpty()) {
            result.addProperty("origen","tmdb");result.addProperty("tmdb_collection_id",details.number("id"))
            result.addProperty("idioma_tmdb",language)
        }
        return result
    }
}
