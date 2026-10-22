"""Scope and integration checks, not UI/DPAD or Android compilation."""
import subprocess
import unittest
from pathlib import Path
from tv012_contract_support import without_tv012_progress_wiring
ROOT = Path(__file__).resolve().parents[1]
BASE = '68953b2c1d02bf8796a8d36364d885c28d9420b9'
PREFIX = 'app/src/main/java/ar/com/miflix/client/'
def old(path): return subprocess.check_output(['git','show',BASE+':'+path],cwd=ROOT)
class Tv012Contracts(unittest.TestCase):
    def test_main_only_adds_tv_progress_adapter_and_observer(self):
        path=PREFIX+'MainActivity.kt'
        self.assertEqual(old(path).decode(),without_tv012_progress_wiring((ROOT/path).read_text()))
    def test_player_hero_intro_auth_tracks_and_mobile_sources_are_byte_identical(self):
        files = subprocess.check_output(['git','ls-tree','-r','--name-only',BASE,'--','app/src'],cwd=ROOT,text=True).splitlines()
        for path in files:
            if path in {PREFIX+'MainActivity.kt',PREFIX+'TvDetailScreen.kt'}: continue
            self.assertEqual(old(path),(ROOT/path).read_bytes(),path)
    def test_gradle_only_changes_tv_version(self):
        path='app/build.gradle.kts'
        expected=old(path).decode().replace('versionCode = 12','versionCode = 13').replace('0.1.11-diagnostico','0.1.12-diagnostico')
        self.assertEqual(expected,(ROOT/path).read_text())
    def test_detail_keeps_catalog_indices_for_focus_and_playback(self):
        text=(ROOT/PREFIX/'TvDetailScreen.kt').read_text()
        for marker in ('val index = seasonOrder[displayIndex]', 'seasonFocus[index]',
            'onSeasonSelected(index)', 'seasons.getOrNull(seasonIndex)',
            'findPreviousPlayableEpisode(title, seasonIndex, index, validLink)',
            'findNextPlayableEpisode(title, seasonIndex, index, validLink)'):
            self.assertIn(marker,text)
        for marker in ('onPreviewKeyEvent { event ->','focusAt(1, seasonFocus[seasonIndex])'):
            self.assertEqual(old(PREFIX+'TvDetailScreen.kt').decode().count(marker),text.count(marker))
    def test_persistence_schema_rules_and_continue_projection_unchanged(self):
        for name in ('PlaybackProgress.kt','ContinueWatching.kt','EpisodeNavigation.kt'):
            self.assertEqual(old(PREFIX+name),(ROOT/PREFIX/name).read_bytes())
        storage=(ROOT/PREFIX/'TvProgressStorage.kt').read_text()
        self.assertNotIn('SharedPreferences',storage)
        self.assertNotIn('isComplete',storage)
if __name__=='__main__': unittest.main()
