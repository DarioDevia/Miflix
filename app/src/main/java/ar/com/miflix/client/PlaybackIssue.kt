package ar.com.miflix.client

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.Locale

/** Conserva el error de TDLib sin publicar sus detalles en la interfaz familiar. */
internal class TelegramRequestException(
    val code: Int,
    val detail: String,
    val operation: String
) : IllegalStateException(detail)

internal enum class PlaybackIssue(val title: String, val message: String) {
    ACCESS_DENIED("Contenido no disponible",
        "Tu cuenta de Telegram no tiene acceso a este contenido.\nHablá con el administrador de MiFlix para solicitar acceso."),
    UNAVAILABLE("Contenido no disponible", "Este contenido ya no está disponible."),
    CONNECTION("No se pudo conectar", "Revisá tu conexión a Internet e intentá nuevamente."),
    UNKNOWN("No pudimos reproducir este contenido", "Intentá nuevamente en unos momentos.")
}

internal fun classifyPlaybackIssue(error: Throwable): PlaybackIssue {
    // Un límite evita recorrer cadenas de causas cíclicas o patológicas.
    val causes = generateSequence(error) { it.cause }.take(12).toList()
    for (cause in causes.filterIsInstance<TelegramRequestException>()) {
        if (cause.operation !in setOf("GetMessageLinkInfo", "DownloadFile")) continue
        val message = cause.detail.trim().uppercase(Locale.ROOT)
        if (cause.code in setOf(400, 403, 406) &&
            message in setOf("CHANNEL_PRIVATE", "HAVE NO ACCESS TO THE CHAT")) {
            return PlaybackIssue.ACCESS_DENIED
        }
        if (cause.code == 400 && message in setOf(
                "MESSAGE NOT FOUND", "MSG_ID_INVALID", "MESSAGE_ID_INVALID")) {
            return PlaybackIssue.UNAVAILABLE
        }
    }
    if (causes.any { it is UnknownHostException || it is ConnectException ||
            it is NoRouteToHostException }) return PlaybackIssue.CONNECTION
    // Chat not found, mensaje nulo, timeouts y IOException genérica son ambiguos.
    return PlaybackIssue.UNKNOWN
}
