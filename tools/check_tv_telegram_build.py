"""CI-only non-secret fixture: exercise original root properties and compile configured TV.
No APK is assembled here. Fixture is removed and generated outputs cleaned before public builds.
"""
import json,os,subprocess
from pathlib import Path
from inspect_tv015_apks import validate_tv_build_configs

ROOT=Path(__file__).resolve().parents[1]
CONFIG=ROOT/'telegram.local.properties'

def gradle(stage,tasks,family=True,expected=True,diagnostic=None):
    args=['bash','./gradlew',*tasks,'--console=plain','--no-configuration-cache']
    if family:args.append('-PmiflixTvFamilyBuild=true')
    r=subprocess.run(args,cwd=ROOT,capture_output=True,text=True,timeout=900)
    output=r.stdout+r.stderr
    if (r.returncode==0)!=expected:
        # Never dump generated source/parameters. This fixture is non-secret nevertheless.
        raise RuntimeError(stage+' did not produce its expected result (values withheld)')
    if diagnostic and diagnostic not in output:raise RuntimeError(stage+' missing clear diagnostic')
    if '12345' in output or '0'*32 in output:raise RuntimeError('Build echoed synthetic configuration')

if __name__=='__main__':
    if os.environ.get('GITHUB_ACTIONS')!='true':raise SystemExit('CI-only synthetic test; use the family build script locally.')
    if CONFIG.exists():raise SystemExit('Existing properties detected; refusing to read or replace them.')
    os.chmod(ROOT/'gradlew',0o755)
    results={}
    try:
        gradle('missing',[':app:generateTvDebugBuildConfig'],expected=False,diagnostic='Configuración Telegram familiar ausente')
        results['missingFamilyConfigRejected']=True
        CONFIG.write_text('TELEGRAM_API_ID=12345\n')
        gradle('incomplete',[':app:generateTvDebugBuildConfig'],expected=False,diagnostic='Configuración Telegram privada incompleta')
        results['incompleteFamilyConfigRejected']=True
        CONFIG.write_text('TELEGRAM_API_ID=12345\nTELEGRAM_API_HASH='+('0'*32)+'\n')
        gradle('syntheticConfiguredCompile',[':app:compileTvDebugKotlin',':app:compileTvReleaseKotlin'])
        validate_tv_build_configs(ROOT/'app/build/generated/source/buildConfig','private-preconfigured')
        results['configuredTvDebugAndReleaseCompile']=True
    finally:
        CONFIG.unlink(missing_ok=True)
    gradle('cleanPublicDefaults',[':app:clean',':app:generateTvDebugBuildConfig',':app:generateTvReleaseBuildConfig'],family=False)
    validate_tv_build_configs(ROOT/'app/build/generated/source/buildConfig','ci-empty')
    results.update(syntheticFixtureRemoved=True,publicDefaultsVerifiedEmpty=True,realCredentialsRead=False,realTelegramLoginTested=False)
    out=ROOT/'telegram-build-contract.json';out.write_text(json.dumps(results,indent=2)+'\n')
    print(json.dumps(results))
