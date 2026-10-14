"""Read-only source contract checks. They do not replace Android compilation or UI tests."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/ar/com/miflix/client'

class Tv015Contract(unittest.TestCase):
    def test_panel_never_owns_or_changes_player_lifecycle(self):
        panel = (SRC / 'TvTrackPanel.kt').read_text()
        for operation in ('ExoPlayer', 'player.pause(', 'player.play(', 'seekTo(', 'release(', 'TelegramSession'):
            self.assertNotIn(operation, panel)
        self.assertIn('BackHandler { onClose() }', panel)

    def test_marks_use_renderer_snapshots(self):
        playback = (SRC / 'PlaybackScreen.kt').read_text()
        self.assertIn('selected = !availableTracks.isTypeSelected(C.TRACK_TYPE_TEXT)', playback)
        self.assertGreaterEqual(playback.count('selected = option.group.isTrackSelected(option.trackIndex)'), 2)
        self.assertIn('availableTracks = tracks', playback)
        self.assertIn('setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))', playback)

    def test_mobile_keeps_original_sheet_and_builder(self):
        playback = (SRC / 'PlaybackScreen.kt').read_text()
        self.assertIn('if (showTracksPanel && !released && !BuildConfig.IS_TV)', playback)
        self.assertIn('else ExoPlayer.Builder(context)', playback)
        self.assertIn('player.release()', playback)
        self.assertLess(playback.index('player.release()'), playback.index('video.clear()'))

    def test_panel_is_shared_and_has_no_category_branches(self):
        playback = (SRC / 'PlaybackScreen.kt').read_text()
        self.assertEqual(playback.count('TvTrackPanel(rows,'), 1)
        panel = (SRC / 'TvTrackPanel.kt').read_text()
        for category in ('pelicula', 'serie', 'anime'):
            self.assertNotIn('"' + category + '"', panel)

    def test_normal_tv_screens_have_no_version_labels(self):
        for name in ('TvScreens.kt', 'TvDetailScreen.kt', 'TvPlaybackControls.kt', 'TvInput.kt', 'TvIntegratedTrailer.kt'):
            source = (SRC / name).read_text()
            self.assertNotIn('VERSION_NAME', source)
            self.assertNotIn('VERSION_CODE', source)
        settings = (SRC / 'MainActivity.kt').read_text()
        self.assertEqual(settings.count('VERSION_NAME'), 1)
        self.assertIn('if (showAbout)', settings)
        self.assertIn('Acerca de MiFlix', settings)

    def test_tv_version_and_mobile_version_are_independent(self):
        gradle = (ROOT / 'app/build.gradle.kts').read_text()
        self.assertRegex(gradle, r'versionName = "0\.1\.\d+-diagnostico"')
        self.assertRegex(gradle.split('create("tv")', 1)[1], r'versionCode = \d+')
        self.assertIn('versionName = "0.9.2-diagnostico"', gradle)
        self.assertIn('versionCode = 26', gradle)

if __name__ == '__main__':
    unittest.main()
