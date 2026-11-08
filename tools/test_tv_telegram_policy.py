"""Synthetic generated fields only; never opens an owner's local properties or Telegram data."""
import tempfile,unittest
from pathlib import Path
from inspect_tv015_apks import validate_tv_build_configs

class TvTelegramArtifactPolicyTest(unittest.TestCase):
    def configs(self,root,id_value='0',hash_value=''):
        for kind in ('debug','release'):
            path=root/'tv'/kind/'BuildConfig.java';path.parent.mkdir(parents=True,exist_ok=True)
            path.write_text('public static final int TELEGRAM_API_ID = '+id_value+';\npublic static final String TELEGRAM_API_HASH = "'+hash_value+'";')
    def test_public_ci_accepts_only_empty_configuration(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);self.configs(root)
            self.assertFalse(validate_tv_build_configs(root,'ci-empty'))
    def test_private_mode_accepts_synthetic_preconfiguration(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);self.configs(root,'12345','0'*32)
            self.assertTrue(validate_tv_build_configs(root,'private-preconfigured'))
    def test_public_mode_rejects_configured_apks_without_echoing_values(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);self.configs(root,'12345','0'*32)
            with self.assertRaises(AssertionError) as failure:validate_tv_build_configs(root,'ci-empty')
            self.assertNotIn('12345',str(failure.exception));self.assertNotIn('0'*32,str(failure.exception))
    def test_family_mode_rejects_empty_and_incomplete_configuration(self):
        for id_value,hash_value in [('0',''),('12345',''),('0','0'*32),('12345','bad')]:
            with tempfile.TemporaryDirectory() as d:
                root=Path(d);self.configs(root,id_value,hash_value)
                with self.assertRaises(AssertionError):validate_tv_build_configs(root,'private-preconfigured')
    def test_missing_variants_and_unknown_mode_fail_closed(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            with self.assertRaises(AssertionError):validate_tv_build_configs(root,'ci-empty')
            self.configs(root)
            with self.assertRaises(AssertionError):validate_tv_build_configs(root,'unknown')

if __name__=='__main__':unittest.main()
