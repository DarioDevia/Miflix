"""Read-only release guards; not a substitute for Android compilation or visual tests."""
from pathlib import Path
import subprocess
import unittest
ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/ar/com/miflix/client'
BASE = '94790b811d0eb88d85457ecedfddf50ce755c148'

class Tv017Contract(unittest.TestCase):
    def test_single_control_row_has_requested_order_and_central_weight(self):
        controls = (SRC / 'TvPlaybackControls.kt').read_text().split('if (visible) Row(', 1)[1]
        self.assertNotIn('Column(', controls)
        labels = ['PlaybackIcon(if (playing)', 'Text(formatProgressTime(position)',
                  'LinearProgressIndicator(', 'Text(formatProgressTime(duration',
                  'PlaybackIcon("Audio y subtítulos"']
        offsets = [controls.index(label) for label in labels]
        self.assertEqual(offsets, sorted(offsets))
        self.assertIn('Modifier.weight(1f).height(12.dp)', controls)

    def test_poster_retains_border_and_click_without_default_indication(self):
        card = (SRC / 'TvScreens.kt').read_text().split('internal fun TvCard', 1)[1].split('@Composable', 1)[0]
        self.assertIn('indication = null', card)
        self.assertIn('.border(3.dp, if (focused) Color.White else Color.Transparent)', card)
        self.assertIn('role = Role.Button, onClick = onClick', card)
        self.assertNotIn('ColorFilter', card)
        self.assertNotIn('.alpha(', card)

    def test_failed_webview_is_removed_and_retry_has_fresh_state(self):
        trailer = (SRC / 'TvIntegratedTrailer.kt').read_text()
        self.assertIn('if (problem == null) AndroidView', trailer)
        self.assertIn('key(target, attempt)', trailer)
        self.assertIn('onRetry = { attempt++ }', trailer)
        self.assertIn('view?.destroy()', trailer)

    def test_native_watchdog_precedes_play_request(self):
        trailer = (SRC / 'TvIntegratedTrailer.kt').read_text()
        request = trailer.split('fun requestPlayback()', 1)[1].split('LaunchedEffect(ready)', 1)[0]
        self.assertLess(request.index('playRequest++'), request.index('evaluateJavascript("tvPlay()"'))
        self.assertIn('requestPlayback()', trailer.split('LaunchedEffect(ready)', 1)[1].split('LaunchedEffect(playRequest)', 1)[0])

    def test_external_failures_and_tls_are_handled(self):
        launcher = (SRC / 'TvTrailer.kt').read_text()
        self.assertIn('ActivityNotFoundException', launcher)
        self.assertIn('SecurityException', launcher)
        trailer = (SRC / 'TvIntegratedTrailer.kt').read_text()
        self.assertIn('handler.cancel()', trailer)
        self.assertNotIn('handler.proceed()', trailer)
        self.assertIn('settings.allowFileAccess = false', trailer)

    def test_audio_mobile_streaming_and_catalog_sources_are_identical(self):
        protected = ['PlaybackScreen.kt', 'TelegramVideo.kt', 'TelegramSession.kt', 'Catalog.kt',
                     'MainActivity.kt', 'MediaTrackPolicy.kt', 'TvTrackPanel.kt', 'TvAudioDiagnostics.kt',
                     'TrailerPreview.kt', 'TrailerLink.kt']
        diff = subprocess.check_output(['git', 'diff', BASE, '--'] + [str(SRC / p) for p in protected], cwd=ROOT)
        self.assertEqual(diff, b'')

    def test_only_tv_manifest_enables_hardware_acceleration(self):
        manifest = (ROOT / 'app/src/tv/AndroidManifest.xml').read_text()
        self.assertIn('android:hardwareAccelerated="true"', manifest)
        self.assertIn('versionName = "0.1.7-diagnostico"', (ROOT / 'app/build.gradle.kts').read_text())
        self.assertIn('versionCode = 8', (ROOT / 'app/build.gradle.kts').read_text())

if __name__ == '__main__':
    unittest.main()
