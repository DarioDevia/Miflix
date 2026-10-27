#!/usr/bin/env python3
"""Checks an already-built APK for accidental test credentials or signing containers.

No private value is supplied, read, printed or embedded. This bounded regression scan
is not a claim that arbitrary unknown secrets can be detected without knowing them.
"""
import argparse
import json
import zipfile

MARKERS = (b'synthetic-key-only-for-tests', b'synthetic-key', b'synthetic-session',
           b'synthetic-pin', b'test-private-key', b'replacement-synthetic-key')
FORBIDDEN_EXTENSIONS = ('.keystore', '.jks', '.p12', '.pfx', '.key', '.pem')


def verify(path):
    checked = 0
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise ValueError('APK con entrada ZIP dañada')
        for entry in archive.infolist():
            if entry.filename.lower().endswith(FORBIDDEN_EXTENSIONS):
                raise ValueError('La APK contiene un contenedor de firma o clave privada')
            data = archive.read(entry)
            if any(marker in data for marker in MARKERS):
                raise ValueError('La APK contiene una credencial de pruebas')
            checked += 1
    return {'zip_entries_checked': checked, 'test_credentials_found': False,
            'signing_key_containers_found': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk')
    print(json.dumps(verify(parser.parse_args().apk), ensure_ascii=False))
