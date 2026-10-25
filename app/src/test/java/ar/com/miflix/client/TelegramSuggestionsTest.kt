package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TelegramSuggestionsTest {
    @Test fun unconfiguredDestinationIsDisabled() {
        listOf(null, "", " ", "@").forEach { assertNull(telegramSuggestionsUrl(it)) }
    }
    @Test fun publicUsernameProducesOnlyAnHttpsProfileLink() {
        // Deliberate test fixture, never used as an application default/destination.
        assertEquals("https://t.me/Example_User", telegramSuggestionsUrl("Example_User"))
        assertEquals("https://t.me/Example_User", telegramSuggestionsUrl(" @Example_User "))
    }
    @Test fun noPhoneNumbersPathsQueriesInvitationsOrArbitraryUrlsAreAccepted() {
        listOf("+542611234567", "542611234567", "https://t.me/Example_User", "Example_User/path",
            "Example_User?start=token", "Example_User#fragment", "joinchat/abc", "@@Example_User",
            "<script>", "Example_User\nOther_User").forEach { assertNull(telegramSuggestionsUrl(it)) }
    }
    @Test fun usernameLengthAndCharactersAreBounded() {
        assertNotNull(telegramSuggestionsUrl("A1234"))
        assertNotNull(telegramSuggestionsUrl("A".repeat(32)))
        listOf("A123", "A".repeat(33), "1abcd", "_abcd", "ábcde", "A-bcd", "A bcd", "A.bcd")
            .forEach { assertNull(telegramSuggestionsUrl(it)) }
    }
}
