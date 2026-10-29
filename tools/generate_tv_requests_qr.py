"""Generate the bundled contact QR locally. Dev-only dependency: qrcode==8.2.
No Android runtime dependency and no network service. Reproducible grayscale PNG.
"""
import re
import struct
import zlib
from pathlib import Path
import qrcode
ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT/'app/src/main/java/ar/com/miflix/client/TvSuggestions.kt'
TARGET = ROOT/'app/src/tv/assets/miflix_tv_requests_qr.png'
def generate():
    url = re.search(r'const val TV_REQUESTS_URL = "([^"]+)"', SOURCE.read_text()).group(1)
    qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=10, border=4)
    qr.add_data(url); qr.make(fit=True)
    matrix = qr.get_matrix(); scale=10; size=len(matrix)*scale
    raw=b''.join(b'\0'+bytes(0 if cell else 255 for cell in row for _ in range(scale))
                 for row in matrix for _ in range(scale))
    def chunk(kind,data): return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',size,size,8,0,0,0,0))
    png+=chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
    TARGET.parent.mkdir(parents=True,exist_ok=True);TARGET.write_bytes(png)
    return png
if __name__=='__main__': generate()
