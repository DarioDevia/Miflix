"""TV 0.1.15 candidate evidence: inspect real APKs, never private files or signing keys."""
import argparse,json,re,subprocess
from pathlib import Path
from zipfile import ZipFile
from verify_tv_physical_apk import inspect_apk,packaging_errors,verify_signature,manifest_elements,backup_xml,PROVENANCE
EPG='com.android.providers.tv.permission.WRITE_EPG_DATA'
def elements(path):
    with ZipFile(path) as z:
        names=z.namelist()
        assert not any(n.lower().endswith(('.jks','.keystore','.session','telegram.local.properties')) for n in names)
        return list(manifest_elements(z.read('AndroidManifest.xml')))
def inspect_candidate(debug,release,mobile,signer,generated):
    reports={}
    for variant,path in [('tvDebug',debug),('tvRelease',release),('mobileDebug',mobile)]:
        report=inspect_apk(path);elems=elements(path)
        main=next(a for tag,a in elems if tag=='activity' and a.get('name','').endswith('.MainActivity'))
        assert not any('TvHomeProbe' in a.get('name','') for _,a in elems)
        if variant.startswith('tv'):
            assert main.get('launchMode')==2
            assert EPG in report['permissions']
            assert not any('READ_EPG' in p or 'SECURE_SETTINGS' in p for p in report['permissions'])
            providers=[a for tag,a in elems if tag=='provider' and a.get('name','').endswith('.TvHomeImages')]
            assert len(providers)==1 and providers[0].get('exported') is True
            assert providers[0].get('authorities')=='ar.com.miflix.client.tv.homeprobe.images'
            assert not providers[0].get('grantUriPermissions',False)
            with ZipFile(path) as z:
                for resource,count in [('res/xml/backup_rules.xml',1),('res/xml/data_extraction_rules.xml',2)]:
                    root_tag='full-backup-content' if count==1 else 'data-extraction-rules'
                    rules=list(manifest_elements(backup_xml(z,resource,root_tag)))
                    for domain,value in [('sharedpref','miflix_telegram.xml'),('file','tdlib/')]:
                        assert sum(tag=='exclude' and attrs.get('domain')==domain and attrs.get('path')==value for tag,attrs in rules)>=count
            policy=dict(report);policy['debuggable']=True
            assert not packaging_errors(policy,json.loads(PROVENANCE.read_text())),packaging_errors(policy,json.loads(PROVENANCE.read_text()))
            assert report['debuggable']==(variant=='tvDebug')
        else:
            assert report['package']=='ar.com.miflix.client'
            assert report['versionName']=='0.9.2-diagnostico' and report['versionCode']==26
            assert main.get('launchMode',0)==0
            assert EPG not in report['permissions']
            assert not any('TvHomeImages' in a.get('name','') for _,a in elems)
        reports[variant]=report
    verify_signature(signer,debug)
    release_verification=subprocess.run([str(signer),'verify','--print-certs',str(release)],capture_output=True,text=True)
    assert release_verification.returncode!=0,'Expected unsigned Release, do not relabel a signed artifact'
    # Read only generated TV configs (whose flavor forcibly supplies empty credentials), never Mobile or local properties.
    configs=[p for p in generated.rglob('BuildConfig.java') if any(part.lower() in ('tv','tvdebug','tvrelease') for part in p.parts)]
    assert len(configs)==2,'Expected both generated TV BuildConfigs'
    for p in configs:
        text=p.read_text()
        assert re.search(r'TELEGRAM_API_ID\s*=\s*0\s*;',text),'TV API ID must be empty'
        assert re.search(r'TELEGRAM_API_HASH\s*=\s*""\s*;',text),'TV API hash must be empty'
    reports.update(tvCredentialsEmbedded=False,privateKeyAvailableInWork=False,
        debugSignatureCompatibleWithInstalledDevice='UNKNOWN',releaseSigned=False,
        physicalValidation='PENDING',mobileIntegration=False)
    return reports
def signing_input(path):
    report=inspect_apk(path)
    normalized=dict(report);normalized['debuggable']=True
    assert not packaging_errors(normalized,json.loads(PROVENANCE.read_text()))
    assert EPG in report['permissions']
    assert not report['debuggable'],'Signing workflow expects the Release candidate'
    elems=elements(path)
    assert not any('TvHomeProbe' in a.get('name','') for _,a in elems)
    assert any(tag=='provider' and a.get('name','').endswith('.TvHomeImages') for tag,a in elems)
    return {k:report[k] for k in ('package','versionName','versionCode','debuggable','testOnly')}
if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--signing-input',type=Path)
    for arg in ('debug','release','mobile','apksigner'):p.add_argument('--'+arg,type=Path)
    p.add_argument('--generated',type=Path,default=Path('app/build/generated/source/buildConfig'))
    a=p.parse_args()
    if a.signing_input:
        print(json.dumps(signing_input(a.signing_input)))
    else:
        assert all((a.debug,a.release,a.mobile,a.apksigner))
        print(json.dumps(inspect_candidate(a.debug,a.release,a.mobile,a.apksigner,a.generated),indent=2))
