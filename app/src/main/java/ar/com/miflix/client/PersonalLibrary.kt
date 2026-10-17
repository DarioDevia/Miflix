package ar.com.miflix.client

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface LibraryStorage {
    fun read(): String?
    fun write(json: String)
}

/** Versioned local envelope: catalog IDs survive missing titles and can be exported later. */
internal class PersonalLibraryStore(private val storage: LibraryStorage) {
    private val favorites = linkedSetOf<String>()
    private val version = MutableStateFlow(0L)
    val revision = version.asStateFlow()

    init {
        runCatching {
            val root = JsonParser.parseString(storage.read() ?: "{}").asJsonObject
            root.getAsJsonArray("favorites")?.forEach { value ->
                runCatching { value.asString }.getOrNull()?.takeIf { it.isNotBlank() }?.let(favorites::add)
            }
        }
    }
    fun isFavorite(id: String) = id in favorites
    fun setFavorite(id: String, favorite: Boolean) {
        if (id.isBlank()) return
        val changed = if (favorite) favorites.add(id) else favorites.remove(id)
        if (changed) persist()
    }
    fun favorites(titles: List<Title>): List<Title> = titles.distinctBy { it.id }
        .filter { it.id in favorites }.sortedWith(compareBy<Title> { normalizeSearchText(it.titulo) }.thenBy { it.id })

    fun exportJson(): String = Gson().toJson(mapOf("schemaVersion" to 1, "favorites" to favorites.toList()))
    private fun persist() {
        storage.write(exportJson())
        version.value++
    }
}
