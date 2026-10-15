"""Verify the unchanged TDLib dependency's exact AAR, Java bindings and four ELF files."""
import argparse
import hashlib
import io
import json
import struct
from pathlib import Path
from zipfile import ZipFile


def verify(path):
    expected=json.loads(Path(__file__).with_name('tdlib-0.1.1-provenance.json').read_text())
    if hashlib.sha256(path.read_bytes()).hexdigest()!=expected['aar_sha256']:
        raise ValueError('AAR differs from verified Maven Central core:0.1.1')
    with ZipFile(path) as aar:
        bindings=aar.read('libs/tdlib-classes.jar')
        if hashlib.sha256(bindings).hexdigest()!=expected['bindings_sha256']:
            raise ValueError('Java bindings differ')
        with ZipFile(io.BytesIO(bindings)) as jar:
            for name in ('org/drinkless/tdlib/Client.class','org/drinkless/tdlib/TdApi.class'):
                if name not in jar.namelist(): raise ValueError('Missing '+name)
        for abi, reference in expected['libraries'].items():
            data=aar.read('jni/'+abi+'/libtdjni.so')
            actual={'sha256':hashlib.sha256(data).hexdigest(),'size':len(data),
                'elf_class':data[4],'elf_machine':struct.unpack_from('<H',data,18)[0]}
            if data[:4]!=b'\x7fELF' or data[5]!=1 or actual!=reference:
                raise ValueError('ELF mismatch: '+abi)
            print(abi,actual['sha256'])
    print('PASS: unchanged AAR/bindings and four real ELF libraries. Not an APK or runtime test.')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('aar',type=Path)
    verify(parser.parse_args().aar)
