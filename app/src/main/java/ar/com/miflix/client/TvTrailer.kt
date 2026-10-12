package ar.com.miflix.client

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Safe TV alternative: an explicit action delegates an existing public trailer to YouTube. */
@Composable
internal fun TvTrailerAction(title: Title) {
    val target = remember(title.trailerUrl) { TrailerLink.parse(title.trailerUrl) }
    val context = LocalContext.current
    var unavailable by remember(title.id) { mutableStateOf(false) }
    if (target != null) {
        TvDetailAction("Ver tráiler en YouTube") {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://www.youtube.com/watch?v=${target.videoId}&t=${target.startSeconds}s")))
            } catch (_: ActivityNotFoundException) { unavailable = true }
        }
        if (unavailable) Text("No hay una aplicación disponible para abrir el tráiler en este dispositivo.", color = Color.White)
    }
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
