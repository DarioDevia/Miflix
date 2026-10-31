package ar.com.miflix.admin

import com.google.gson.*
import java.net.URI
import java.util.UUID

/** Versioned additive contract. Only stable MiFlix IDs are stored, never copies of catalog items. */
object CollectionRules {
    const val FORMAT="miflix-collections"
    const val MAX_BYTES=2*1024*1024
    fun empty()=JsonObject().apply {addProperty("format",FORMAT);addProperty("version",1);add("colecciones",JsonArray())}
    fun fresh()=JsonObject().apply {
        addProperty("id","col-"+UUID.randomUUID());addProperty("nombre","");addProperty("descripcion","")
        addProperty("origen","manual");addProperty("orden",0);addProperty("visible",true);add("titulos",JsonArray())
    }
    private fun string(obj:JsonObject,key:String,required:Boolean=false,max:Int=4000):String? {
        val value=obj.get(key)
        if(value==null || value.isJsonNull) {require(!required) {"Falta $key en la colección."};return null}
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {"$key debe ser texto."}
        val text=value.asString
        require(text.length<=max && (!required || text.isNotBlank())) {"$key está vacío o es demasiado largo."}
        return text
    }
    fun validUrl(value:String):Boolean = value.isEmpty() || runCatching {
        val u=URI(value);val host=u.host.orEmpty().lowercase(java.util.Locale.ROOT)
        u.scheme=="https" && host.isNotBlank() && host!="thetvdb.com" && !host.endsWith(".thetvdb.com") && host!="thetvdb.plex.tv" && !host.endsWith(".thetvdb.plex.tv") && u.userInfo==null && value.length<=2000 && value.none {it.isWhitespace()}
    }.getOrDefault(false)
    fun validate(value:JsonObject) {
        string(value,"id",true,300);string(value,"nombre",true,200);string(value,"descripcion",false,12000)
        require(string(value,"origen",true) in setOf("manual","tmdb")) {"Origen de colección desconocido; no se modificó."}
        val tmdb=value.get("tmdb_collection_id")
        require(tmdb==null || tmdb.isJsonNull || (tmdb.isJsonPrimitive && tmdb.asJsonPrimitive.isNumber && tmdb.asString.matches(Regex("[1-9][0-9]*")) && value.number("tmdb_collection_id")!=null)) {"ID de colección TMDB inválido."}
        require(value.text("origen")!="tmdb" || value.number("tmdb_collection_id")!=null) {"Falta ID TMDB de la colección."}
        for(key in listOf("poster_url","backdrop_url")) require(validUrl(string(value,key,false,2000).orEmpty())) {"$key requiere una URL HTTPS válida, sin credenciales y ajena a TheTVDB."}
        val order=value.get("orden")
        require(order!=null && order.isJsonPrimitive && order.asJsonPrimitive.isNumber && order.asString.matches(Regex("[0-9]+")) && (value.number("orden") ?: -1) in 0..1000000) {"Orden de presentación inválido (0 a 1000000)."}
        require(value.get("visible")?.let {it.isJsonPrimitive && it.asJsonPrimitive.isBoolean}==true) {"Visibilidad inválida."}
        require(value.get("titulos")?.isJsonArray==true) {"Falta la lista de IDs de títulos."}
        val ids=ids(value)
        require(ids.size<=10000 && ids.size==ids.distinct().size) {"Hay IDs de títulos duplicados o demasiados integrantes."}
        value.array("titulos").forEach {require(it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotBlank() && it.asString.length<=300) {"Referencia de título inválida; solo se admiten IDs MiFlix."}}
    }
    fun ids(value:JsonObject)=value.array("titulos").map {require(it.isJsonPrimitive && it.asJsonPrimitive.isString) {"Referencia de título inválida."};it.asString}
    fun list(root:JsonObject)=root.array("colecciones").map {require(it.isJsonObject) {"Colección inválida."};it.asJsonObject}
    fun parse(raw:String):JsonObject {
        require(raw.toByteArray(Charsets.UTF_8).size<=MAX_BYTES) {"El respaldo de colecciones supera 2 MB."}
        val root=try {JsonParser.parseString(raw).asJsonObject} catch(_:Exception) {error("Respaldo de colecciones JSON inválido.")}
        require(root.text("format")==FORMAT && root.get("version")?.let {it.isJsonPrimitive && it.asJsonPrimitive.isNumber && it.asString=="1"}==true) {"Formato o versión de colecciones desconocido. No se sobrescribió."}
        require(root.get("colecciones")?.isJsonArray==true) {"Faltan colecciones en el respaldo."}
        val values=list(root);require(values.size<=1000) {"Máximo 1000 colecciones."}
        values.forEach(::validate);require(values.map {it.text("id")}.distinct().size==values.size) {"IDs de colección duplicados."}
        return root
    }
    fun save(root:JsonObject,draft:JsonObject,editingId:String?):JsonObject {
        parse(root.toString());validate(draft)
        val existing=list(root)
        if(editingId!=null) require(draft.text("id")==editingId && existing.count {it.text("id")==editingId}==1) {"La colección editada ya no existe o su ID cambió."}
        else require(existing.none {it.text("id")==draft.text("id")}) {"El ID de colección ya existe."}
        return root.deepCopy().apply {add("colecciones",JsonArray().apply {
            existing.forEach {add(if(it.text("id")==editingId) draft.deepCopy() else it.deepCopy())}
            if(editingId==null) add(draft.deepCopy())
        })}.also {parse(it.toString())}
    }
    fun remove(root:JsonObject,id:String):JsonObject {
        parse(root.toString());require(list(root).any {it.text("id")==id}) {"La colección ya no existe."}
        return root.deepCopy().apply {add("colecciones",JsonArray().apply {list(root).filter {it.text("id")!=id}.forEach {add(it.deepCopy())}})}
    }
    fun add(draft:JsonObject,selected:Set<String>,catalog:JsonObject):JsonObject {
        val known=CatalogRules.items(catalog).map {it.text("id")}.toSet();val previous=ids(draft)
        require(selected.all {it in known || it in previous}) {"Solo se pueden agregar títulos existentes en MiFlix."}
        return members(draft,previous+selected.filter {it !in previous})
    }
    fun members(draft:JsonObject,ids:List<String>)=draft.deepCopy().apply {add("titulos",JsonArray().apply {ids.distinct().forEach {add(it)}})}
    fun move(draft:JsonObject,index:Int,delta:Int):JsonObject {
        val ids=ids(draft).toMutableList();val target=index+delta
        require(index in ids.indices && target in ids.indices) {"No se puede mover más en esa dirección."}
        val id=ids.removeAt(index);ids.add(target,id);return members(draft,ids)
    }
}

/** Atomic, separate from the catalog envelope: collection saves never set catalog pending or publish. */
class CollectionRepository(private val store:StateStore) {
    var error:String="";private set
    private var state:JsonObject=try {
        (store.read() ?: JsonObject()).also {s ->
            if(s.entrySet().isNotEmpty()) {
                require(s.text("storage_version")=="1") {"Versión de almacenamiento de colecciones desconocida."}
                CollectionRules.parse(s.get("data").toString())
                s.get("backup")?.let {CollectionRules.parse(it.toString())}
                s.get("draft")?.let {entry ->
                    require(entry.isJsonObject);val candidate=entry.asJsonObject.deepCopy()
                    candidate.remove("poster_url");candidate.remove("backdrop_url")
                    if(candidate.text("nombre").isBlank()) candidate.addProperty("nombre","Borrador")
                    CollectionRules.validate(candidate)
                }
            }
        }
    } catch(_:Exception) {error="No se pudo leer el archivo de colecciones o su versión no es compatible. Se conserva sin sobrescribir; restaurá un respaldo válido explícitamente.";JsonObject()}
    val writable get()=error.isEmpty()
    fun data()=state.get("data")?.takeIf {it.isJsonObject}?.asJsonObject?.deepCopy() ?: CollectionRules.empty()
    fun draft()=state.get("draft")?.takeIf {it.isJsonObject}?.asJsonObject?.deepCopy()
    val editingId get()=state.text("editing_id").ifBlank {null}
    val hasBackup get()=writable && state.get("backup")?.isJsonObject==true
    private fun commit(next:JsonObject) {store.write(next);state=next;error=""}
    fun stage(draft:JsonObject?,editing:String?) {
        check(writable) {error}
        val next=state.deepCopy().apply {addProperty("storage_version",1);if(!has("data")) add("data",CollectionRules.empty())}
        if(draft==null) {next.remove("draft");next.remove("editing_id")} else {next.add("draft",draft.deepCopy());if(editing==null) next.remove("editing_id") else next.addProperty("editing_id",editing)}
        commit(next)
    }
    fun save(draft:JsonObject,editing:String?) {
        check(writable) {error};val previous=data();val fresh=CollectionRules.save(previous,draft,editing)
        commit(state.deepCopy().apply {addProperty("storage_version",1);add("backup",previous);add("data",fresh);remove("draft");remove("editing_id")})
    }
    fun delete(id:String) {
        check(writable) {error};val previous=data();val fresh=CollectionRules.remove(previous,id)
        commit(state.deepCopy().apply {addProperty("storage_version",1);add("backup",previous);add("data",fresh)})
    }
    fun export():String {check(writable) {error};return CollectionRules.parse(data().toString()).toString()}
    fun restore(raw:String) {
        val fresh=CollectionRules.parse(raw)
        // Even a damaged/unknown file is replaced only after explicit validated restore. AtomicFile keeps write failure recoverable.
        val next=state.deepCopy().apply {addProperty("storage_version",1);if(writable) add("backup",data());add("data",fresh);remove("draft");remove("editing_id")}
        commit(next)
    }
    fun undo() {
        check(hasBackup) {"No hay un estado anterior de colecciones para restaurar."}
        val backup=state.getAsJsonObject("backup").deepCopy();restore(backup.toString())
    }
}
