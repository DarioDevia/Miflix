import json
import tempfile
import unittest
from pathlib import Path
from locate_tv_apk import locate_tv_apk
class LocateApkTest(unittest.TestCase):
    def seed(self, root, variant, name):
        folder=root/variant;folder.mkdir(exist_ok=True)
        (folder/name).write_bytes(b'placeholder, locator never claims APK inspection')
        (folder/'output-metadata.json').write_text(json.dumps({'variantName':variant,'elements':[{'outputFile':name}]}))
        return folder/name
    def test_uses_metadata_output_name_and_excludes_release(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            debug=self.seed(root,'tvDebug','custom-abi-name.apk')
            self.seed(root,'tvRelease','app-release-unsigned.apk')
            self.assertEqual(debug.resolve(),locate_tv_apk(root))
    def test_missing_and_ambiguous_outputs_are_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            with self.assertRaises(ValueError): locate_tv_apk(root)
            apk=self.seed(root,'tvDebug','one.apk')
            metadata=apk.parent/'output-metadata.json'
            data=json.loads(metadata.read_text());data['elements']*=2
            metadata.write_text(json.dumps(data))
            with self.assertRaises(ValueError): locate_tv_apk(root)
    def test_output_cannot_escape_build_root(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)/'outputs';root.mkdir()
            folder=root/'debug';folder.mkdir()
            (folder/'output-metadata.json').write_text(json.dumps({'variantName':'tvDebug','elements':[{'outputFile':'../../outside.apk'}]}))
            with self.assertRaises(ValueError): locate_tv_apk(root)
if __name__=='__main__': unittest.main()
