"""Focused source invariants. Does not replace Android compilation or DPAD/UI execution."""
import subprocess
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PREFIX='app/src/main/java/ar/com/miflix/client/'
BASE='2f067c63fcee7c0378d30ad565745fc01d5999e3'
def old(name):return subprocess.check_output(['git','show',BASE+':'+PREFIX+name],cwd=ROOT,text=True)
def now(name):return (ROOT/PREFIX/name).read_text()
class HeroContracts(unittest.TestCase):
    def test_main_activity_only_changes_tv_hero_source(self):
        text=old('MainActivity.kt')
        a=text.index('    val tvFeaturedSession = remember');b=text.index('    val fullscreenActive',a)
        text=text[:a]+'''    val featured = remember(titles) {
        if (BuildConfig.IS_TV) null else featuredSession.select(titles)
    }
'''+text[b:]
        text=text.replace('TvHome(titles, featured, continuing,','TvHome(titles, continuing,')
        self.assertEqual(text,now('MainActivity.kt'))
    def test_cards_and_all_home_rows_unchanged(self):
        before=old('TvScreens.kt');after=now('TvScreens.kt')
        self.assertEqual(before[:before.index('internal fun TvHome')],after[:after.index('internal fun TvHome')])
        marker='            if (continuing.isNotEmpty()) item(key = "continue")'
        self.assertEqual(before[before.index(marker):],after[after.index(marker):])
    def test_rotation_never_requests_focus_or_scroll(self):
        text=now('TvDynamicHero.kt')
        self.assertNotIn('requestFocus',text);self.assertNotIn('scrollTo',text)
        self.assertIn('LaunchedEffect(pool, currentId, rotating)',text)
        self.assertIn('delay(TV_HERO_INTERVAL_MS)',text)
        self.assertIn('initialHeroFocusPending = false',now('TvScreens.kt'))
        self.assertNotIn('LaunchedEffect(featured) {',now('TvScreens.kt'))
    def test_lifecycle_preload_and_actions_do_not_touch_playback(self):
        text=now('TvDynamicHero.kt')
        for forbidden in ('ExoPlayer','TelegramSession','WebView','TvTrailer','player.play','seekTo','startActivity'):
            self.assertNotIn(forbidden,text)
        self.assertIn('if (focused && id == currentId) pendingRejected = pendingRejected + id',text)
        self.assertIn('removeObserver(observer)',text)
        self.assertIn('requests.forEach { it.dispose() }',text)
        for marker in ('onPlayFeatured(featured)','onSelect(featured)','tvFeaturedActionLabel(featured)'):
            self.assertIn(marker,now('TvScreens.kt'))
    def test_only_decorative_background_is_animated(self):
        self.assertIn('Crossfade(targetState = title',now('TvDynamicHero.kt'))
        self.assertNotIn('TvDetailAction(',now('TvDynamicHero.kt'))
        self.assertIn('.onFocusChanged { heroFocused = it.hasFocus }.focusGroup()',now('TvScreens.kt'))
    def test_arm_provenance_and_build_command_unchanged(self):
        for path in ('tools/tdlib-0.1.1-provenance.json','tools/build_tv_physical_debug.ps1',
            'app/src/tv/AndroidManifest.xml','app/src/main/AndroidManifest.xml'):
            before=subprocess.check_output(['git','show',BASE+':'+path],cwd=ROOT,text=True)
            self.assertEqual(before,(ROOT/path).read_text())
if __name__=='__main__':unittest.main()
