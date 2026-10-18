"""Source-only guards for the Lint fix. They never claim Android Lint succeeded."""
import re
import subprocess
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
BASE='eb5559ef299d3939c611cb749a562fcb2ff90a45'
PREFIX='app/src/main/java/ar/com/miflix/client/'
ANN='@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)'
def old(path):return subprocess.check_output(['git','show',BASE+':'+path],cwd=ROOT,text=True)
class LintFixContracts(unittest.TestCase):
    def test_only_annotations_change_in_media_and_range_source(self):
        for name in ('PlaybackScreen.kt','TelegramVideo.kt'):
            path=PREFIX+name;actual=(ROOT/path).read_text()
            self.assertEqual(2,actual.count(ANN),name)
            stripped=re.sub(r'(?m)^[ \t]*'+re.escape(ANN)+r'\n','',actual)
            self.assertEqual(old(path),stripped,name)
    def test_opt_ins_have_specific_declaration_scopes(self):
        playback=(ROOT/PREFIX/'PlaybackScreen.kt').read_text()
        ranges=(ROOT/PREFIX/'TelegramVideo.kt').read_text()
        self.assertIn(ANN+'\n@Composable\ninternal fun PlaybackScreen(',playback)
        self.assertIn(ANN+'\nprivate fun logMediaTracks(',playback)
        self.assertIn('    '+ANN+'\n    fun factory()',ranges)
        self.assertIn(ANN+'\nprivate class TelegramRangeDataSource(',ranges)
    def test_main_and_tv_agree_on_existing_exported_activity(self):
        key='{http://schemas.android.com/apk/res/android}'
        main=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').find('./application/activity')
        tv=ET.parse(ROOT/'app/src/tv/AndroidManifest.xml').find('./application/activity')
        self.assertEqual(main.attrib[key+'name'],tv.attrib[key+'name'])
        self.assertEqual('true',main.attrib[key+'exported'])
        self.assertEqual(main.attrib[key+'exported'],tv.attrib[key+'exported'])
        path='app/src/tv/AndroidManifest.xml'
        expected=old(path).replace('android:name=".MainActivity"','android:name=".MainActivity" android:exported="true"')
        self.assertEqual(expected,(ROOT/path).read_text())
    def test_all_other_application_files_are_frozen(self):
        changed=set(subprocess.check_output(['git','diff','--name-only',BASE,'--','app/src'],cwd=ROOT,text=True).splitlines())
        allowed={PREFIX+'PlaybackScreen.kt',PREFIX+'TelegramVideo.kt','app/src/tv/AndroidManifest.xml'}
        self.assertTrue(changed<=allowed,changed-allowed)
    def test_version_dependencies_arm_and_build_lint_are_identical(self):
        for path in ('app/build.gradle.kts','build.gradle.kts','settings.gradle.kts','gradle.properties',
            'tools/tdlib-0.1.1-provenance.json','tools/build_tv_physical_debug.ps1',
            'tools/verify_tv_physical_apk.py'):
            self.assertEqual(old(path),(ROOT/path).read_text(),path)
    def test_no_lint_disable_baseline_or_resource_reflection_suppression_added(self):
        changed=subprocess.check_output(['git','diff',BASE,'--','app','gradle.properties'],cwd=ROOT,text=True)
        additions='\n'.join(line[1:] for line in changed.splitlines() if line.startswith('+') and not line.startswith('+++'))
        for forbidden in ('SuppressLint','Suppress(','baseline','disable','abortOnError','checkReleaseBuilds'):
            self.assertNotIn(forbidden,additions)
        self.assertIn('getIdentifier("miflix_tv_intro_player"',
            (ROOT/PREFIX/'TvCinematicIntro.kt').read_text())
if __name__=='__main__':unittest.main()
