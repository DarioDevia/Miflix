"""Inspect a debug-only experiment and ensure Release/Mobile have no experimental components."""
import argparse,json
from pathlib import Path
from zipfile import ZipFile
from verify_tv_physical_apk import inspect_apk,packaging_errors,verify_signature,manifest_elements,PROVENANCE
EPG='com.android.providers.tv.permission.WRITE_EPG_DATA'
def components(path):
    with ZipFile(path) as z:
        return [(tag,attrs) for tag,attrs in manifest_elements(z.read('AndroidManifest.xml'))
            if tag in ('activity','provider','receiver','service') and 'TvHomeProbe' in attrs.get('name','')]
def main():
    p=argparse.ArgumentParser();p.add_argument('--debug',type=Path,required=True);p.add_argument('--release',type=Path,required=True)
    p.add_argument('--mobile',type=Path,required=True);p.add_argument('--apksigner',type=Path,required=True);args=p.parse_args()
    debug=inspect_apk(args.debug)
    assert EPG in debug['permissions']
    stable={**debug,'permissions':[x for x in debug['permissions'] if x!=EPG]}
    errors=packaging_errors(stable,json.loads(PROVENANCE.read_text()))
    assert not errors, errors
    assert len(components(args.debug))==2
    assert all(a.get('exported') is True for _,a in components(args.debug))
    assert not any('READ_EPG' in x or 'SECURE_SETTINGS' in x or 'READ_TV_LISTINGS' in x for x in debug['permissions'])
    verify_signature(args.apksigner,args.debug)
    for name,path in [('release',args.release),('mobile',args.mobile)]:
        report=inspect_apk(path)
        assert EPG not in report['permissions'],name
        assert not components(path),name
        if name=='release':
            for field in ('package','versionName','versionCode','minSdk','targetSdk','libraries','intro','requestsQr'):
                assert report[field]==debug[field],field
            assert report['debuggable'] is False
        else:
            assert report['package']=='ar.com.miflix.client'
            assert report['versionName']=='0.9.2-diagnostico' and report['versionCode']==26
    print(json.dumps({'tvDebug':debug,'experimentComponents':components(args.debug),
        'releaseAndMobileIsolated':True,'runtimeProviderAndLauncherTested':False},indent=2))
if __name__=='__main__':main()
