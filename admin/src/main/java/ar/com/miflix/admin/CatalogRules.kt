package ar.com.miflix.admin

import com.google.gson.*
import java.net.URI
import java.time.Instant
import java.security.MessageDigest

fun JsonObject.text(key: String): String = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
fun JsonObject.number(key: String): Long? = text(key).toLongOrNull()
fun JsonObject.array(key: String): JsonArray = get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
fun now(): String = Instant.now().toString()
fun JsonObject.putText(key: String, value: String) { addProperty(key, value.trim()) }

object CatalogRules {
    const val MAX_BYTES = 5 * 1024 * 1024
    fun parse(raw: String): JsonObject {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "El catálogo supera 5 MB." }
        val root = try { JsonParser.parseString(raw).asJsonObject } catch (_: Exception) {
            error("El servidor no devolvió un catálogo JSON válido.")
        }
        require(root.text("schema_version") == "1") { "Se necesita schema_version 1." }
        require(root.get("items")?.isJsonArray == true) { "Falta la lista de títulos." }
        val ids = mutableSetOf<String>()
        root.array("items").forEach { entry ->
            require(entry.isJsonObject) { "Hay una entrada inválida." }
            val item = entry.asJsonObject
            require(item.text("id").isNotBlank() && ids.add(item.text("id"))) { "ID vacío o repetido." }
            require(item.text("titulo").isNotBlank()) { "Hay un título vacío." }
            require(item.text("tipo") in setOf("pelicula", "serie", "anime")) { "Categoría desconocida." }
            validateSeasons(item)
        }
        return root
    }
    private fun validateSeasons(item: JsonObject) {
        if (item.has("temporadas") && !item.get("temporadas").isJsonNull) {
            require(item.get("temporadas").isJsonArray) { "Temporadas inválidas." }
            val nums = mutableSetOf<Long>()
            item.array("temporadas").forEach { s ->
                require(s.isJsonObject) { "Temporada inválida." }
                val season = s.asJsonObject
                val num = season.number("numero")
                require(num != null && num >= 0 && nums.add(num)) { "Número de temporada inválido o repetido." }
                if (season.has("episodios") && !season.get("episodios").isJsonNull) {
                    require(season.get("episodios").isJsonArray) { "Episodios inválidos." }
                    val eps = mutableSetOf<Long>()
                    season.array("episodios").forEach { e ->
                        require(e.isJsonObject) { "Episodio inválido." }
                        val n = e.asJsonObject.number("numero")
                        require(n != null && n >= 0 && eps.add(n)) { "Episodio inválido o repetido." }
                    }
                }
            }
        }
    }
    fun items(root: JsonObject): List<JsonObject> = root.array("items").map { it.asJsonObject }
    fun duplicate(root: JsonObject, candidate: JsonObject, excludeId: String?=candidate.text("id")): JsonObject? {
        if (candidate.text("tipo") != "pelicula" || candidate.number("tmdb_id") == null) return null
        return items(root).firstOrNull { it.text("tipo") == "pelicula" &&
            it.number("tmdb_id") == candidate.number("tmdb_id") && it.text("id") != excludeId }
    }
    fun save(root: JsonObject, candidate: JsonObject): JsonObject {
        val copy = root.deepCopy()
        val result = candidate.deepCopy()
        require(result.text("titulo").isNotBlank()) { "El título es obligatorio." }
        if (result.text("id").isBlank()) {
            val digest = MessageDigest.getInstance("SHA-256").digest(
                "${result.text("tipo")}:${result.text("titulo").lowercase()}".toByteArray())
            result.addProperty("id", "${result.text("tipo")}-manual-${digest.take(6).joinToString("") { "%02x".format(it) }}")
        }
        // Catalog identity, not a missing date or a creation-screen flag, determines an insertion.
        val existing = items(copy).firstOrNull { it.text("id") == result.text("id") }
        if (existing == null) result.addProperty("fecha_agregado", now())
        else if (existing.has("fecha_agregado")) result.add("fecha_agregado", existing.get("fecha_agregado").deepCopy())
        else result.remove("fecha_agregado") // Historical records stay undated, even with a partial/imported candidate.
        result.addProperty("actualizado", now())
        val list = items(copy).filter { it.text("id") != result.text("id") } + result
        copy.add("items", JsonArray().apply { list.sortedBy { it.text("titulo").lowercase() }.forEach { add(it) } })
        copy.addProperty("generated_at", now())
        return parse(copy.toString())
    }
    fun publicCopy(root: JsonObject): JsonObject = root.deepCopy().apply {
        items(this).forEach { item ->
            item.remove("poster_local")
            item.array("temporadas").forEach { s -> s.asJsonObject.array("episodios").forEach { e -> e.asJsonObject.remove("local_path") } }
        }
    }
    fun removeItem(root: JsonObject,id: String): JsonObject {
        require(items(root).any {it.text("id")==id}) {"El contenido ya no existe en el catálogo."}
        return root.deepCopy().apply {
            val entries=array("items")
            entries.remove(entries.indexOfFirst {it.asJsonObject.text("id")==id})
            addProperty("generated_at",now())
        }.let {parse(it.toString())}
    }
    fun fingerprint(root: JsonObject): String = canonical(root.deepCopy().apply { remove("generated_at") })
    private fun canonical(element: JsonElement): String = when {
        element.isJsonObject -> element.asJsonObject.entrySet().sortedBy { it.key }.joinToString(prefix="{", postfix="}") { Gson().toJson(it.key)+":"+canonical(it.value) }
        element.isJsonArray -> element.asJsonArray.joinToString(prefix="[",postfix="]") { canonical(it) }
        else -> element.toString()
    }
    fun mergeSeason(item: JsonObject, incoming: JsonObject): JsonObject {
        val copy = item.deepCopy()
        val number = incoming.number("numero") ?: error("Temporada sin número.")
        val old = copy.array("temporadas").map { it.asJsonObject }.firstOrNull { it.number("numero") == number }
        val merged = old?.deepCopy() ?: JsonObject()
        incoming.entrySet().filter { it.key != "episodios" }.forEach { merged.add(it.key,it.value.deepCopy()) }
        if (incoming.array("episodios").size() > 0) {
            val oldEps = old?.array("episodios")?.map { it.asJsonObject }.orEmpty()
            val newEps = incoming.array("episodios").map { entry ->
                val e = entry.asJsonObject
                val prev = oldEps.firstOrNull { e.number("tmdb_id") != null && it.number("tmdb_id") == e.number("tmdb_id") }
                    ?: oldEps.firstOrNull { it.number("numero") == e.number("numero") }
                (prev?.deepCopy() ?: JsonObject()).apply {
                    e.entrySet().forEach { (key,value) ->
                        if (key !in setOf("telegram_url","telegram_message_id","local_path","archivo") || (!value.isJsonNull && value.asString.isNotBlank())) add(key,value.deepCopy())
                    }
                }
            }
            // Preserve manually added episodes not returned by the provider.
            val missing = oldEps.filter { o -> newEps.none { it.number("numero") == o.number("numero") } }
            merged.add("episodios",JsonArray().apply { (newEps+missing).sortedBy { it.number("numero") }.forEach { add(it) } })
        }
        val all = copy.array("temporadas").map { it.asJsonObject }.filter { it.number("numero") != number } + merged
        copy.add("temporadas",JsonArray().apply { all.sortedBy { it.number("numero") }.forEach { add(it) } })
        return copy
    }
    fun updateVersion(old: JsonObject, link: String, audio: String, quality: String): JsonObject = old.deepCopy().apply {
        require(TelegramLinks.valid(link) != null) { "Indicá un enlace válido de la nueva versión." }
        addProperty("telegram_url",link.trim()); TelegramLinks.messageId(link)?.let { addProperty("telegram_message_id",it) }
        if (audio.isNotBlank()) addProperty("audio",audio)
        if (quality.isNotBlank()) addProperty("calidad",quality)
        array("temporadas").forEach { s -> if (s.asJsonObject.number("numero") == 0L) s.asJsonObject.array("episodios").forEach { e ->
            e.asJsonObject.addProperty("telegram_url",link.trim()); TelegramLinks.messageId(link)?.let { e.asJsonObject.addProperty("telegram_message_id",it) }
        } }
    }
}

object TelegramLinks {
    fun valid(value: String): String? {
        val raw = value.trim()
        return try {
            val uri = URI(raw)
            if (uri.scheme != "https" || uri.host?.lowercase() !in setOf("t.me","telegram.me") || uri.userInfo != null || uri.port != -1) return null
            val parts = uri.path.split('/').filter { it.isNotBlank() }
            val numbers = if (parts.firstOrNull() == "c") {
                if (parts.size !in 3..4 || parts[1].toLongOrNull() == null) return null
                parts.drop(2)
            } else {
                if (parts.size !in 2..3 || !parts[0].matches(Regex("[A-Za-z0-9_]+"))) return null
                parts.drop(1)
            }
            if (numbers.any { (it.toLongOrNull() ?: 0) <= 0 }) null else raw
        } catch (_: Exception) { null }
    }
    fun messageId(value: String): Long? = valid(value)?.let { URI(it).path.trimEnd('/').substringAfterLast('/').toLongOrNull() }
    fun extract(text: String): List<String> = Regex("https://(?:t\\.me|telegram\\.me)/[^\\s<>]+").findAll(text)
        .map { it.value.trimEnd('.',',',')',']',';') }.mapNotNull(::valid).distinct().toList()
    fun checkpoint(item: JsonObject, original: String): JsonObject {
        val link = valid(original) ?: error("El checkpoint necesita el enlace original de la publicación.")
        return JsonObject().apply {
            require(item.text("tipo") in AdminCheckpoints.categories.map { it.first }) { "Categoría de checkpoint desconocida." }
            addProperty("tipo",item.text("tipo")); addProperty("titulo",item.text("titulo")); addProperty("anio",item.text("anio"))
            listOf("id","tmdb_id","imdb_id","anilist_id","mal_id").forEach { key -> item.get(key)?.let { add(key,it.deepCopy()) } }
            addProperty("telegram_url",link)
            messageId(link)?.let { addProperty("telegram_message_id",it) }; addProperty("procesado_at",now())
        }
    }
}
