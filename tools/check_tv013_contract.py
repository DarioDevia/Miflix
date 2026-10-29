"""TV 0.1.13 scope contracts; historical whole-screen snapshots are superseded by authorized UI changes."""
import hashlib
import subprocess
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE='57fca311dc06b5ad9fdcf5a02ac9daa9c08bd0f9'
PREFIX='app/src/main/java/ar/com/miflix/client/'
def old(path):return subprocess.check_output(['git','show',BASE+':'+path],cwd=ROOT)
class Tv013Contracts(unittest.TestCase):
    def test_protected_runtime_sources_byte_exact(self):
        for name in ('TelegramVideo.kt','TelegramSession.kt','TvProgressStorage.kt','PlaybackProgress.kt',
            'ContinueWatching.kt','TvSeasonPresentation.kt','TvDynamicHero.kt','TvFeaturedSession.kt',
            'TvCinematicIntro.kt','TvIntroPolicy.kt','TvTrackPanel.kt','MediaTrackPolicy.kt','TvTrackPolicy.kt',
            'TvPlaybackControls.kt','TvTrailer.kt','TvIntegratedTrailer.kt','TvTrailerPolicy.kt',
            'EpisodeNavigation.kt','TvBrowse.kt','TvInput.kt','TvFocusTarget.kt'):
            path=PREFIX+name
            self.assertEqual(old(path),(ROOT/path).read_bytes(),name)
        for path in ('app/src/main/AndroidManifest.xml','app/src/tv/AndroidManifest.xml',
            'app/src/tv/assets/miflix_tv_intro.mp4','tools/tdlib-0.1.1-provenance.json'):
            self.assertEqual(old(path),(ROOT/path).read_bytes(),path)
    def test_only_read_only_coverage_hook_is_added_to_player(self):
        path=PREFIX+'PlaybackScreen.kt';text=(ROOT/path).read_text()
        text=text.replace(', libraryProgressKey = if (BuildConfig.IS_TV) progressKey else null','')
        text=text.replace('    tvArtwork: Title?,\n    libraryProgressKey: String? = null','    tvArtwork: Title?')
        text=text.replace('    if (libraryProgressKey != null) TvObserveLibraryCoverage(player, libraryProgressKey)\n','')
        self.assertEqual(old(path).decode(),text)
        observer=(ROOT/PREFIX/'TvPlaybackCoverageObserver.kt').read_text()
        for forbidden in ('seekTo(','.prepare(','.release(','.playWhenReady =','.volume =','setMediaSource(','.trackSelectionParameters ='):
            self.assertNotIn(forbidden,observer)
    def test_gradle_only_changes_tv_version(self):
        path='app/build.gradle.kts'
        expected=old(path).decode().replace('versionCode = 13','versionCode = 14').replace('0.1.12-diagnostico','0.1.13-diagnostico')
        self.assertEqual(expected,(ROOT/path).read_text())
    def test_cards_and_hero_rendering_are_unchanged(self):
        before=old(PREFIX+'TvScreens.kt').decode();after=(ROOT/PREFIX/'TvScreens.kt').read_text()
        marker='internal fun TvHome('
        self.assertEqual(before[:before.index(marker)],after[:after.index(marker)])
        start='            item(key = "hero") {';end='            if (continuing.isNotEmpty()) item(key = "continue")'
        self.assertEqual(before[before.index(start):before.index(end)],after[after.index(start):after.index(end)])
        self.assertIn('Triple("favorite", "Mi Lista", favorites), Triple("added", "Últimos agregados", latestAdded)',after)
        self.assertNotIn('Últimos estrenos',after)
    def test_library_is_tv_only_and_qr_has_no_external_service(self):
        host=(ROOT/PREFIX/'TvPersonalLibraryUi.kt').read_text()
        self.assertIn('if (!BuildConfig.IS_TV) { content(); return }',host)
        self.assertIn('"miflix_tv_library"',host)
        self.assertIn('removeObserver(observer)',host)
        dialog=(ROOT/PREFIX/'TvSuggestions.kt').read_text()
        for forbidden in ('TELEGRAM_API','ACTION_VIEW','WebView','TelegramSession','http://','qrserver'):
            self.assertNotIn(forbidden,dialog)
        self.assertIn('https://t.me/Dario_Devia',dialog)
        self.assertIn('onDismissRequest = onClose',dialog)
        self.assertIn('closeFocus.requestFocus()',dialog)
        episode=(ROOT/PREFIX/'TvEpisodeRow.kt').read_text()
        self.assertIn('keyCode == 22',episode); self.assertIn('markFocus.requestFocus()',episode)
        self.assertIn('keyCode == 21',episode); self.assertIn('playFocus.requestFocus()',episode)
        library=(ROOT/PREFIX/'TvLibraryScreen.kt').read_text()
        for marker in ('"✓ Favoritos"','"✓ Vistos"','tabFocus.requestFocus()','scroll.scrollToItem(index)','onSelect(title)'):
            self.assertIn(marker,library)
    def test_all_existing_policy_tests_are_preserved(self):
        files=subprocess.check_output(['git','ls-tree','-r','--name-only',BASE,'--','app/src/test'],cwd=ROOT,text=True).splitlines()
        for path in files: self.assertEqual(old(path),(ROOT/path).read_bytes(),path)
if __name__=='__main__':unittest.main()
