package ar.com.miflix.admin

import com.google.gson.*
import java.security.MessageDigest
import java.text.Normalizer

/** Removal-only compatibility boundary for historical data. This is NOT a metadata provider. */
object RetiredMetadata {
    const val STATE="retired_metadata_cleanup"
    private val rootFields=setOf("titulo","titulo_original","sinopsis","descripcion","poster_url","fondo_url","backdrop_url","logo_url","duracion","anio","year","generos","trailer_url","protagonistas","direccion","puntuacion","fuente_puntuacion","fecha_emision","director","reparto","titulos_alternativos","thumbnail_url")
    private val partFields=rootFields+setOf("nombre","imagen_url","fecha_estreno")
    private val protected=setOf("id","tipo","numero","temporada","fecha_agregado","actualizado","local_path","archivo","poster_local","url","stream_url","enlace","enlace_video","_admin_original","_admin_metadata_recovery")
    private val tags=setOf("fuente","provider","fuente_principal","fuente_episodios","publicacion_bloqueada")
    private fun protected(key:String)=key in protected || key.startsWith("telegram") || key.startsWith("playback") || key.startsWith("video") || (key.endsWith("_id") && key!="tvdb_id")
    fun retired(value:JsonElement?):Boolean = value?.let {
        (it.isJsonPrimitive && it.asString.equals("TheTVDB",true)) || (it.isJsonObject && (
            listOf("fuente","provider","fuente_principal","fuente_episodios").any {key->it.asJsonObject.text(key).equals("TheTVDB",true)} || it.asJsonObject.text("publicacion_bloqueada")=="true"))
    } ?: false
    fun contains(value:JsonElement?):Boolean = when {
        value==null -> false
        value.isJsonObject -> retired(value) || value.asJsonObject.entrySet().any {(key,v)->key=="tvdb_id" || (key.endsWith("_url") && v.isJsonPrimitive && retiredImage(v.asString)) || (key=="fuentes_metadata" && containsSources(v)) || contains(v)}
        value.isJsonArray -> value.asJsonArray.any {contains(it)}
        else -> false
    }
    private fun containsSources(v:JsonElement):Boolean = when {v.isJsonObject -> v.asJsonObject.entrySet().any {retired(it.value) || containsSources(it.value)};v.isJsonArray ->v.asJsonArray.any {containsSources(it)};else ->retired(v)}
    fun hash(value:JsonElement):String {
        val raw=if(value.isJsonPrimitive && value.asJsonPrimitive.isString) Normalizer.normalize(value.asString,Normalizer.Form.NFKC).replace(Regex("\\s+")," ").trim() else value.toString()
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    }
    private fun trackable(v:JsonElement)=v.isJsonPrimitive && v.asJsonPrimitive.isString && v.asString.isNotBlank() && v.asString.toDoubleOrNull()==null
    fun identity(item:JsonObject)=item.text("id").ifBlank {"draft:"+item.text("_admin_metadata_recovery").ifBlank {item.text("tipo")}}
    private data class Node(val key:String,val obj:JsonObject)
    private fun nodes(item:JsonObject):List<Node> {
        val list=mutableListOf(Node("title/season",item))
        item.array("temporadas").filter {it.isJsonObject}.forEach {s ->val season=s.asJsonObject;val n=season.number("numero")
            list.add(Node("$n/season",season))
            season.array("episodios").filter {it.isJsonObject}.forEach {e->list.add(Node("$n/${e.asJsonObject.number("numero")}",e.asJsonObject))}
        };return list
    }
    private fun cleanSourceRecord(item:JsonObject,key:String):Boolean {
        if(retired(item)) return false
        val sources=item.get("fuentes_metadata")?.takeIf {it.isJsonObject}?.asJsonObject ?: return true
        if(sources.entrySet().any {it.key!="recuperacion_selectiva" && retired(it.value)}) return false
        val row=sources.get("recuperacion_selectiva")?.takeIf {it.isJsonObject}?.asJsonObject?.get(key)
        return !retired(row) && !retired(item)
    }
    data class ItemResult(val item:JsonObject,val hashes:Set<String>,val paths:Set<String>,val issues:List<JsonObject>)
    private fun sanitizeItem(item:JsonObject,baselines:List<JsonObject>,oldHashes:Set<String>,oldPaths:Set<String>,verified:JsonObject?):ItemResult {
        val out=item.deepCopy();val current=nodes(out);val lookup=current.associateBy {it.key}
        val restricted=linkedSetOf<String>();val hashes=oldHashes.toMutableSet();val specific=oldPaths.toMutableSet();val issues=mutableListOf<JsonObject>()
        val source=item.get("fuentes_metadata")?.takeIf {it.isJsonObject}?.asJsonObject
        source?.get("recuperacion_selectiva")?.takeIf {it.isJsonObject}?.asJsonObject?.entrySet()?.filter {retired(it.value)}?.forEach {(key,_) ->restricted.add(key)}
        source?.entrySet()?.filter {it.key!="recuperacion_selectiva" && retired(it.value)}?.forEach {(group,_) ->
            val fields=Enrichment.groups[group] ?: rootFields.toList()
            fields.forEach {restricted.add("title/season/$it")}
        }
        current.forEach {node ->
            val global=retired(item) || retired(node.obj) || (item.text("fuente_episodios").equals("TheTVDB",true) && node.key!="title/season")
            if(global) (if(node.key=="title/season") rootFields else partFields).filter {node.obj.has(it)}.forEach {restricted.add("${node.key}/$it")}
            node.obj.entrySet().filter {(key,v)->key=="tvdb_id" || (key.endsWith("_url") && v.isJsonPrimitive && retiredImage(v.asString))}.forEach {(key,_) ->restricted.add("${node.key}/$key")}
        }
        fun collectRetired(v:JsonElement,inside:Boolean=false) {
            if(v.isJsonObject) {
                val marked=inside || retired(v)
                v.asJsonObject.entrySet().filter {it.key !in tags+setOf("atribucion","condiciones","idioma") && !protected(it.key)}.forEach {collectRetired(it.value,marked)}
            } else if(v.isJsonArray) v.asJsonArray.forEach {collectRetired(it,inside)}
            else if(inside && trackable(v)) hashes.add(hash(v))
        }
        current.forEach {n->n.obj.entrySet().filter {it.key !in setOf("temporadas","episodios","fuentes_metadata") && !protected(it.key)}.forEach {collectRetired(it.value)}}
        // Capture exact retired values before removing provenance, including aliases/copies in other fields.
        restricted.forEach {path ->val node=lookup[path.substringBeforeLast('/')];val field=path.substringAfterLast('/');node?.obj?.get(field)?.let {v->
            if(verified?.text(path)!=hash(v)) {specific.add(path+"|"+hash(v));if(trackable(v)) hashes.add(hash(v))}
        }}
        val basis=baselines.filter {identity(it)==identity(item)}
        fun safeBaseline(path:String):Pair<JsonElement,JsonObject?>? {
            val key=path.substringBeforeLast('/');val field=path.substringAfterLast('/')
            return basis.firstNotNullOfOrNull {base ->
                val n=nodes(base).filter {it.key==key}.singleOrNull()?.obj
                val value=n?.get(field)
                if(value==null || value.isJsonNull || contains(value) || value.isJsonPrimitive && value.asString.isBlank() || !cleanSourceRecord(base,path) || retired(n) || (value.isJsonPrimitive && field.endsWith("_url") && retiredImage(value.asString))) null
                else value.deepCopy() to base.get("fuentes_metadata")?.takeIf {it.isJsonObject}?.asJsonObject
            }
        }
        fun issue(path:String,action:String) {issues.add(JsonObject().apply {addProperty("item_id",item.text("id"));addProperty("identity",identity(item));addProperty("titulo",item.text("titulo").ifBlank {item.text("id").ifBlank {"Borrador"}});addProperty("path",path);addProperty("action",action)})}
        fun strip(path:String,node:JsonObject,field:String) {
            if(protected(field)) {issue(path,"Referencia protegida conservada; marca histórica incompatible, revisar.");return}
            val fallback=safeBaseline(path)
            if(fallback!=null) {
                node.add(field,fallback.first);issue(path,"Repuesto desde una copia anterior verificable sin procedencia retirada.")
            } else {
                node.remove(field)
                if(field=="titulo" && path=="title/season/titulo") node.addProperty(field,"Contenido ${item.text("id").ifBlank {"local"}} · revisar título")
                issue(path,"Dato restringido apartado; completar desde TMDB, TVmaze o edición propia. Original en respaldo privado.")
            }
            // Remove only the obsolete field record; never relabel it. Restore earlier record if independently known.
            val recovery=out.get("fuentes_metadata")?.takeIf {it.isJsonObject}?.asJsonObject?.get("recuperacion_selectiva")?.takeIf {it.isJsonObject}?.asJsonObject
            if(recovery?.has(path)==true) {
                recovery.remove(path)
                fallback?.second?.get("recuperacion_selectiva")?.takeIf {it.isJsonObject}?.asJsonObject?.get(path)?.takeIf {!retired(it)}?.let {recovery.add(path,it.deepCopy())}
            }
        }
        current.forEach {node ->
            node.obj.entrySet().toList().forEach fields@{(field,value) ->
                val path="${node.key}/$field"
                if(field in setOf("temporadas","episodios","fuentes_metadata") || field in tags) return@fields
                val known=trackable(value) && hash(value) in hashes || "$path|${hash(value)}" in specific
                val receipt=verified?.text(path)==hash(value)
                val previous=safeBaseline(path)?.first
                val sameTrusted=previous==value
                if((path in restricted && !receipt) || (!protected(field) && known && !receipt && !sameTrusted)) strip(path,node.obj,field)
                else if(!protected(field)) {
                    // Unknown metadata objects explicitly attributed to the retired provider are quarantined as objects.
                    fun scrub(v:JsonElement,p:String):JsonElement? {
                        if(v.isJsonObject && retired(v)) {issue(p,"Objeto de metadatos retirado al respaldo privado; revisar.");return null}
                        if(v.isJsonObject) v.asJsonObject.entrySet().toList().forEach {(a,b)->if(!protected(a)) {
                            val clean=scrub(b,"$p/$a");if(clean==null) v.asJsonObject.remove(a) else v.asJsonObject.add(a,clean)
                        }}
                        else if(v.isJsonArray) {
                            val clean=JsonArray();v.asJsonArray.forEachIndexed {i,e->scrub(e,"$p/$i")?.let {clean.add(it)}};return clean
                        } else if(trackable(v) && hash(v) in hashes) {issue(p,"Copia de un dato restringido apartada; revisar.");return null}
                        return v
                    }
                    if(value.isJsonObject || value.isJsonArray) {
                        val clean=scrub(value,path);if(clean==null) node.obj.remove(field) else node.obj.add(field,clean)
                    }
                }
            }
            node.obj.entrySet().filter {(k,v)->k in tags && (retired(v) || k=="publicacion_bloqueada")}.map {it.key}.forEach {node.obj.remove(it)}
        }
        source?.let {
            val sources=out.getAsJsonObject("fuentes_metadata")
            sources.entrySet().filter {it.key!="recuperacion_selectiva" && retired(it.value)}.map {it.key}.forEach {sources.remove(it)}
            val rec=sources.get("recuperacion_selectiva")?.takeIf {it.isJsonObject}?.asJsonObject
            rec?.entrySet()?.filter {retired(it.value)}?.map {it.key}?.forEach {key->
                // Unknown paths do not identify a field reliably; keep playback/identity intact and report specifically.
                if(key !in restricted || lookup[key.substringBeforeLast('/')]==null) issue(key,"Procedencia con ruta no identificable; no se atribuyeron ni borraron otros campos. Revisar el respaldo.")
                rec.remove(key)
            }
        }
        return ItemResult(out,hashes,specific,issues)
    }
    fun retiredImage(url:String):Boolean=runCatching {val host=java.net.URI(url).host.orEmpty().lowercase(java.util.Locale.ROOT);host=="thetvdb.com" || host.endsWith(".thetvdb.com") || host=="thetvdb.plex.tv" || host.endsWith(".thetvdb.plex.tv")}.getOrDefault(false)
    fun label(path:String):String {
        val p=path.split('/');if(p.size!=3) return path
        val field=mapOf("titulo" to "Título","sinopsis" to "Sinopsis","nombre" to "Nombre","poster_url" to "Póster","backdrop_url" to "Fondo","fondo_url" to "Fondo","imagen_url" to "Imagen","duracion" to "Duración","fecha_emision" to "Fecha de emisión")[p[2]] ?: p[2]
        return (if(p[0]=="title") "Ficha" else if(p[1]=="season") "Temporada ${p[0]}" else "T${p[0]} · Episodio ${p[1]}")+" · "+field
    }
    data class Migration(val state:JsonObject,val changed:Boolean)
    fun migrate(state:JsonObject,previous:JsonObject?=null):Migration {
        val existing=state.get(STATE)?.takeIf {it.isJsonObject}?.asJsonObject
        if((existing?.get("items")?.takeIf {it.isJsonObject}?.asJsonObject?.size() ?: 0)==0 && (existing?.array("global_hashes")?.size() ?: 0)==0 &&
            listOf("catalog","draft","base","metadata_undo").none {contains(state.get(it))}) return Migration(state,false)
        val next=state.deepCopy();val ledger=state.get(STATE)?.takeIf {it.isJsonObject}?.asJsonObject?.deepCopy() ?: JsonObject()
        val bans=ledger.get("items")?.takeIf {it.isJsonObject}?.asJsonObject ?: JsonObject().also {ledger.add("items",it)}
        val global=ledger.array("global_hashes").map {it.asString}.toMutableSet()
        val issues=ledger.array("issues").map {it.deepCopy().asJsonObject}.toMutableList()
        val verified=state.get("retired_metadata_verified")?.takeIf {it.isJsonObject}?.asJsonObject
        val before=state.get("metadata_undo")?.takeIf {it.isJsonObject}?.asJsonObject?.get("before")?.takeIf {it.isJsonObject}?.asJsonObject
        val after=state.get("metadata_undo")?.takeIf {it.isJsonObject}?.asJsonObject?.get("after")?.takeIf {it.isJsonObject}?.asJsonObject
        val base=state.get("base")?.takeIf {it.isJsonObject}?.asJsonObject
        val historical=listOfNotNull(before,after)+base?.let {CatalogRules.items(it)}.orEmpty()
        val active=listOfNotNull(state.get("catalog")?.takeIf {it.isJsonObject}?.asJsonObject?.let {CatalogRules.items(it)}).flatten()+listOfNotNull(state.get("draft")?.takeIf {it.isJsonObject}?.asJsonObject)
        // Learn old marked values even if somebody already changed a label or copied a field locally.
        (historical+active).forEach {item ->if(contains(item)) {
            val id=identity(item);val old=bans.get(id)?.takeIf {it.isJsonObject}?.asJsonObject
            val scan=sanitizeItem(item,emptyList(),old?.array("hashes")?.map {it.asString}?.toSet().orEmpty(),old?.array("paths")?.map {it.asString}?.toSet().orEmpty(),verified?.get(id)?.takeIf {it.isJsonObject}?.asJsonObject)
            fun longValues(v:JsonElement) {
                if(v.isJsonObject) v.asJsonObject.entrySet().forEach {longValues(it.value)}
                else if(v.isJsonArray) v.asJsonArray.forEach {longValues(it)}
                else if(trackable(v) && v.asString.length>=32 && hash(v) in scan.hashes) global.add(hash(v))
            }
            longValues(item)
            bans.add(id,JsonObject().apply {add("hashes",JsonArray().apply {scan.hashes.sorted().forEach {add(it)}});add("paths",JsonArray().apply {scan.paths.sorted().forEach {add(it)}})})
        }}
        fun clean(item:JsonObject,draft:Boolean=false):JsonObject {
            val oldLedger=previous?.get(STATE)?.takeIf {it.isJsonObject}?.asJsonObject
            val prior=if(draft) previous?.get("draft")?.takeIf {it.isJsonObject}?.asJsonObject else previous?.get("catalog")?.takeIf {it.isJsonObject}?.asJsonObject?.let {CatalogRules.items(it).singleOrNull {old->identity(old)==identity(item)}}
            if(prior==item && oldLedger!=null && oldLedger.get("items")==bans && oldLedger.array("global_hashes").map {it.asString}.toSet()==global) return item.deepCopy()
            val id=identity(item);val b=bans.get(id)?.takeIf {it.isJsonObject}?.asJsonObject
            // base is an independently downloaded server document. Undo.before is usable only when after matches the affected fields.
            val undoBase=before?.takeIf {identity(it)==id && after!=null && identity(after)==id && nodes(item).all {node ->
                val oldNode=nodes(after).find {it.key==node.key}?.obj
                node.obj.entrySet().filter {it.key in rootFields || it.key in partFields}.all {(k,v)->!retired(item.get("fuentes_metadata")?.takeIf {it.isJsonObject}?.asJsonObject?.get("recuperacion_selectiva")?.takeIf {it.isJsonObject}?.asJsonObject?.get("${node.key}/$k")) || oldNode?.get(k)==v}
            }}
            val candidates=base?.let {CatalogRules.items(it).filter {i->identity(i)==id}}.orEmpty()+listOfNotNull(undoBase)
            val result=sanitizeItem(item,candidates,b?.array("hashes")?.map {it.asString}?.toSet().orEmpty()+global,b?.array("paths")?.map {it.asString}?.toSet().orEmpty(),verified?.get(id)?.takeIf {it.isJsonObject}?.asJsonObject)
            result.issues.forEach {row->issues.removeAll {it.text("identity")==id && it.text("path")==row.text("path")};issues.add(row)}
            return result.item
        }
        next.get("catalog")?.takeIf {it.isJsonObject}?.asJsonObject?.let {catalog ->
            val old=catalog.deepCopy();catalog.add("items",JsonArray().apply {CatalogRules.items(old).forEach {add(clean(it))}})
            // Preserve unknown root properties; quarantine only nested objects with explicit retired provenance.
            fun rootExtra(v:JsonElement,path:String):JsonElement? {
                if(v.isJsonObject && retired(v)) {
                    issues.add(JsonObject().apply {addProperty("item_id","");addProperty("identity","catalog");addProperty("titulo","Catálogo");addProperty("path",path);addProperty("action","Objeto de procedencia retirada apartado; otros campos desconocidos preservados.")});return null
                }
                if(v.isJsonObject) v.asJsonObject.entrySet().toList().forEach {(k,e)->val clean=rootExtra(e,"$path/$k");if(clean==null) v.asJsonObject.remove(k) else v.asJsonObject.add(k,clean)}
                if(v.isJsonArray) return JsonArray().apply {v.asJsonArray.forEachIndexed {i,e->rootExtra(e,"$path/$i")?.let {add(it)}}}
                return v
            }
            catalog.entrySet().toList().filter {it.key !in setOf("items","colecciones","colecciones_version")}.forEach {(k,v)->
                if(k in tags && (retired(v) || k=="publicacion_bloqueada")) {
                    catalog.remove(k);issues.add(JsonObject().apply {addProperty("item_id","");addProperty("identity","catalog");addProperty("titulo","Catálogo");addProperty("path",k);addProperty("action","Marca histórica retirada; no se atribuyeron otros campos a ese proveedor.")})
                }
                else {val clean=rootExtra(v,k);if(clean==null) catalog.remove(k) else catalog.add(k,clean)}
            }
            if(catalog!=old) next.addProperty("pending",true)
        }
        next.get("draft")?.takeIf {it.isJsonObject}?.asJsonObject?.let {next.add("draft",clean(it,true))}
        if(bans.size()>0 || issues.isNotEmpty()) {
            ledger.addProperty("version",1);ledger.add("global_hashes",JsonArray().apply {global.sorted().forEach {add(it)}});ledger.add("issues",JsonArray().apply {issues.distinctBy {it.text("identity")+"/"+it.text("path")}.forEach {add(it)}});next.add(STATE,ledger)
        }
        return Migration(next,next!=state)
    }
    /** Pure defense for direct serializers/imports; repository migration also preserves an immutable private backup. */
    fun projection(root:JsonObject):JsonObject=migrate(JsonObject().apply {add("catalog",root.deepCopy())}).state.getAsJsonObject("catalog")
    /** Receipt paths must correspond to values actually returned by a fresh provider call AND written. */
    fun freshPaths(provided:JsonObject,actual:JsonObject):Set<String> {
        val written=nodes(actual).associateBy {it.key}
        return nodes(provided).flatMap {node->
            val fields=if(node.key=="title/season") rootFields else partFields
            node.obj.entrySet().filter {(key,value)->key in fields && !value.isJsonNull && value.toString() !in setOf("\"\"","[]") && written[node.key]?.obj?.get(key)==value}.map {"${node.key}/${it.key}"}
        }.toSet()
    }
    fun verified(state:JsonObject,item:JsonObject,paths:Set<String>):JsonObject=state.deepCopy().apply {
        val receipts=get("retired_metadata_verified")?.takeIf {it.isJsonObject}?.asJsonObject ?: JsonObject().also {add("retired_metadata_verified",it)}
        val row=receipts.get(identity(item))?.takeIf {it.isJsonObject}?.asJsonObject ?: JsonObject().also {receipts.add(identity(item),it)}
        paths.forEach {path->nodes(item).singleOrNull {it.key==path.substringBeforeLast('/')}?.obj?.get(path.substringAfterLast('/'))?.let {row.addProperty(path,hash(it))}}
    }
    fun reidentify(state:JsonObject,from:String,to:String) {
        for(map in listOf(state.get("retired_metadata_verified")?.takeIf {it.isJsonObject}?.asJsonObject,state.get(STATE)?.takeIf {it.isJsonObject}?.asJsonObject?.get("items")?.takeIf {it.isJsonObject}?.asJsonObject)) {
            map?.get(from)?.let {map.add(to,it.deepCopy())}
        }
    }
}
