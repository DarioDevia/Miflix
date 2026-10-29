"""Inspect only manifest/native packaging, never DEX, sessions or BuildConfig.

Default mode requires a signed universal TV 0.1.13 debug APK and Android apksigner.
--inspect diagnoses older APKs without claiming verification or installability.
Uses Python 3 standard library; does not execute or modify the APK.
"""
import struct
from zipfile import ZipFile, BadZipFile
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import sys

def manifest_elements(data):
    def u16(p): return struct.unpack_from('<H',data,p)[0]
    def u32(p): return struct.unpack_from('<I',data,p)[0]
    if len(data) < 8 or u16(0) != 3 or u32(4) != len(data):
        raise ValueError('Expected Android compiled XML')
    strings=[]
    offset=u16(2)
    while offset<len(data):
        kind, header, size=struct.unpack_from('<HHI',data,offset)
        if size<header or size<8 or offset+size>len(data): raise ValueError('Malformed binary XML chunk')
        if kind==1:
            count=u32(offset+8); utf8=bool(u32(offset+16)&0x100); start=offset+u32(offset+20)
            def length(p,wide=False):
                n=u16(p) if wide else data[p];p+=2 if wide else 1
                mask=0x8000 if wide else 0x80
                if n&mask:
                    n=((n & (mask-1)) << (16 if wide else 8)) | (u16(p) if wide else data[p]);p+=2 if wide else 1
                return n,p
            for i in range(count):
                pos=start+u32(offset+header+4*i)
                n,pos=length(pos,not utf8)
                if utf8: n,pos=length(pos)
                strings.append(data[pos:pos+n*(1 if utf8 else 2)].decode('utf-8' if utf8 else 'utf-16-le'))
        elif kind==0x102:
            ext=offset+header; name=strings[u32(ext+4)]
            attrstart,attrsize,count=struct.unpack_from('<HHH',data,ext+8)
            attrs={}
            for i in range(count):
                a=ext+attrstart+i*attrsize; key=strings[u32(a+4)];raw=u32(a+8);typ=data[a+15];value=u32(a+16)
                value=strings[raw] if raw!=0xffffffff else strings[value] if typ==3 else bool(value) if typ==18 else value
                attrs[key]=value
            yield name,attrs
        offset+=size

INTRO_PATH = "assets/miflix_tv_intro.mp4"
INTRO_SHA256 = "ecede13d1d994ba31de549b7d3ca557e64d69c4f3518bbae07caae5841423b17"
INTRO_SIZE = 2134895

REQUESTS_QR_PATH = "assets/miflix_tv_requests_qr.png"
REQUESTS_QR_SHA256 = "260478e570101b569de5511987e4c8ceed5ccc5132566c72706688b1918d7c49"
REQUESTS_QR_SIZE = 780

PROVENANCE = Path(__file__).with_name("tdlib-0.1.1-provenance.json")

def inspect_apk(path):
    with ZipFile(path) as apk:
        if len(apk.namelist()) != len(set(apk.namelist())):
            raise ValueError("Duplicate ZIP entries")
        elements = list(manifest_elements(apk.read("AndroidManifest.xml")))
        def attributes(tag):
            return next((attrs for name, attrs in elements if name == tag), {})
        manifest = attributes("manifest")
        app = attributes("application")
        sdk = attributes("uses-sdk")
        features = {a.get("name"): a.get("required", True) for n,a in elements if n == "uses-feature"}
        libraries = {}
        for name in apk.namelist():
            if name.startswith("lib/") and name.endswith("/libtdjni.so"):
                data = apk.read(name)
                if len(data) < 20 or data[:4] != b"\x7fELF" or data[5] != 1:
                    raise ValueError("Invalid little-endian ELF: " + name)
                libraries[name.split('/')[1]] = {
                    "sha256": hashlib.sha256(data).hexdigest(), "size": len(data),
                    "elf_class": data[4], "elf_machine": struct.unpack_from('<H',data,18)[0]}
        intro = apk.read(INTRO_PATH) if INTRO_PATH in apk.namelist() else None
        qr = apk.read(REQUESTS_QR_PATH) if REQUESTS_QR_PATH in apk.namelist() else None
        backup_local = True
        for resource, count in (("res/xml/backup_rules.xml",1),("res/xml/data_extraction_rules.xml",2)):
            if resource not in apk.namelist():
                backup_local = False
                continue
            exclusions = [a for n,a in manifest_elements(apk.read(resource)) if n == "exclude"]
            backup_local = backup_local and sum(a.get("domain")=="sharedpref" and
                a.get("path")=="miflix_tv_library.xml" for a in exclusions) >= count
        return {
            "tvLibraryBackupExcluded": backup_local,
            "requestsQr": {"sha256": hashlib.sha256(qr).hexdigest(), "size": len(qr)} if qr is not None else None,
            "intro": {"sha256": hashlib.sha256(intro).hexdigest(), "size": len(intro)} if intro is not None else None,
            "package": manifest.get("package"), "versionName": manifest.get("versionName"),
            "versionCode": manifest.get("versionCode"), "minSdk": sdk.get("minSdkVersion"),
            "targetSdk": sdk.get("targetSdkVersion"), "testOnly": app.get("testOnly",False),
            "debuggable": app.get("debuggable",False),
            "leanbackRequired": features.get("android.software.leanback"),
            "touchscreenRequired": features.get("android.hardware.touchscreen"),
            "leanbackLauncher": any(n == "category" and a.get("name") ==
                "android.intent.category.LEANBACK_LAUNCHER" for n,a in elements),
            "permissions": sorted(a.get("name", "") for n,a in elements if n == "uses-permission"),
            "cleartextTraffic": app.get("usesCleartextTraffic",False), "libraries": libraries}

def packaging_errors(report, provenance):
    errors=[]
    expected = {"package":"ar.com.miflix.client.tv", "versionName":"0.1.13-diagnostico",
        "versionCode":14,"minSdk":26,"targetSdk":34,"testOnly":False,"debuggable":True,
        "leanbackRequired":True,"touchscreenRequired":False,"leanbackLauncher":True,
        "cleartextTraffic":False,"tvLibraryBackupExcluded":True}
    for key,value in expected.items():
        if report.get(key) != value: errors.append(f"{key}: expected {value}, found {report.get(key)}")
    if report.get("intro") != {"sha256": INTRO_SHA256, "size": INTRO_SIZE}:
        errors.append("Original TV intro MP4 missing or altered (path/hash/size)")
    if report.get("requestsQr") != {"sha256": REQUESTS_QR_SHA256, "size": REQUESTS_QR_SIZE}:
        errors.append("Exact public-contact QR missing or altered")
    allowed = {"android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
        "ar.com.miflix.client.tv.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}
    if not set(report.get("permissions",[])) <= allowed:
        errors.append("Unexpected permissions; inspect merged manifest before distribution")
    for abi,expected_lib in provenance["libraries"].items():
        if report["libraries"].get(abi) != expected_lib:
            errors.append(f"{abi}: missing or differs from verified core:0.1.1 ELF/hash/size")
    if set(report["libraries"]) != set(provenance["libraries"]):
        errors.append("Unexpected TDLib ABI set")
    return errors

def verify_signature(apksigner, apk):
    # Official Build Tools verifies actual APK signatures, not mere META-INF presence.
    result = subprocess.run([str(apksigner), "verify", "--verbose", "--min-sdk-version", "26", str(apk)],
        capture_output=True, text=True)
    if result.returncode:
        raise ValueError("Official apksigner rejected the APK signature")

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk",type=Path)
    parser.add_argument("--inspect",action="store_true",help="Diagnostic only; no certification")
    parser.add_argument("--apksigner",type=Path,help="SDK Build Tools/apksigner.bat (required for verification)")
    args=parser.parse_args()
    try:
        report=inspect_apk(args.apk)
        digest = hashlib.sha256()
        with args.apk.open("rb") as source:
            for block in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(block)
        report["apkSha256"] = digest.hexdigest()
        print(json.dumps(report,indent=2,ensure_ascii=False))
        if args.inspect:
            print("INSPECTION ONLY: no signature, installation or playback verified")
            return 0
        errors=packaging_errors(report,json.loads(PROVENANCE.read_text()))
        if not args.apksigner or not args.apksigner.is_file():
            errors.append("Pass --apksigner with the official SDK Build Tools executable")
        if errors:
            print("REJECTED: " + "; ".join(errors),file=sys.stderr)
            return 1
        verify_signature(args.apksigner,args.apk)
        print("PASS: inspected TV 0.1.13 debug packaging and signature. Physical installation/playback still untested.")
        return 0
    except (OSError,ValueError,KeyError,IndexError,struct.error,BadZipFile) as error:
        print("REJECTED: " + str(error),file=sys.stderr)
        return 1

if __name__ == "__main__":
    sys.exit(main())
