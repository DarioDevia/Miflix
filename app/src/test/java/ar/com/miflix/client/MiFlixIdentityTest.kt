package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class MiFlixIdentityTest {
    private val verified = ConfirmedIdentity("test-subject", "Persona de prueba", null, 2000)
    @Test fun startsWithoutAccountAndNeverRestoresFromDisk() {
        assertNull(AccountState().identity)
        assertNull(AccountState().confirmed(verified, 1000).signedOut().identity)
        assertNull(AccountState().identity) // A fresh process has no persisted verified state.
    }
    @Test fun startsOnlyOnePendingOperation() {
        val pending = AccountState().start()
        assertTrue(pending.busy); assertSame(pending, pending.start()); assertNull(pending.identity)
    }
    @Test fun cancellationDoesNotAuthenticate() {
        val state = AccountState().start().failed(IdentityFailure.CANCELLED)
        assertNull(state.identity); assertFalse(state.busy); assertTrue(state.message!!.contains("cancelado"))
    }
    @Test fun failuresDoNotLeaveVerifiedIdentity() {
        for (failure in IdentityFailure.values()) {
            val state = AccountState().confirmed(verified, 1000).failed(failure)
            assertNull(state.identity); assertFalse(state.busy)
        }
    }
    @Test fun confirmationRequiresUnexpiredIdentity() {
        assertEquals(verified, AccountState().confirmed(verified, 1000).identity)
        assertNull(AccountState().confirmed(verified, 2000).identity)
        assertNull(AccountState().confirmed(verified.copy(sub = ""), 1000).identity)
    }
    @Test fun expirationAndSignOutDiscardIdentity() {
        val state = AccountState().confirmed(verified, 1000)
        assertEquals(verified, state.expire(1999).identity)
        assertNull(state.expire(2000).identity)
        assertNull(state.signedOut().identity)
        assertTrue(state.signedOut().message!!.contains("Telegram no cambió"))
    }
    @Test fun absentConfigIsDisabledAndDoesNotRequireGoogleToCompile() {
        assertFalse(IdentityConfig("", "").enabled)
        assertFalse(IdentityConfig("123-test.apps.googleusercontent.com", "").enabled)
    }
    @Test fun configRequiresHttpsOriginAndWebClientFormat() {
        val id = "123-test.apps.googleusercontent.com" // TEST ONLY, not configured in production.
        assertTrue(IdentityConfig(id, "https://identity.example.org").enabled)
        for (url in listOf("http://identity.example.org", "https://u:p@identity.example.org", "https://identity.example.org/path", "https://identity.example.org?token=x", "https://identity.example.org#x"))
            assertFalse(IdentityConfig(id, url).enabled)
        assertFalse(IdentityConfig("client-secret", "https://identity.example.org").enabled)
    }
    private fun response(expiry: Long = 2000) = """{"authenticated":true,"identity":{"provider":"google","sub":"test-subject","name":null},"expiresAt":$expiry,"authorization":{"privateDataAllowed":false}}"""
    @Test fun acceptsOnlyServerConfirmedResponseWithoutOptionalProfile() {
        assertEquals(ConfirmedIdentity("test-subject", null, null, 2000), IdentityApi.parseResponse(response(), 1000))
    }
    @Test fun rejectsExpiredMalformedOrPrivilegedResponses() {
        for (text in listOf(response(999), "{}", "null", response().replace("\"authenticated\":true", "\"authenticated\":false"),
            response().replace("\"privateDataAllowed\":false", "\"privateDataAllowed\":true"), response().replace("google", "untrusted"), "x".repeat(8193))) {
            try { IdentityApi.parseResponse(text, 1000); fail("Must reject") }
            catch (e: IdentityApiFailure) { assertEquals(IdentityFailure.REJECTED, e.reason) }
        }
    }
}
