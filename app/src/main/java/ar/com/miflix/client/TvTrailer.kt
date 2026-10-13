package ar.com.miflix.client

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/** No external app is launched without an explicit user action. */
@Composable
internal fun TvTrailerAction(title: Title) {
    val target = remember(title.trailerUrl) { TrailerLink.parse(title.trailerUrl) }
    val context = LocalContext.current
    var unavailable by remember(title.id) { mutableStateOf(false) }
    var integrated by remember(title.id) { mutableStateOf(false) }
    var restore by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val external = remember(title.trailerUrl) { tvTrailerExternalUrl(title.trailerUrl) }
    val openExternal: () -> Unit = {
        if (external != null) try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(external)))
        } catch (_: ActivityNotFoundException) { unavailable = true }
    }
    LaunchedEffect(integrated) {
        if (!integrated && restore) { withFrameNanos { }; focus.requestFocus(); restore = false }
    }
    if (target != null) {
        TvDetailAction("Ver tráiler", Modifier.focusRequester(focus)) { integrated = true }
        if (integrated) TvIntegratedTrailer(target, title.titulo,
            onExternal = openExternal, externalUnavailable = unavailable,
            onClose = { integrated = false; restore = true })
    } else if (external != null) {
        Text("Esta fuente no tiene integración disponible en MiFlix TV.", color = Color.LightGray)
        TvDetailAction("Abrir fuente del tráiler", onClick = openExternal)
    }
    if (unavailable && !integrated) Text("No hay una aplicación disponible para abrir el tráiler.", color = Color.White)
}

internal fun tvAdjacentPlayableIndex(episodes: List<Episode>, index: Int, direction: Int,
    valid: (String) -> Boolean): Int? {
    require(direction == -1 || direction == 1)
    var next = index + direction
    while (next in episodes.indices) {
        if (episodes[next].telegramUrl?.let(valid) == true) return next
        next += direction
    }
    return null
}
