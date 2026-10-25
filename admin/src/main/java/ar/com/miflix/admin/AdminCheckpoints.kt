package ar.com.miflix.admin

import com.google.gson.JsonObject

/** Private administrative state; never part of the public catalog or its fingerprint. */
object AdminCheckpoints {
    val categories = listOf("pelicula" to "Películas", "serie" to "Series", "anime" to "Anime")
    private val types = categories.map { it.first }.toSet()

    fun migrate(state: JsonObject): JsonObject {
        val next = state.deepCopy()
        if (next.has("checkpoints")) {
            require(next.get("checkpoints").isJsonObject) { "El formato de checkpoints locales no es válido; se conserva sin sobrescribir." }
            val entries = next.getAsJsonObject("checkpoints")
            types.forEach { type -> entries.get(type)?.takeUnless { it.isJsonNull }?.let { cp ->
                require(cp.isJsonObject && cp.asJsonObject.text("tipo").let { it.isBlank() || it == type }) {
                    "Hay un checkpoint local incompatible; se conserva sin sobrescribir."
                }
            } }
        } else {
            val entries = JsonObject()
            next.get("checkpoint")?.takeIf { it.isJsonObject }?.asJsonObject?.let { legacy ->
                // Only the explicit category is reliable. IDs/titles alone cannot distinguish TV/anime/movies.
                legacy.text("tipo").takeIf { it in types }?.let { entries.add(it, legacy.deepCopy()) }
            }
            next.add("checkpoints", entries)
            // Keep the entire legacy record for recovery, including unclassified and unknown fields.
        }
        next.remove("checkpoint_pending")
        next.remove("checkpoint_available")
        return next
    }

    fun get(state: JsonObject, type: String): JsonObject? {
        require(type in types) { "Categoría de checkpoint desconocida." }
        return state.get("checkpoints")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get(type)?.takeIf { it.isJsonObject }?.asJsonObject?.deepCopy()
    }

    fun put(state: JsonObject, checkpoint: JsonObject): JsonObject {
        val type = checkpoint.text("tipo")
        require(type in types) { "El checkpoint necesita una categoría válida." }
        return migrate(state).apply { getAsJsonObject("checkpoints").add(type, checkpoint.deepCopy()) }
    }

    fun unclassifiedLegacy(state: JsonObject): Boolean = state.get("checkpoint")?.let {
        !it.isJsonNull && (!it.isJsonObject || it.asJsonObject.text("tipo") !in types)
    } == true

    /** Called only after the review is confirmed, at the existing local-save boundary. */
    fun forSave(item: JsonObject, original: String, isNew: Boolean, updateMultimedia: Boolean): JsonObject? {
        val type = item.text("tipo")
        require(type in types) { "Categoría de checkpoint desconocida." }
        val processed = original.ifBlank { item.text("telegram_url") }
        val processing = if (type == "pelicula") isNew || updateMultimedia else isNew || original.isNotBlank()
        return if (processing && processed.isNotBlank()) TelegramLinks.checkpoint(item, processed) else null
    }
}
