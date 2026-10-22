"""Small packaging-policy tests; no Android execution or signature claims."""
import copy
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from zipfile import ZipFile
from verify_tv_physical_apk import INTRO_SHA256, INTRO_SIZE, PROVENANCE, packaging_errors, inspect_apk, verify_signature

class PackagingPolicyTest(unittest.TestCase):
    def setUp(self):
        self.provenance=json.loads(PROVENANCE.read_text())
        self.report={"package":"ar.com.miflix.client.tv","versionName":"0.1.12-diagnostico",
            "versionCode":13,"minSdk":26,"targetSdk":34,"testOnly":False,"debuggable":True,
            "leanbackRequired":True,"touchscreenRequired":False,"leanbackLauncher":True,
            "intro":{"sha256":INTRO_SHA256,"size":INTRO_SIZE},
            "cleartextTraffic":False,"permissions":["android.permission.INTERNET"],
            "libraries":copy.deepcopy(self.provenance['libraries'])}
    def errors(self): return packaging_errors(self.report,self.provenance)
    def test_known_packaging_passes_policy_only(self): self.assertEqual([],self.errors())
    def test_x86_only_is_rejected(self):
        self.report['libraries']={'x86':self.report['libraries']['x86']}
        for abi in ('armeabi-v7a','arm64-v8a','x86_64'):
            self.assertTrue(any(abi in e for e in self.errors()))
    def test_wrong_arm_elf_is_rejected(self):
        self.report['libraries']['arm64-v8a']['elf_machine']=3
        self.assertTrue(self.errors())
    def test_different_native_version_is_rejected(self):
        self.report['libraries']['armeabi-v7a']['sha256']='0'*64
        self.assertTrue(self.errors())
    def test_test_only_is_rejected_even_when_debuggable(self):
        self.report['testOnly']=True
        self.assertTrue(any('testOnly' in e for e in self.errors()))
    def test_wrong_app_version_sdk_and_tv_features_are_rejected(self):
        for key,value in [('package','ar.com.miflix.client'),('versionCode',9),
            ('minSdk',30),('leanbackLauncher',False),('touchscreenRequired',True),
            ('cleartextTraffic',True)]:
            with self.subTest(key=key):
                original=self.report[key];self.report[key]=value
                self.assertTrue(any(key in e for e in self.errors()));self.report[key]=original
    def test_extra_permission_is_rejected(self):
        self.report['permissions'].append('android.permission.MANAGE_EXTERNAL_STORAGE')
        self.assertTrue(self.errors())
    def test_signature_requires_actual_tool_success(self):
        with patch('verify_tv_physical_apk.subprocess.run') as run:
            run.return_value=subprocess.CompletedProcess([],1)
            with self.assertRaises(ValueError): verify_signature('apksigner.bat','sample.apk')
            self.assertIn('verify',run.call_args.args[0])
            run.return_value=subprocess.CompletedProcess([],0)
            verify_signature('apksigner.bat','sample.apk')
    def test_duplicate_zip_entries_rejected(self):
        import warnings
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'bad.apk'
            with warnings.catch_warnings():
                warnings.simplefilter('ignore',UserWarning)
                with ZipFile(p,'w') as z:
                    z.writestr('AndroidManifest.xml',b'invalid')
                    z.writestr('AndroidManifest.xml',b'invalid')
            with self.assertRaisesRegex(ValueError,'Duplicate'): inspect_apk(p)

class FrozenSourcesTest(unittest.TestCase):
    def test_protected_application_sources_identical_to_0110(self):
        root=Path(__file__).resolve().parents[1]
        changed=subprocess.check_output(['git','diff','--name-only',
            '5fed7e317710d8ad71e19212bffa6f88a727b3f4','--','app/src'],cwd=root,text=True).splitlines()
        prefix='app/src/main/java/ar/com/miflix/client/'
        allowed={prefix+name for name in ('MainActivity.kt','TvDetailScreen.kt','TvProgressStorage.kt','TvSeasonPresentation.kt','TvCinematicIntro.kt','TvIntroPolicy.kt','PlaybackScreen.kt','TelegramVideo.kt')}
        allowed.update({'app/src/tv/AndroidManifest.xml','app/src/tv/res/layout/miflix_tv_intro_player.xml',
            'app/src/tv/res/values/styles.xml','app/src/tv/assets/miflix_tv_intro.mp4',
            'app/src/test/java/ar/com/miflix/client/TvIntroPolicyTest.kt',
            'app/src/test/java/ar/com/miflix/client/TvProgressStorageTest.kt',
            'app/src/test/java/ar/com/miflix/client/TvSeasonPresentationTest.kt'})
        self.assertTrue(set(changed)<=allowed,set(changed)-allowed)
    def test_only_tv_version_changes_and_arm_configuration_is_identical(self):
        root=Path(__file__).resolve().parents[1]
        old=subprocess.check_output(['git','show',
            '5fed7e317710d8ad71e19212bffa6f88a727b3f4:app/build.gradle.kts'],cwd=root,text=True)
        expected=old.replace('versionCode = 11','versionCode = 13').replace(
            'versionName = "0.1.10-diagnostico"','versionName = "0.1.12-diagnostico"')
        self.assertEqual(expected,(root/'app/build.gradle.kts').read_text())
class IntroAssetPolicyTest(unittest.TestCase):
    setUp = PackagingPolicyTest.setUp
    errors = PackagingPolicyTest.errors
    def test_missing_intro_is_rejected(self):
        self.report['intro']=None
        self.assertTrue(any('MP4' in e for e in self.errors()))
    def test_altered_intro_is_rejected(self):
        self.report['intro']['sha256']='0'*64
        self.assertTrue(any('MP4' in e for e in self.errors()))
    def test_wrong_intro_size_is_rejected(self):
        self.report['intro']['size']=0
        self.assertTrue(any('MP4' in e for e in self.errors()))
if __name__=='__main__': unittest.main()
