package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

/** Policy/reducer tests are not an Android Intent or DPAD instrumentation claim. */
class TvHomeEntryTest {
    private fun title(id: String, type: String = "pelicula") = Title(id=id, tipo=type, titulo="Same name")
    @Test fun validCatalogIdAcceptedWithoutDisplayNameOrUrl() {
        assertEquals("tmdb:123", homeEntryId(HOME_DETAIL_ACTION,"tmdb:123"))
    }
    @Test fun noIdOrNormalLauncherHasNoNavigationRequest() {
        assertNull(homeEntryId(HOME_DETAIL_ACTION,null));assertNull(homeEntryId("android.intent.action.MAIN","a"))
        assertEquals(HomeEntryStatus.NONE,homeEntryResolve(null,emptyList(),false,false).status)
    }
    @Test fun malformedRequestsCannotSupplyUrlsPathsPlaybackOrHugeValues() {
        for(value in listOf<Any>("", " ", "../file", "https://t.me/1", "a\n", "x".repeat(257), 42, listOf("a")))
            assertNull(homeEntryId(HOME_DETAIL_ACTION,value))
        assertNull(homeEntryId(HOME_DETAIL_ACTION,"valid",true))
    }
    @Test fun coldStartRetainsSelectionUntilCatalogLoaded() {
        val inbox=HomeEntryInbox().submit(HOME_DETAIL_ACTION,"a")
        assertEquals(HomeEntryStatus.WAIT_CATALOG,homeEntryResolve(inbox.id,emptyList(),false,true).status)
        val resolved=homeEntryResolve(inbox.id,listOf(title("a")),true,true)
        assertEquals(HomeEntryStatus.DETAIL,resolved.status);assertEquals("a",resolved.title?.id)
    }
    @Test fun authorizationMustCompleteBeforeOpeningTheNormalDetail() {
        assertEquals(HomeEntryStatus.WAIT_AUTH,homeEntryResolve("a",listOf(title("a")),true,false).status)
    }
    @Test fun nonexistentIdNeverMatchesAnotherTitleWithSameDisplayName() {
        val result=homeEntryResolve("missing",listOf(title("other")),true,true)
        assertEquals(HomeEntryStatus.MISSING,result.status);assertNull(result.title)
    }
    @Test fun duplicateIdentityIsAmbiguousAndSafelyRejected() {
        assertEquals(HomeEntryStatus.MISSING,homeEntryResolve("a",listOf(title("a"),title("a")),true,true).status)
    }
    @Test fun warmStartAndConsecutiveCardsReplacePendingRequest() {
        val first=HomeEntryInbox().submit(HOME_DETAIL_ACTION,"a")
        val second=first.submit(HOME_DETAIL_ACTION,"b")
        assertEquals("b",second.id);assertTrue(second.serial>first.serial)
        assertEquals(second,second.consume(first.serial))
        assertEquals("b",homeEntryResolve(second.id,listOf(title("a"),title("b")),true,true).title?.id)
    }
    @Test fun recompositionAndBackCannotReplayConsumedRequest() {
        val first=HomeEntryInbox().submit(HOME_DETAIL_ACTION,"a")
        val consumed=first.consume(first.serial)
        assertNull(consumed.id);assertEquals(consumed,consumed.consume(first.serial))
        assertEquals(HomeEntryStatus.NONE,homeEntryResolve(consumed.id,listOf(title("a")),true,true).status)
    }
    @Test fun pendingRequestRestoresAcrossActivityRecreationButConsumedRequestDoesNot() {
        val first=HomeEntryInbox().submit(HOME_DETAIL_ACTION,"a")
        val restored=HomeEntryInbox().submit(HOME_DETAIL_ACTION,first.id)
        assertEquals("a",restored.id)
        assertNull(HomeEntryInbox().submit(HOME_DETAIL_ACTION,first.consume(first.serial).id).id)
    }
    @Test fun invalidNewRequestDoesNotOverwriteValidPendingSelection() {
        val first=HomeEntryInbox().submit(HOME_DETAIL_ACTION,"a")
        assertEquals(first,first.submit(HOME_DETAIL_ACTION,"https://bad"))
    }
    @Test fun movieSeriesAndAnimeResolveOnlyToDetailsWithoutPlaybackParameters() {
        for(type in listOf("pelicula","serie","anime")) {
            val result=homeEntryResolve("a",listOf(title("a",type)),true,true)
            assertEquals(HomeEntryStatus.DETAIL,result.status);assertEquals(type,result.title?.tipo)
        }
        assertEquals(15_000L,HOME_DETAIL_WAIT_MS)
    }
}
