package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvDetailArtworkTest {
    private val background = "https://image.tmdb.org/t/p/original/bg.jpg"
    private val legacy = "https://image.tmdb.org/t/p/original/old.jpg"
    private val logo = "https://image.tmdb.org/t/p/original/logo.png"
    private fun parse(fields: String, schema: String = "1.2.0", type: String = "pelicula"): Title =
        CatalogRepository.parseCatalog("""{"schema_version":"$schema","items":[{"id":"a","tipo":"$type","titulo":"Título"$fields}]}""").items.single()

    @Test fun movieWithBackgroundAndLogoKeepsSeparateImageRoles() {
        val art = tvDetailArtwork(parse(""", "backdrop_url":"$background", "logo_url":"$logo""""))
        assertEquals(listOf("https://image.tmdb.org/t/p/w1280/bg.jpg"), art.backgrounds)
        assertEquals("https://image.tmdb.org/t/p/w500/logo.png", art.logo)
        assertNull(art.poster)
    }
    @Test fun backdropPrecedesLegacyButBothRemainAvailableForLoadFailure() {
        val art = tvDetailArtwork(parse(""", "backdrop_url":"$background", "fondo_url":"$legacy""""))
        assertEquals(listOf("https://image.tmdb.org/t/p/w1280/bg.jpg",
            "https://image.tmdb.org/t/p/w1280/old.jpg"), art.backgrounds)
    }
    @Test fun absentLogoDoesNotUsePosterAsLogo() {
        val art = tvDetailArtwork(parse(""", "backdrop_url":"$background", "poster_url":"https://cdn.test/poster.jpg""""))
        assertNull(art.logo)
        assertEquals("https://cdn.test/poster.jpg", art.poster)
    }
    @Test fun noBackgroundKeepsPortraitPosterAndNeutralFallback() {
        val art = tvDetailArtwork(parse(""", "poster_url":"https://cdn.test/poster.jpg""""))
        assertTrue(art.backgrounds.isEmpty()); assertNotNull(art.poster)
        assertTrue(tvDetailArtwork(parse("")).backgrounds.isEmpty())
    }
    @Test fun invalidPrimaryBackgroundFallsBackToHistoricalUrl() {
        val art = tvDetailArtwork(parse(""", "backdrop_url":"not a URL", "fondo_url":"$legacy""""))
        assertEquals(listOf("https://image.tmdb.org/t/p/w1280/old.jpg"), art.backgrounds)
    }
    @Test fun malformedOptionalImageFieldsDoNotDiscardCatalogOrPlayback() {
        for (schema in listOf("1.2.0", "2")) {
            val title = parse(""", "backdrop_url":{}, "fondo_url":42, "logo_url":[], "poster_url":false, "telegram_url":"https://t.me/c/1/2"""", schema)
            assertNull(title.backdropUrl); assertNull(title.fondoUrl); assertNull(title.logoUrl)
            assertNull(title.posterUrl); assertEquals("https://t.me/c/1/2", title.telegramUrl)
        }
    }
    @Test fun bothSchemasReadLogoAndLegacyBackgroundForAllContentTypes() {
        for (schema in listOf("1.2.0", "2")) for (type in listOf("pelicula", "serie", "anime")) {
            val title = parse(""", "fondo_url":"$legacy", "logo_url":"$logo"""", schema, type)
            assertEquals(legacy, title.fondoUrl); assertEquals(logo, title.logoUrl)
        }
    }
    @Test fun oldCatalogsWithMissingOptionalMetadataRemainReadable() {
        val title = parse("")
        assertNull(title.logoUrl); assertNull(title.fondoUrl)
        assertEquals("", tvDetailFacts(title)); assertEquals("", tvDetailGenres(title))
    }
    @Test fun blanksNullAndUnsafeUrlsNeverBecomeImageRequests() {
        for (raw in listOf(null, "", " ", "null", "http://cdn.test/a.jpg", "file:///tmp/a.png",
            "javascript:alert(1)", "https:///a.jpg", "https://cdn.test", "https://u:p@cdn.test/a.png",
            "https://cdn.test/a b.png")) {
            assertNull("$raw", tvArtworkUrl(raw, TvArtworkKind.LOGO))
        }
    }
    @Test fun sizingChangesOnlyImageRenditionsNotExternalProviderOrImageIdentity() {
        assertEquals("https://image.tmdb.org/t/p/w342/bg.jpg", tvArtworkUrl(background, TvArtworkKind.POSTER))
        assertEquals("https://image.tmdb.org/t/p/w500/logo.png?x=1", tvArtworkUrl(logo+"?x=1", TvArtworkKind.LOGO))
        assertEquals("https://cdn.test/original/logo.png", tvArtworkUrl("https://cdn.test/original/logo.png", TvArtworkKind.LOGO))
        assertEquals("https://image.tmdb.org/custom/logo.png", tvArtworkUrl("https://image.tmdb.org/custom/logo.png", TvArtworkKind.LOGO))
    }
    @Test fun duplicateRenditionsAreRequestedOnceAndQueryEscapingIsPreserved() {
        val art = tvDetailArtwork(Title(backdropUrl = background, fondoUrl = background.replace("original", "w780")))
        assertEquals(1, art.backgrounds.size)
        assertEquals("https://cdn.test/a%20b.png?q=a%20b", tvArtworkUrl("https://cdn.test/a%20b.png?q=a%20b", TvArtworkKind.LOGO))
    }
    @Test fun decodeCapsAreBoundedAndLogoKeepsPngTransparencyFormat() {
        assertEquals(1280, TvArtworkKind.BACKDROP.width); assertEquals(720, TvArtworkKind.BACKDROP.height)
        assertEquals(800, TvArtworkKind.LOGO.width); assertEquals(320, TvArtworkKind.LOGO.height)
        assertTrue(tvArtworkUrl(logo, TvArtworkKind.LOGO)!!.endsWith("logo.png"))
    }
    @Test fun metadataOmitsEmptyValuesWithoutInventingSeparators() {
        assertEquals("2025 · ★ 7.2 / 10", tvDetailFacts(Title(year=2025, puntuacion=7.2)))
        assertEquals("★ 0.0 / 10", tvDetailFacts(Title(puntuacion=0.0)))
        assertEquals("", tvDetailFacts(Title(year=0, puntuacion=Double.NaN)))
        assertEquals("Drama · Acción", tvDetailGenres(Title(generos=listOf(" Drama ", "", "Acción"))))
    }
    @Test fun additionalVisualFieldsKeepEpisodeOrderAndExistingProgressIdentity() {
        val old = parse(""", "telegram_url":"https://t.me/c/1/2", "temporadas":[{"numero":2,"episodios":[{"id":"e2","numero":2,"telegram_url":"https://t.me/c/1/3"},{"id":"e1","numero":1,"telegram_url":"https://t.me/c/1/4"}]}]""", type="serie")
        val enriched = old.copy(fondoUrl=legacy, logoUrl=logo)
        assertEquals(listOf(2,1), enriched.temporadas.single().episodios.map { it.numero })
        val season = old.temporadas.single(); val episode = season.episodios.first()
        assertEquals(ProgressKeys.episode(old,season,episode), ProgressKeys.episode(enriched,season,episode))
        assertEquals(ProgressKeys.title(old), ProgressKeys.title(enriched))
    }
}
