from tv012_contract_support import without_tv012_progress_wiring
"""Startup/asset source guards only; not Android UI, decoder or focus execution."""
import hashlib
import re
import subprocess
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/ar/com/miflix/client'
BASE='5fed7e317710d8ad71e19212bffa6f88a727b3f4'
def old(path):return subprocess.check_output(['git','show',BASE+':'+path],cwd=ROOT)
class IntroContracts(unittest.TestCase):
    def test_original_video_is_tv_only_and_byte_exact(self):
        p=ROOT/'app/src/tv/assets/miflix_tv_intro.mp4'
        self.assertEqual(2134895,p.stat().st_size)
        self.assertEqual('ecede13d1d994ba31de549b7d3ca557e64d69c4f3518bbae07caae5841423b17',hashlib.sha256(p.read_bytes()).hexdigest())
        self.assertFalse((ROOT/'app/src/main/assets/miflix_tv_intro.mp4').exists())
        self.assertFalse((ROOT/'app/src/mobile/assets/miflix_tv_intro.mp4').exists())
    def test_hero_engine_and_all_playback_sources_remain_exact(self):
        for name in ('TvDynamicHero.kt','TvFeaturedSession.kt','TvScreens.kt','PlaybackScreen.kt',
            'TvTrackPanel.kt','MediaTrackPolicy.kt','TvTrackPolicy.kt','TelegramVideo.kt','TelegramSession.kt',
            'TvTrailer.kt','TvIntegratedTrailer.kt','TvTrailerPolicy.kt','FeaturedSession.kt','Catalog.kt'):
            path='app/src/main/java/ar/com/miflix/client/'+name
            actual=(ROOT/path).read_bytes()
            if name in ('PlaybackScreen.kt','TelegramVideo.kt'):
                actual=re.sub(rb'(?m)^[ \t]*@androidx\.annotation\.OptIn\(androidx\.media3\.common\.util\.UnstableApi::class\)\n',b'',actual)
            self.assertEqual(old(path),actual,name)
    def test_actual_application_and_auth_body_is_unchanged(self):
        path='app/src/main/java/ar/com/miflix/client/MainActivity.kt'
        before=old(path).decode();after=without_tv012_progress_wiring((ROOT/path).read_text());marker='private fun ClientApp('
        self.assertEqual(before[before.index(marker):],after[after.index(marker):])
        self.assertIn('BuildConfig.IS_TV && TvIntroProcess.session.claim(savedInstanceState != null)',after)
        intro=(SRC/'TvCinematicIntro.kt').read_text()
        self.assertIn('if (!showInitially) { content(); return }',intro)
        self.assertIn('enteredApp -> content()',intro)
        self.assertIn('if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) enteredApp = true',intro)
    def test_fit_texture_and_no_controls(self):
        xml=ET.parse(ROOT/'app/src/tv/res/layout/miflix_tv_intro_player.xml').getroot()
        app='{http://schemas.android.com/apk/res-auto}'
        for key,value in [('use_controller','false'),('resize_mode','fit'),('surface_type','texture_view'),('show_buffering','never')]:
            self.assertEqual(value,xml.attrib[app+key])
    def test_intro_lifecycle_timeout_and_skip_do_not_touch_main_player(self):
        text=(SRC/'TvCinematicIntro.kt').read_text()
        self.assertEqual(1,text.count('ExoPlayer.Builder('))
        for forbidden in ('PlaybackScreen(', 'TelegramSession.', 'TelegramRangeDataSource(', 'requestPlayback('):
            self.assertNotIn(forbidden,text)
        for required in ('Player.REPEAT_MODE_OFF','Player.STATE_ENDED','onPlayerError',
            'delay(TV_INTRO_TIMEOUT_MS)','Lifecycle.Event.ON_PAUSE','removeObserver(observer)',
            'playback.dispose()', 'KeyEvent.ACTION_UP', 'it.release()', 'view?.player = null'):
            self.assertIn(required,text)
    def test_arm_and_manifests_have_only_tv_starting_theme_change(self):
        for path in ('tools/tdlib-0.1.1-provenance.json','app/src/main/AndroidManifest.xml'):
            self.assertEqual(old(path),(ROOT/path).read_bytes())
        path='app/src/tv/AndroidManifest.xml'
        expected=old(path).decode().replace('android:name=".MainActivity"','android:name=".MainActivity" android:exported="true" android:theme="@style/Theme.MiFlix.TV"')
        self.assertEqual(expected,(ROOT/path).read_text())
if __name__=='__main__':unittest.main()
