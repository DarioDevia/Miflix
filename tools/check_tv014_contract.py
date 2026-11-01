"""0.1.13 regressions plus narrow 0.1.14 navigation/discovery extensions; no checks disabled."""
import subprocess
import unittest
from pathlib import Path
import check_tv013_contract as previous
ROOT=Path(__file__).resolve().parents[1]
BASE='152701cf8f57eb72e73b57e99825f63b82d76288'
PREFIX=previous.PREFIX
def old(path): return subprocess.check_output(['git','show',BASE+':'+path],cwd=ROOT)
class Tv014Contracts(previous.Tv013Contracts):
    def test_gradle_only_changes_tv_version(self):
        path='app/build.gradle.kts'
        expected=old(path).decode().replace('versionCode = 14','versionCode = 15').replace('0.1.13-diagnostico','0.1.14-diagnostico')
        self.assertEqual(expected,(ROOT/path).read_text())
    def test_cards_and_hero_rendering_are_unchanged(self):
        before=old(PREFIX+'TvScreens.kt').decode();after=(ROOT/PREFIX/'TvScreens.kt').read_text()
        marker='internal fun TvHome('
        self.assertEqual(before[:before.index(marker)],after[:after.index(marker)])
        start='            item(key = "hero") {';end='            if (continuing.isNotEmpty()) item(key = "continue")'
        self.assertEqual(before[before.index(start):before.index(end)],after[after.index(start):after.index(end)])
        # Continue row must remain byte exact too.
        self.assertEqual(before[before.index(end):before.index('            listOf(Triple("favorite"')],
            after[after.index(end):after.index('            // A single saga row')])
        for text in ('"Mi Lista"','"Últimos agregados"','"Sagas destacadas"','tvGenreRows(titles)',
            'listOf("favorite", "sagas", "added") + genres.map { it.key }','onCollection(collection)','onSelect(title)'):
            self.assertIn(text,after)
        self.assertNotIn('Últimos estrenos',after)
    def test_all_013_library_playback_intro_and_mobile_sources_are_frozen(self):
        for name in ('PlaybackScreen.kt','TvPersonalLibrary.kt','TvPersonalLibraryUi.kt','TvLibraryScreen.kt',
            'TvWatchCoverage.kt','TvPlaybackCoverageObserver.kt','TvContinueWatching.kt','TvHomeDiscovery.kt',
            'TvSuggestions.kt','TvDetailScreen.kt','TvEpisodeRow.kt','CatalogSearch.kt','TvHomeSuggestions.kt'):
            path=PREFIX+name;self.assertEqual(old(path),(ROOT/path).read_bytes(),name)
        for path in ('app/src/tv/assets/miflix_tv_requests_qr.png','app/src/main/assets/catalogo_fallback.json',
            'app/src/tv/res/xml/backup_rules.xml','app/src/tv/res/xml/data_extraction_rules.xml'):
            self.assertEqual(old(path),(ROOT/path).read_bytes(),path)
        files=subprocess.check_output(['git','ls-tree','-r','--name-only',BASE,'--','app/src/test'],cwd=ROOT,text=True).splitlines()
        for path in files:self.assertEqual(old(path),(ROOT/path).read_bytes(),path)
    def test_new_navigation_uses_existing_details_and_local_catalog_only(self):
        screens=(ROOT/PREFIX/'TvCollectionScreens.kt').read_text()
        self.assertIn('TvCard(title, title.id, pending',screens)
        self.assertIn('onSelect(title)',screens)
        self.assertNotIn('Text(title.titulo',screens)
        session=(ROOT/PREFIX/'TvCollectionsSession.kt').read_text()
        self.assertIn('internal object TvProcessCollections',session)
        self.assertIn('if (!ready) return emptyList()',session)
        for name in ('TvCollectionsSession.kt','TvGenreRows.kt','TvCatalogCollections.kt','TvCollectionScreens.kt'):
            source=(ROOT/PREFIX/name).read_text()
            for forbidden in ('OkHttp','TelegramSession','/admin/','http://miflix','newCall('):self.assertNotIn(forbidden,source)
if __name__=='__main__':unittest.main()
