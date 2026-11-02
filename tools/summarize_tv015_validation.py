"""Summarize actual CI XML outputs; fail closed if reports are absent or contain errors."""
import json
from pathlib import Path
from xml.etree import ElementTree as ET
root=Path('app/build')
result={'tests':{},'lint':{},'androidRuntimeTested':False,'physicalStability':'PENDING'}
for variant in ('TvDebug','TvRelease','MobileDebug'):
    folder=root/'test-results'/('test'+variant+'UnitTest')
    files=list(folder.glob('TEST-*.xml'))
    assert files,variant
    totals={k:0 for k in ('tests','failures','errors','skipped')}
    for path in files:
        suite=ET.parse(path).getroot()
        for k in totals:totals[k]+=int(suite.get(k,0))
    assert totals['failures']==totals['errors']==0
    result['tests'][variant]=totals
    path=root/'reports'/('lint-results-'+variant[0].lower()+variant[1:]+'.xml')
    issues=ET.parse(path).getroot().findall('issue')
    errors=sum(x.get('severity') in ('Error','Fatal') for x in issues)
    assert errors==0
    result['lint'][variant]={'errors':errors,'warnings':sum(x.get('severity')=='Warning' for x in issues)}
print(json.dumps(result,indent=2))
