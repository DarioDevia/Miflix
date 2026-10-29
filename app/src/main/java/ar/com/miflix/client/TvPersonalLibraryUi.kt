package ar.com.miflix.client

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

internal class AndroidTvLibraryStorage(context: Context) : TvLibraryStorage {
    private val prefs = context.applicationContext.getSharedPreferences("miflix_tv_library", Context.MODE_PRIVATE)
    override fun read(): String? = prefs.getString("library_v1", null)
    override fun write(json: String) {
        check(prefs.edit().putString("library_v1", json).commit()) { "Library persistence failed" }
    }
}
internal val LocalTvLibrary = staticCompositionLocalOf<TvPersonalLibraryStore?> { null }
internal val LocalTvLibraryRevision = staticCompositionLocalOf { 0L }

@Composable
internal fun TvPersonalLibraryHost(content: @Composable () -> Unit) {
    if (!BuildConfig.IS_TV) { content(); return }
    val context = LocalContext.current.applicationContext
    val library = remember(context) { TvPersonalLibraryStore(AndroidTvLibraryStorage(context)) }
    val revision by library.revision.collectAsState()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, library) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) library.flush() }
        owner.lifecycle.addObserver(observer)
        onDispose { library.flush(); owner.lifecycle.removeObserver(observer) }
    }
    CompositionLocalProvider(LocalTvLibrary provides library, LocalTvLibraryRevision provides revision) { content() }
}
