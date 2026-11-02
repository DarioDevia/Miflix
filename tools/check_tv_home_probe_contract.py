"""Experimental tvDebug only: stable TV/Mobile source and resources remain exact."""
import subprocess
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE='e20be389c79e0176fc9fa068fd1c91354f824641'
SOURCE_SET='    // AGP creates combined-variant configurations only after the source set exists.\n    sourceSets.maybeCreate("tvDebug")\n'
ADDITION='    // Isolated experiment: absent from TV Release and every Mobile variant.\n    add("tvDebugImplementation", "androidx.tvprovider:tvprovider:1.1.0")\n'
class ProbeContract(unittest.TestCase):
    def test_all_preexisting_app_sources_tests_resources_and_tools_unchanged(self):
        files=subprocess.check_output(['git','ls-tree','-r','--name-only',BASE,'--','app','tools'],cwd=ROOT,text=True).splitlines()
        for path in files:
            before=subprocess.check_output(['git','show',BASE+':'+path],cwd=ROOT)
            after=(ROOT/path).read_bytes()
            if path=='app/build.gradle.kts':after=after.decode().replace(ADDITION,'').replace(SOURCE_SET,'').encode()
            self.assertEqual(before,after,path)
    def test_dependency_is_scoped_to_tv_debug_only(self):
        s=(ROOT/'app/build.gradle.kts').read_text()
        self.assertEqual(1,s.count(ADDITION))
        self.assertEqual(1,s.count(SOURCE_SET))
        self.assertNotIn('tvImplementation',s)
        self.assertNotIn('engage-tv',s)
    def test_no_external_service_secrets_or_playback_changes(self):
        src=ROOT/'app/src/tvDebug/java/ar/com/miflix/client'
        for p in src.glob('TvHomeProbe*.kt'):
            s=p.read_text()
            for bad in ('telegram.local.properties','TELEGRAM_API','api_hash','READ_EPG_DATA','WRITE_SECURE_SETTINGS','READ_TV_LISTINGS','Runtime.getRuntime','Media3','TelegramSession','TelegramRangeDataSource'):
                self.assertNotIn(bad,s,p.name)
        activity=(src/'TvHomeProbeActivity.kt').read_text()
        for marker in ('requestChannelBrowsable','PROGRAMAS_VERIFICADOS','CANAL_EXISTENTE','VISIBILIDAD_EN_HOME=NO_OBSERVADA',
            'ChannelLogoUtils.storeChannelLogo','writes.withLock','scope.cancel()','Dispatchers.IO'):
            self.assertIn(marker,activity)
    def test_exported_artwork_provider_is_read_only_and_path_limited(self):
        s=(ROOT/'app/src/tvDebug/java/ar/com/miflix/client/TvHomeProbeImages.kt').read_text()
        for marker in ('[a-f0-9]{64}','mode != "r"','tv_home_probe_art','ParcelFileDescriptor.MODE_READ_ONLY'):
            self.assertIn(marker,s)
        self.assertEqual(3,s.count('throw UnsupportedOperationException()'))
if __name__=='__main__':unittest.main()
