"""Small packaging-policy tests; no Android execution or signature claims."""
import copy
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from zipfile import ZipFile
from verify_tv_physical_apk import INTRO_SHA256, INTRO_SIZE, REQUESTS_QR_SHA256, REQUESTS_QR_SIZE, PROVENANCE, packaging_errors, inspect_apk, verify_signature, backup_xml

class PackagingPolicyTest(unittest.TestCase):
    def setUp(self):
        self.provenance=json.loads(PROVENANCE.read_text())
        self.report={"package":"ar.com.miflix.client.tv","versionName":"0.1.15-diagnostico",
            "versionCode":16,"minSdk":26,"targetSdk":34,"testOnly":False,"debuggable":True,
            "leanbackRequired":True,"touchscreenRequired":False,"leanbackLauncher":True,
            "tvLibraryBackupExcluded":True,
            "requestsQr":{"sha256":REQUESTS_QR_SHA256,"size":REQUESTS_QR_SIZE},
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

class OptimizedBackupResourcesTest(unittest.TestCase):
    def fake_xml(self,data):
        if data==b'backup':yield ('full-backup-content',{})
        elif data==b'transfer':yield ('data-extraction-rules',{})
        else:yield ('other',{})
    def archive(self,entries):
        from io import BytesIO
        stream=BytesIO()
        with ZipFile(stream,'w') as z:
            for key,value in entries.items():z.writestr(key,value)
        stream.seek(0)
        return ZipFile(stream)
    def test_shortened_release_paths_keep_backup_checks(self):
        with self.archive({'res/A.xml':b'backup','res/B.xml':b'transfer'}) as z, patch('verify_tv_physical_apk.manifest_elements',self.fake_xml):
            self.assertEqual(b'backup',backup_xml(z,'res/xml/backup_rules.xml','full-backup-content'))
            self.assertEqual(b'transfer',backup_xml(z,'res/xml/data_extraction_rules.xml','data-extraction-rules'))
    def test_missing_or_ambiguous_backup_resources_are_rejected(self):
        for entries in ({'res/A.xml':b'other'},{'res/A.xml':b'backup','res/B.xml':b'backup'}):
            with self.archive(entries) as z, patch('verify_tv_physical_apk.manifest_elements',self.fake_xml):
                with self.assertRaises(ValueError):backup_xml(z,'res/xml/backup_rules.xml','full-backup-content')
    def test_named_debug_resource_is_also_checked_for_its_root(self):
        with self.archive({'res/xml/backup_rules.xml':b'other'}) as z, patch('verify_tv_physical_apk.manifest_elements',self.fake_xml):
            with self.assertRaises(ValueError):backup_xml(z,'res/xml/backup_rules.xml','full-backup-content')

class FrozenSourcesTest(unittest.TestCase):
    def test_protected_sources_and_progress_remain_exact(self):
        from check_tv015_home_contract import Tv015HomeContracts
        Tv015HomeContracts().test_protected_runtime_sources_byte_exact()
    def test_only_tv_version_changes_and_arm_configuration_is_identical(self):
        from check_tv015_home_contract import Tv015HomeContracts
        Tv015HomeContracts().test_gradle_scope_and_safe_tv_credentials()
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
