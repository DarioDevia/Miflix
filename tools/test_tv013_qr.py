"""Decode the actual bundled QR, not just its source text or a mocked barcode."""
import hashlib
import unittest
from pathlib import Path
from PIL import Image
import zxingcpp
import generate_tv_requests_qr as generator
ROOT=Path(__file__).resolve().parents[1]
class TvQrTest(unittest.TestCase):
    def test_real_png_decodes_to_exact_public_contact(self):
        with Image.open(generator.TARGET) as image:
            results=zxingcpp.read_barcodes(image)
        self.assertEqual(['https://t.me/Dario_Devia'],[x.text for x in results])
    def test_offline_generation_is_reproducible_and_keeps_quiet_zone(self):
        before=generator.TARGET.read_bytes()
        self.assertEqual(before,generator.generate())
        with Image.open(generator.TARGET) as image:
            self.assertGreaterEqual(image.width,250)
            self.assertEqual(255,image.getpixel((0,0)))
            self.assertEqual(image.width,image.height)
        self.assertEqual('260478e570101b569de5511987e4c8ceed5ccc5132566c72706688b1918d7c49',hashlib.sha256(before).hexdigest())
if __name__=='__main__': unittest.main()
