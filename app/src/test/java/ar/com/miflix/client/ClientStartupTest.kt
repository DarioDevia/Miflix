package ar.com.miflix.client

import org.junit.Assert.assertEquals
import org.junit.Test

class ClientStartupTest {
    @Test fun freshInstallAndEmptyPreferencesUseDefaultCatalog() {
        for (saved in listOf(null, "", "   ")) {
            assertEquals(CatalogRepository.DEFAULT_URL, startupCatalogUrl(saved))
        }
    }

    @Test fun advancedCatalogOverrideSurvivesStartup() {
        val custom = "https://example.org/catalogo.json"
        assertEquals(custom, startupCatalogUrl("  $custom  "))
    }

    @Test fun authorizedAccountStartsAtHome() {
        assertEquals(Screen.HOME, startupScreen(TelegramSession.State.Ready))
    }

    @Test fun pendingAuthorizationAlwaysStartsAtConnectionWithoutTechnicalSettings() {
        val states = listOf(TelegramSession.State.NeedsApi, TelegramSession.State.Starting,
            TelegramSession.State.Phone, TelegramSession.State.Code, TelegramSession.State.Password,
            TelegramSession.State.Email, TelegramSession.State.EmailCode,
            TelegramSession.State.OtherDevice("https://example.org/confirmation"),
            TelegramSession.State.Failed("Sin conexión"))
        for (state in states) assertEquals(Screen.CONNECT, startupScreen(state))
    }
}
