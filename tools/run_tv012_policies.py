"""Compile actual pure Kotlin policies with the existing Kotlin 1.9.22 test jars.
Android I/O implementations are omitted, not simulated. This is not a Gradle/Android build.
Usage: python tools/run_tv012_policies.py /absolute/path/to/kotlin-test-tools
"""
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/ar/com/miflix/client'
TEST = ROOT / 'app/src/test/java/ar/com/miflix/client'
POLICIES = ['TvIntroPolicy', 'TvFeaturedSession', 'MediaTrackPolicy', 'TvTrackPolicy',
            'TvHomeSuggestions', 'TvPlayerControlsPolicy', 'TvRemote', 'TrailerLink',
            'ContinueWatching', 'TvProgressStorage', 'TvSeasonPresentation']
TESTS = ['TvIntroPolicy', 'TvFeaturedSession', 'MediaTrackPolicy', 'TvTrackPolicy',
         'TvHomeSuggestions', 'TvPlayerControlsPolicy', 'TrailerLink',
         'ContinueWatching', 'PlaybackProgress', 'TvProgressStorage', 'TvSeasonPresentation']
def main():
    jars = Path(sys.argv[1]).resolve()
    cp = os.pathsep.join(str(p) for p in sorted(jars.glob('*.jar')))
    with tempfile.TemporaryDirectory(prefix='miflix-tv012-') as directory:
        temp = Path(directory)
        catalog = (SRC/'Catalog.kt').read_text()
        models = catalog[catalog.index('data class Catalog('):catalog.index('data class CatalogLoad(')]
        models = re.sub(r'@SerializedName\("[^"]+"\)\s*', '', models)
        (temp/'Models.kt').write_text('package ar.com.miflix.client\n'+models)
        progress = (SRC/'PlaybackProgress.kt').read_text().split('internal class SharedPreferencesProgressStorage')[0]
        (temp/'Progress.kt').write_text(progress.replace('import android.content.Context\n', ''))
        output = temp/'classes'
        sources = [temp/'Models.kt', temp/'Progress.kt'] + [SRC/(p+'.kt') for p in POLICIES]
        sources += [TEST/(p+'Test.kt') for p in TESTS]
        subprocess.run(['java','-cp',cp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
            '-no-stdlib','-no-reflect','-jvm-target','17','-classpath',cp,'-d',str(output),
            *map(str,sources)],check=True)
        subprocess.run(['java','-cp',str(output)+os.pathsep+cp,'org.junit.runner.JUnitCore',
            *['ar.com.miflix.client.'+p+'Test' for p in TESTS]],check=True)
if __name__ == '__main__': main()
