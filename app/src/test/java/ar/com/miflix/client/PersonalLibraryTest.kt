package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class PersonalLibraryTest {
    private class Storage(var json: String? = null) : LibraryStorage {
        override fun read() = json
        override fun write(json: String) { this.json = json }
    }
    @Test fun newInstallStartsEmpty() {
        val store = PersonalLibraryStore(Storage())
        assertFalse(store.isFavorite("a"))
        assertTrue(store.favorites(emptyList()).isEmpty())
    }
    @Test fun favoriteSurvivesReopenAndCanBeRemoved() {
        val disk = Storage(); val store = PersonalLibraryStore(disk)
        store.setFavorite("a", true)
        assertTrue(PersonalLibraryStore(disk).isFavorite("a"))
        store.setFavorite("a", false)
        assertFalse(PersonalLibraryStore(disk).isFavorite("a"))
    }
    @Test fun duplicateMutationsAndDuplicateCatalogEntriesDoNotDuplicateFavorites() {
        val store = PersonalLibraryStore(Storage())
        repeat(3) { store.setFavorite("a", true) }
        assertEquals(listOf("a"), store.favorites(listOf(Title(id="a"), Title(id="a"))).map { it.id })
    }
    @Test fun missingCatalogTitleDoesNotEraseFavorite() {
        val disk=Storage(); val store=PersonalLibraryStore(disk)
        store.setFavorite("missing", true)
        assertTrue(store.favorites(emptyList()).isEmpty())
        assertTrue(PersonalLibraryStore(disk).isFavorite("missing"))
    }
    @Test fun blankAndBrokenStorageDoNotBreakStartup() {
        val store=PersonalLibraryStore(Storage("broken"))
        store.setFavorite("",true)
        assertFalse(store.isFavorite(""))
    }
    @Test fun exportsVersionedIdentifiersWithoutCatalogPayload() {
        val store=PersonalLibraryStore(Storage()); store.setFavorite("stable-id",true)
        assertTrue(store.exportJson().contains("\"schemaVersion\":1"))
        assertTrue(store.exportJson().contains("stable-id"))
    }
}
