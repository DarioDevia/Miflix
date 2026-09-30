package ar.com.miflix.client

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackIssueTest {
    private fun telegram(code: Int, message: String, operation: String = "GetMessageLinkInfo") =
        TelegramRequestException(code, message, operation)

    @Test fun explicitReadAccessErrorsAreRecognized() {
        for (code in listOf(400, 403, 406)) {
            for (message in listOf("CHANNEL_PRIVATE", "Have no access to the chat")) {
                assertEquals(PlaybackIssue.ACCESS_DENIED,
                    classifyPlaybackIssue(telegram(code, message)))
            }
        }
    }

    @Test fun rangeErrorInsideAnIoWrapperKeepsItsTelegramMeaning() {
        val error = IOException("Error del origen", telegram(406, "CHANNEL_PRIVATE", "DownloadFile"))
        assertEquals(PlaybackIssue.ACCESS_DENIED, classifyPlaybackIssue(error))
    }

    @Test fun ExplicitMissingMessageErrorsMeanUnavailable() {
        for (message in listOf("Message not found", "MSG_ID_INVALID", "MESSAGE_ID_INVALID")) {
            assertEquals(PlaybackIssue.UNAVAILABLE, classifyPlaybackIssue(telegram(400, message)))
        }
    }

    @Test fun AmbiguousErrorsAndStatusCodesNeverProveNoAccess() {
        for (error in listOf(telegram(400, "Chat not found"), telegram(403, "UNKNOWN"),
                telegram(500, "CHANNEL_PRIVATE"), IOException("Permission denied"),
                IllegalStateException("CHANNEL_PRIVATE"), SocketTimeoutException("Timeout"),
                telegram(400, "CHANNEL_PRIVATE", "CheckAuthenticationPassword"))) {
            assertEquals(PlaybackIssue.UNKNOWN, classifyPlaybackIssue(error))
        }
    }

    @Test fun specificNetworkFailuresAreRecognizedThroughCauses() {
        for (error in listOf(UnknownHostException(), ConnectException(), NoRouteToHostException())) {
            assertEquals(PlaybackIssue.CONNECTION, classifyPlaybackIssue(IOException("Origen", error)))
        }
    }

    @Test fun familyMessagesNeverIncludeUnknownTechnicalDetails() {
        val error = telegram(400, "INTERNAL_FAILURE at miflix://telegram/example")
        val issue = classifyPlaybackIssue(error)
        assertEquals("No pudimos reproducir este contenido", issue.title)
        assertEquals("Intentá nuevamente en unos momentos.", issue.message)
        assertEquals("Tu cuenta de Telegram no tiene acceso a este contenido.\n" +
            "Hablá con el administrador de MiFlix para solicitar acceso.",
            PlaybackIssue.ACCESS_DENIED.message)
    }
}
