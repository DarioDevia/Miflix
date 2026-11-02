package ar.com.miflix.client

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Activity-owned pending request; no process-global navigation or second activity/player. */
internal class ExperimentalHomeEntry {
    private var inbox by mutableStateOf(HomeEntryInbox())
    private val savedKey = "miflix.home.entry.pending.v1"

    fun create(intent: Intent?, saved: Bundle?) {
        if(saved == null) receive(intent)
        else {
            val id = runCatching { homeEntryId(HOME_DETAIL_ACTION, saved.getString(savedKey)) }.getOrNull()
            if(id != null) inbox = inbox.submit(HOME_DETAIL_ACTION, id)
            // Saved absence means consumed: do not replay the Activity's original Intent.
        }
    }
    fun receive(intent: Intent?) {
        if(intent?.action != HOME_DETAIL_ACTION) return
        val id = runCatching {
            homeEntryId(intent.action, intent.getStringExtra(HOME_DETAIL_EXTRA),
                intent.data != null || intent.selector != null || intent.clipData != null)
        }.getOrNull()
        if(id == null) { Log.i("MiFlixHomeEntry", "SOLICITUD_RECHAZADA"); return }
        inbox = inbox.submit(HOME_DETAIL_ACTION, id) // Latest valid selection wins; cancels any previous wait.
        Log.i("MiFlixHomeEntry", "SOLICITUD_RECIBIDA")
    }
    fun save(out: Bundle) { inbox.id?.let { out.putString(savedKey, it) } }

    @Composable
    fun Dispatch(titles: List<Title>, catalogReady: Boolean, authorized: Boolean, onOpen: (Title?) -> Unit) {
        val request = inbox.takeIf { it.id != null }
        val latestTitles by rememberUpdatedState(titles)
        val latestReady by rememberUpdatedState(catalogReady)
        val latestAuthorized by rememberUpdatedState(authorized)
        val latestOpen by rememberUpdatedState(onOpen)
        val context = LocalContext.current
        LaunchedEffect(request) {
            if(request == null) return@LaunchedEffect
            // Authorization remains the existing user-driven flow; never bypass it or time it out.
            snapshotFlow { latestAuthorized }.first { it }
            val loaded = withTimeoutOrNull(HOME_DETAIL_WAIT_MS) {
                snapshotFlow { latestReady }.first { it }
            } != null
            if(inbox.serial != request.serial) return@LaunchedEffect
            if(!loaded) {
                inbox = inbox.consume(request.serial)
                Log.i("MiFlixHomeEntry", "CATALOGO_ESPERA_AGOTADA")
                Toast.makeText(context, "No se pudo cargar el título. Intentá nuevamente desde Inicio.", Toast.LENGTH_LONG).show()
                latestOpen(null)
                return@LaunchedEffect
            }
            val result = homeEntryResolve(request.id, latestTitles, latestReady, latestAuthorized)
            inbox = inbox.consume(request.serial) // Consume before navigation; recomposition/recreation cannot replay it.
            if(result.status == HomeEntryStatus.DETAIL) {
                latestOpen(result.title)
                Log.i("MiFlixHomeEntry", "FICHA_SOLICITADA; reproduccionAutomatica=false; validacionVisual=pendiente")
            } else {
                Toast.makeText(context, "Este título ya no está disponible en el catálogo.", Toast.LENGTH_LONG).show()
                latestOpen(null)
                Log.i("MiFlixHomeEntry", "TITULO_NO_DISPONIBLE")
            }
        }
    }
}
