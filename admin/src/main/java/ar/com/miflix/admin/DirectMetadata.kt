package ar.com.miflix.admin

import com.google.gson.*
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** Only an identity is extracted. The supplied address is never fetched. */
data class MetadataLink(val source:String,val mediaType:String,val id:Long) {
    val canonicalUrl get()=if(source=="TMDB") "https://www.themoviedb.org/$mediaType/$id" else "https://anilist.co/anime/$id"
    companion object {
        fun parse(value:String,category:String):MetadataLink {
            val raw=value.trim()
            require(raw.isNotEmpty() && raw.length<=2000 && raw.none {it.isWhitespace() || it.isISOControl()}) {"Pegá un enlace HTTPS válido de TMDB o AniList."}
            val uri=try {URI(raw)} catch(_:Exception) {throw IllegalArgumentException("El enlace no tiene un formato válido.")}
            require(uri.scheme.equals("https",true) && uri.rawUserInfo==null && uri.port in setOf(-1,443)) {"El enlace debe usar HTTPS, sin credenciales ni puertos especiales."}
            val host=uri.host?.lowercase(Locale.ROOT)
            require(host in setOf("themoviedb.org","www.themoviedb.org","anilist.co","www.anilist.co")) {"Solo se admiten los dominios oficiales de TMDB y AniList."}
            val tmdb=host!!.endsWith("themoviedb.org")
            require(!Regex("(?i)%(?:2f|5c|0[0-9a-f]|1[0-9a-f]|7f)").containsMatchIn(uri.rawPath.orEmpty())) {"La ruta contiene caracteres no permitidos."}
            val parts=uri.rawPath.orEmpty().removeSuffix("/").removePrefix("/").split('/').toMutableList()
            if(tmdb && parts.size==3 && parts[0].matches(Regex("[A-Za-z]{2}(?:-[A-Za-z]{2})?"))) parts.removeAt(0)
            val kind=parts.firstOrNull().orEmpty()
            val idText:String
            if(tmdb) {
                require(parts.size==2 && kind in setOf("movie","tv")) {"Usá el enlace de una ficha TMDB /movie/ID o /tv/ID, sin subpáginas."}
                val match=Regex("([0-9]+)(?:-[^/]+)?").matchEntire(parts[1])
                require(match!=null) {"La ficha TMDB debe contener un ID numérico válido."};idText=match.groupValues[1]
            } else {
                require(parts.size in 2..3 && kind=="anime" && (parts.size==2 || parts[2].isNotBlank())) {"Usá una ficha AniList /anime/ID; no se admiten manga ni otras rutas."}
                if(parts.size==3) require(URLDecoder.decode(parts[2],"UTF-8") !in setOf(".","..")) {"La ruta de la ficha no es válida."}
                idText=parts[1];require(idText.matches(Regex("[0-9]+"))) {"El ID AniList debe ser numérico."}
            }
            val id=idText.toIntOrNull()?.takeIf {it>0} ?: throw IllegalArgumentException("El ID debe ser un entero positivo válido para la API.")
            require(category in setOf("pelicula","serie","anime")) {"Categoría desconocida."}
            require(if(tmdb) (category=="anime" || (category=="pelicula" && kind=="movie") || (category=="serie" && kind=="tv")) else category=="anime") {"El tipo del enlace no coincide con la categoría elegida. No se convirtió la ficha."}
            val seen=mutableSetOf<String>()
            uri.rawQuery?.let {query ->
                require(query.isNotBlank()) {"El enlace contiene parámetros vacíos."}
                for(pair in query.split('&')) {
                    val p=pair.split('=',limit=2)
                    val key=URLDecoder.decode(p[0],"UTF-8");val text=URLDecoder.decode(p.getOrElse(1){""},"UTF-8")
                    require(seen.add(key) && (key in setOf("utm_source","utm_medium","utm_campaign","utm_term","utm_content") || (tmdb && key=="language")) && text.length in 1..200 && text.none {it.isISOControl()}) {"El enlace contiene parámetros no admitidos o repetidos."}
                    if(key=="language") require(text.matches(Regex("[A-Za-z]{2}(?:-[A-Za-z]{2})?"))) {"Idioma del enlace inválido."}
                }
            }
            require(uri.rawFragment==null || uri.rawFragment.matches(Regex("[A-Za-z0-9_-]{1,100}"))) {"El enlace contiene un fragmento no admitido."}
            return MetadataLink(if(tmdb) "TMDB" else "AniList",kind,id.toLong())
        }
    }
}

data class DirectMetadataPreview(val link:MetadataLink,val before:JsonObject,val candidate:JsonObject)
data class DirectMetadataChange(val before:JsonObject,val after:JsonObject,val fields:Set<String>) {
    fun backup()=JsonObject().apply {
        addProperty("kind","direct-metadata-v1")
        add("before",before.deepCopy());add("after",after.deepCopy())
        add("paths",JsonArray().apply {fields.forEach {field ->add(JsonObject().apply {addProperty("field",field)})}})
    }
}
object DirectMetadata {
    val labels=linkedMapOf("titulo" to "Título","titulo_original" to "Título original","anio" to "Año","sinopsis" to "Sinopsis","generos" to "Géneros","poster_url" to "Portada","fondo_url" to "Fondo (ambas referencias)","logo_url" to "Logo","trailer_url" to "Tráiler","puntuacion" to "Puntuación y su fuente","protagonistas" to "Reparto","direccion" to "Dirección / créditos","cantidad_episodios" to "Cantidad de episodios","duracion" to "Duración")
    fun matchesId(obj:JsonObject,id:Long)=obj.get("id")?.let {it.isJsonPrimitive && it.asJsonPrimitive.isNumber && it.toString()==id.toString()}==true
    fun available(item:JsonObject)=labels.keys.filter {usable(item.get(it))}.toSet()
    fun initialSelection(base:JsonObject,candidate:JsonObject)=available(candidate).filter {key ->
        !usable(base.get(key)) && (key!="fondo_url" || !usable(base.get("backdrop_url")))
    }.toSet()
    private fun usable(value:JsonElement?)=value!=null && !value.isJsonNull && value.toString() !in setOf("\"\"","[]")
    fun animeMovie(item:JsonObject)=item.text("tipo")=="anime" && item.get("fuentes_metadata")?.takeIf {it.isJsonObject}?.asJsonObject?.get("enlaces_directos")?.takeIf {it.isJsonObject}?.asJsonObject?.get("TMDB")?.takeIf {it.isJsonObject}?.asJsonObject?.let {it.text("fuente")=="TMDB" && it.text("tipo_audiovisual")=="movie"}==true
    fun checkIdentity(base:JsonObject,link:MetadataLink) {
        val key=if(link.source=="TMDB") "tmdb_id" else "anilist_id"
        require(base.number(key)==null || base.number(key)==link.id) {"Esta ficha está vinculada a otro ID ${link.source}. Se conservó la identificación anterior."}
        if(base.text("tipo")=="anime" && link.source=="TMDB" && base.number("tmdb_id")!=null)
            require((link.mediaType=="movie")==animeMovie(base)) {"No se puede reinterpretar una identificación de serie como película o viceversa."}
    }
    fun validateUndo(current:JsonObject,backup:JsonObject):List<String> {
        require(backup.text("kind")=="direct-metadata-v1") {"Respaldo de enlace no compatible."}
        val before=backup.getAsJsonObject("before");val after=backup.getAsJsonObject("after")
        require(current.text("id").isNotBlank() && current.text("id")==before.text("id") && current.text("id")==after.text("id")) {"El respaldo corresponde a otra ficha."}
        for(key in listOf("tipo","tmdb_id","anilist_id","imdb_id","mal_id")) require(current.get(key)==after.get(key)) {"La identificación cambió; no se revirtieron datos de otra ficha."}
        val allowed=labels.keys+setOf("backdrop_url","fuente_puntuacion")
        require(backup.get("paths")?.isJsonArray==true) {"Respaldo de campos incompleto."}
        val fields=backup.array("paths").map {entry ->
            val path=entry.asJsonObject;val field=path.text("field")
            require(field in allowed && !path.has("season") && !path.has("episode")) {"Campo de reversión directa no permitido."}
            require(current.get(field)==after.get(field)) {"Hay cambios posteriores en un campo recuperado. No se sobrescribieron."}
            field
        }
        require(current.get("fuentes_metadata")==after.get("fuentes_metadata")) {"La procedencia cambió después de la recuperación."}
        require(fields.distinct().size==fields.size) {"El respaldo contiene campos repetidos."}
        return fields
    }
    fun undo(current:JsonObject,backup:JsonObject):JsonObject {
        val fields=validateUndo(current,backup);val before=backup.getAsJsonObject("before")
        return current.deepCopy().apply {
            fields.forEach {field ->val old=before.get(field);if(old==null) remove(field) else add(field,old.deepCopy())}
            val sources=before.get("fuentes_metadata");if(sources==null) remove("fuentes_metadata") else add("fuentes_metadata",sources.deepCopy())
        }
    }
    fun apply(preview:DirectMetadataPreview,selected:Set<String>,confirmed:Boolean):DirectMetadataChange {
        require(confirmed) {"Confirmá la identidad y el tipo audiovisual de la ficha."}
        val base=preview.before;val candidate=preview.candidate;val link=preview.link
        require(base.text("tipo")==candidate.text("tipo")) {"La categoría no coincide."}
        checkIdentity(base,link)
        require(candidate.number(if(link.source=="TMDB") "tmdb_id" else "anilist_id")==link.id) {"La fuente no coincide con la identificación solicitada."}
        require(selected.all {it in available(candidate)}) {"La selección contiene campos no disponibles."}
        val linked=base.deepCopy()
        val identity=if(link.source=="TMDB") listOf("tmdb_id","imdb_id") else listOf("anilist_id","mal_id")
        identity.forEach {key -> candidate.get(key)?.takeIf(::usable)?.let {incoming ->
            require(!usable(base.get(key)) || base.get(key)==incoming) {"La identificación $key es distinta. No se sobrescribió la ficha."}
            linked.add(key,incoming.deepCopy())
        }}
        if(linked.text("id").isBlank()) linked.addProperty("id",candidate.text("id"))
        if(linked.text("fuente_principal").isBlank()) linked.addProperty("fuente_principal",if(linked.number("anilist_id")!=null) "AniList" else link.source)
        if(linked.text("fuente").isBlank()) linked.addProperty("fuente","MANUAL")
        require(!linked.has("fuentes_metadata") || linked.get("fuentes_metadata").isJsonObject) {"La procedencia local necesita revisión; no se sobrescribió."}
        val sources=linked.getAsJsonObject("fuentes_metadata") ?: JsonObject().also {linked.add("fuentes_metadata",it)}
        require(!sources.has("enlaces_directos") || sources.get("enlaces_directos").isJsonObject) {"La identificación local es incompatible; no se sobrescribió."}
        val bindings=sources.getAsJsonObject("enlaces_directos") ?: JsonObject().also {sources.add("enlaces_directos",it)}
        require(!bindings.has(link.source) || bindings.get(link.source).isJsonObject) {"La identificación de la fuente es incompatible."}
        val binding=bindings.getAsJsonObject(link.source)?.deepCopy() ?: JsonObject()
        bindings.add(link.source,binding.apply {addProperty("fuente",link.source);addProperty("tipo_audiovisual",link.mediaType);addProperty("id",link.id);addProperty("url",link.canonicalUrl)})
        val after=linked.deepCopy()
        val applied=selected.toMutableSet()
        if("fondo_url" in applied && usable(candidate.get("backdrop_url"))) applied.add("backdrop_url")
        if("puntuacion" in applied && usable(candidate.get("fuente_puntuacion"))) applied.add("fuente_puntuacion")
        val fields=applied.filter {candidate.get(it)!=linked.get(it)}.toSet()
        val records=after.getAsJsonObject("fuentes_metadata")
        require(!records.has("recuperacion_selectiva") || records.get("recuperacion_selectiva").isJsonObject) {"La procedencia de campos es incompatible; se conservó la revisión."}
        val perField=records.getAsJsonObject("recuperacion_selectiva") ?: JsonObject().also {records.add("recuperacion_selectiva",it)}
        fields.forEach {key ->
            after.add(key,candidate.get(key).deepCopy())
            perField.add("title/season/$key",JsonObject().apply {addProperty("fuente",link.source);addProperty("url",link.canonicalUrl);addProperty("idioma",if(link.source=="TMDB") "es-MX" else "original");addProperty("modificaciones","Selección manual de campos desde identificación directa")})
        }
        return DirectMetadataChange(linked,after,fields)
    }
}
