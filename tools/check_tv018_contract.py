"""Source invariants against the exact 0.1.7 base; no Android/UI validation."""
from pathlib import Path
import subprocess
import unittest
ROOT = Path(__file__).resolve().parents[1]
BASE = '94daefdaf1bebc05e8001626426fd0f1a4488b8b'
PREFIX = 'app/src/main/java/ar/com/miflix/client/'
def old(path):
    return subprocess.check_output(['git', 'show', f'{BASE}:{path}'], cwd=ROOT, text=True)
def now(path):
    return (ROOT / path).read_text()
class Contracts(unittest.TestCase):
    def test_frozen_files_are_identical(self):
        for name in ('TvTrailer.kt','TvIntegratedTrailer.kt','TvTrailerPolicy.kt','TrailerLink.kt',
            'TvPlaybackControls.kt','TvPlayerControlsPolicy.kt','TvTrackPanel.kt',
            'MediaTrackPolicy.kt','TvAudioDiagnostics.kt','FeaturedSession.kt',
            'Catalog.kt','TelegramVideo.kt','TelegramSession.kt'):
            self.assertEqual(old(PREFIX+name), now(PREFIX+name), name)
        self.assertEqual(old('app/src/tv/AndroidManifest.xml'), now('app/src/tv/AndroidManifest.xml'))
    def test_selection_engine_unchanged(self):
        path=PREFIX+'PlaybackScreen.kt'
        before, after=old(path), now(path)
        self.assertEqual(before[:before.index('            if (BuildConfig.IS_TV && showTracksPanel')],
                         after[:after.index('            if (BuildConfig.IS_TV && showTracksPanel')])
        self.assertEqual(before[before.index('                TvTrackPanel(rows,'):],
                         after[after.index('                TvTrackPanel(rows,'):])
        for marker in ('onSelect = { selectTrack(option) }',
                       'selected = option.group.isTrackSelected(option.trackIndex)',
                       'unavailable = tvTrackUnavailableReason'):
            self.assertEqual(before.count(marker), after.count(marker))
        self.assertNotIn('tvTrackLabel(',after)
    def test_cards_and_continue_watching_unchanged(self):
        path=PREFIX+'TvScreens.kt';before,after=old(path),now(path)
        self.assertEqual(before[before.index('internal fun TvCard'):before.index('internal fun TvHome')], after[after.index('internal fun TvCard'):after.index('internal fun TvHome')])
        start='            if (continuing.isNotEmpty()) item(key = "continue")'
        end='            listOf("pelicula" to "Películas"'
        self.assertEqual(before[before.index(start):before.index(end)],after[after.index(start):after.index(end)])
        self.assertIn('if (type == "anime") titles else suggestions', after)
    def test_mobile_featured_retains_original_session(self):
        self.assertIn('else featuredSession.select(titles)',now(PREFIX+'MainActivity.kt'))
    def test_only_tv_version_changes(self):
        path='app/build.gradle.kts'
        self.assertEqual(old(path).replace('versionCode = 8','versionCode = 9')
            .replace('0.1.7-diagnostico','0.1.8-diagnostico'),now(path))
if __name__ == '__main__': unittest.main()
