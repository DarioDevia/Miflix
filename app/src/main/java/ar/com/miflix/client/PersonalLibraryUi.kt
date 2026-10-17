package ar.com.miflix.client

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

internal class AndroidLibraryStorage(context: Context) : LibraryStorage {
    private val prefs = context.applicationContext.getSharedPreferences("miflix_library", Context.MODE_PRIVATE)
    override fun read(): String? = prefs.getString("library_v1", null)
    override fun write(json: String) { prefs.edit().putString("library_v1", json).apply() }
}
internal val LocalPersonalLibrary = staticCompositionLocalOf<PersonalLibraryStore?> { null }
internal val LocalLibraryRevision = staticCompositionLocalOf { 0L }
