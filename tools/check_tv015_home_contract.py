"""Narrow TV 0.1.15 extensions against physically validated 0.1.14; no blanket source exclusions."""
from pathlib import Path
import subprocess,unittest
from xml.etree import ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
BASE='e20be389c79e0176fc9fa068fd1c91354f824641'
PATCHES={'app/src/main/java/ar/com/miflix/client/MainActivity.kt': [(66, 66, '', '    private val homeEntry = TvHomeIntegration()\n\n    override fun onNewIntent(intent: Intent) {\n        super.onNewIntent(intent)\n        homeEntry.receive(intent)\n    }\n\n    override fun onSaveInstanceState(outState: Bundle) {\n        homeEntry.save(outState)\n        super.onSaveInstanceState(outState)\n    }\n\n'), (80, 80, '', '        homeEntry.create(intent, savedInstanceState)\n'), (87, 87, '', '                    homeEntry = homeEntry,\n'), (155, 155, '', '    homeEntry: TvHomeIntegration,\n'), (331, 331, '', '    }\n    homeEntry.ObserveCatalog(catalog, collectionsReady && !busy && problem == null)\n    homeEntry.Dispatch(titles, collectionsReady && !busy, telegramState is TelegramSession.State.Ready) { title ->\n        // Only TV dispatches: use the normal detail and Back route, never playback.\n        pendingResume = null\n        pendingNavigation = null\n        playbackIssue = null\n        episodeTransition = false\n        fullscreen = false\n        if (telegramState is TelegramSession.State.Ready) {\n            screen = Screen.HOME\n            selectedId = null\n            if (title != null) openDetail(title)\n        }\n'), (1066, 1068, '        if (BuildConfig.IS_TV) tvTelegramSetupMessage(BuildConfig.TELEGRAM_API_ID,\n            BuildConfig.TELEGRAM_API_HASH.isNotBlank())?.let { message ->\n', '        if (BuildConfig.IS_TV) tvHomeCredentialMessage()?.let { message ->\n')], 'app/src/main/java/ar/com/miflix/client/PlaybackScreen.kt': [(218, 220, '                    if (BuildConfig.IS_TV) Text(tvTelegramSetupMessage(BuildConfig.TELEGRAM_API_ID,\n                        BuildConfig.TELEGRAM_API_HASH.isNotBlank()).orEmpty())\n', '                    if (BuildConfig.IS_TV) TvHomeCredentials(session)\n')], 'app/build.gradle.kts': [(19, 19, '', '// Explicit private-family build: fail before generating an unconfigured APK.\n// Normal/CI builds without this opt-in retain the original empty defaults.\nval tvFamilyBuild = providers.gradleProperty("miflixTvFamilyBuild").orNull == "true"\nrequire(!tvFamilyBuild || (telegramId > 0 && telegramHash.matches(Regex("[0-9a-fA-F]{32}")))) {\n    "Configuración Telegram familiar ausente o incompleta. Prepará telegram.local.properties en la raíz; no distribuyas esta APK sin preconfiguración."\n}\n\nif (!tvFamilyBuild && telegramId == 0 && gradle.startParameter.taskNames.any { it.contains("tv", ignoreCase = true) }) {\n    logger.warn("MiFlix TV sin preconfiguración Telegram: APK de diagnóstico. Para la APK familiar usá telegram.local.properties y -PmiflixTvFamilyBuild=true.")\n}\n'), (43, 45, '            versionCode = 15\n            versionName = "0.1.14-diagnostico"\n', '            versionCode = 16\n            versionName = "0.1.15-diagnostico"\n'), (59, 59, '', '    add("tvImplementation", "androidx.tvprovider:tvprovider:1.1.0")\n')], 'app/src/tv/AndroidManifest.xml': [(1, 1, '', '    <uses-permission android:name="com.android.providers.tv.permission.WRITE_EPG_DATA" />\n    <!-- Removed from the library merge: deprecated on the supported API levels. -->\n    <uses-permission android:name="com.android.providers.tv.permission.READ_EPG_DATA" tools:node="remove" />\n'), (4, 5, '        <activity android:name=".MainActivity" android:exported="true" android:theme="@style/Theme.MiFlix.TV" android:screenOrientation="landscape" android:hardwareAccelerated="true">\n', '        <activity android:name=".MainActivity" android:exported="true" android:launchMode="singleTask" android:theme="@style/Theme.MiFlix.TV" android:screenOrientation="landscape" android:hardwareAccelerated="true">\n'), (10, 10, '', '        <!-- Legacy authority retained so previously published generated artwork keeps loading. -->\n        <provider android:name=".TvHomeImages" android:authorities="${applicationId}.homeprobe.images"\n            android:exported="true" android:grantUriPermissions="false" />\n')], 'app/src/tv/res/xml/backup_rules.xml': [(4, 4, '', '        <exclude domain="sharedpref" path="miflix_telegram.xml" />\n        <exclude domain="sharedpref" path="miflix_home_probe.xml" />\n        <exclude domain="file" path="tdlib/" />\n        <exclude domain="file" path="tv_home_probe_art/" />\n')], 'app/src/tv/res/xml/data_extraction_rules.xml': [(5, 5, '', '        <exclude domain="sharedpref" path="miflix_telegram.xml" />\n        <exclude domain="sharedpref" path="miflix_home_probe.xml" />\n        <exclude domain="file" path="tdlib/" />\n        <exclude domain="file" path="tv_home_probe_art/" />\n'), (9, 9, '', '        <exclude domain="sharedpref" path="miflix_telegram.xml" />\n        <exclude domain="sharedpref" path="miflix_home_probe.xml" />\n        <exclude domain="file" path="tdlib/" />\n        <exclude domain="file" path="tv_home_probe_art/" />\n')]}
class Tv015HomeContracts(unittest.TestCase):
    def test_protected_runtime_sources_byte_exact(self):
        paths=subprocess.check_output(['git','ls-tree','-r','--name-only',BASE,'--','app'],cwd=ROOT,text=True).splitlines()
        for path in paths:
            old=subprocess.check_output(['git','show',BASE+':'+path],cwd=ROOT)
            new=(ROOT/path).read_bytes()
            if path in PATCHES:
                lines=old.decode().splitlines(keepends=True)
                for first,last,before,after in reversed(PATCHES[path]):
                    self.assertEqual(before,''.join(lines[first:last]),path)
                    lines[first:last]=after.splitlines(keepends=True)
                self.assertEqual(''.join(lines).encode(),new,path)
            else:self.assertEqual(old,new,path)
    def test_gradle_scope_and_safe_tv_credentials(self):
        source=(ROOT/'app/build.gradle.kts').read_text()
        tv=source[source.index('create("tv")'):source.index('    compileOptions')]
        self.assertIn('versionCode = 16',tv)
        self.assertIn('versionName = "0.1.15-diagnostico"',tv)
        self.assertNotIn('"TELEGRAM_API_ID"',tv)
        self.assertNotIn('"TELEGRAM_API_HASH"',tv)
        original=subprocess.check_output(['git','show',BASE+':app/build.gradle.kts'],cwd=ROOT,text=True)
        # The original root loader and defaultConfig values are restored byte-for-byte.
        self.assertEqual(original[original.index('val telegramPrivate'):original.index('android {')].split('val telegramId =')[0],
            source[source.index('val telegramPrivate'):source.index('android {')].split('val telegramId =')[0])
        self.assertIn('buildConfigField("int", "TELEGRAM_API_ID", telegramId.toString())',source)
        self.assertIn('miflixTvFamilyBuild',source)
        self.assertIn('add("tvImplementation", "androidx.tvprovider:tvprovider:1.1.0")',source)
        self.assertNotIn('tvDebugImplementation',source)
        self.assertNotIn('WorkManager',source)
    def test_no_probe_or_background_components_and_mobile_is_inactive(self):
        android='{http://schemas.android.com/apk/res/android}'
        root=ET.parse(ROOT/'app/src/tv/AndroidManifest.xml').getroot()
        app=root.find('application')
        self.assertEqual(['.MainActivity'],[a.get(android+'name') for a in app.findall('activity')])
        self.assertFalse(app.findall('service'));self.assertFalse(app.findall('receiver'))
        self.assertEqual('singleTask',app.find('activity').get(android+'launchMode'))
        self.assertEqual(['.TvHomeImages'],[a.get(android+'name') for a in app.findall('provider')])
        stub=(ROOT/'app/src/mobile/java/ar/com/miflix/client/TvHomeIntegration.kt').read_text()
        self.assertEqual(6,stub.count('= Unit'))
        for forbidden in ('getStringExtra','mutableStateOf','startActivity','TvContract','onOpen('):self.assertNotIn(forbidden,stub)
    def test_selection_and_identity_preserve_experiment_except_universal_id_two(self):
        prefix='app/src/tv/java/ar/com/miflix/client/'
        src=(ROOT/prefix/'TvHomeReleases.kt').read_text()
        old=subprocess.check_output(['git','show','2c241c68dbaf470713d71dc8e4a5c100bbff6316:app/src/tvDebug/java/ar/com/miflix/client/TvHomeProbeReleases.kt'],cwd=ROOT,text=True)
        marker='internal data class HomeProbeDate'
        self.assertEqual(old[old.index(marker):],src[src.index(marker):])
        self.assertIn('eligible.singleOrNull()',src);self.assertNotIn('it.id==2L',src)
        self.assertIn('miflix.homeprobe.v1:',(ROOT/prefix/'TvHomePolicy.kt').read_text())
    def test_shared_hook_opens_normal_detail_not_playback(self):
        source=(ROOT/'app/src/main/java/ar/com/miflix/client/MainActivity.kt').read_text()
        hook=source[source.index('    homeEntry.Dispatch'):source.index('    fun startPlayback')]
        self.assertIn('openDetail(title)',hook);self.assertIn('screen = Screen.HOME',hook)
        for forbidden in ('requestPlayback','startPlayback','TelegramRangeDataSource'):self.assertNotIn(forbidden,hook)
        before=subprocess.check_output(['git','show',BASE+':app/src/main/java/ar/com/miflix/client/PlaybackScreen.kt'],cwd=ROOT,text=True)
        after=(ROOT/'app/src/main/java/ar/com/miflix/client/PlaybackScreen.kt').read_text()
        expected=before.replace('if (BuildConfig.IS_TV) Text(tvTelegramSetupMessage(BuildConfig.TELEGRAM_API_ID,\n                        BuildConfig.TELEGRAM_API_HASH.isNotBlank()).orEmpty())','if (BuildConfig.IS_TV) TvHomeCredentials(session)')
        self.assertEqual(expected,after)
    def test_automatic_sync_is_local_bounded_and_safe(self):
        src=(ROOT/'app/src/tv/java/ar/com/miflix/client/TvHomeSync.kt').read_text()
        for marker in ('Mutex()','Dispatchers.IO','withTimeout(45_000L)','snapshot.titles==catalog.items.orEmpty()',
            'selection.cards.isEmpty()','homeApplyVerified','requestChannelBrowsable','CancellationException'):
            self.assertIn(marker,src)
        for forbidden in ('newCall','DEFAULT_URL','WorkManager','PeriodicWorkRequest','startService','deleteChannel'):
            self.assertNotIn(forbidden,src)
        provider=(ROOT/'app/src/tv/java/ar/com/miflix/client/TvHomeImages.kt').read_text()
        for marker in ('canonicalFile','file.parentFile!=folder','mode != "r"','MODE_READ_ONLY','homeImageLeaf'):
            self.assertIn(marker,provider)
        self.assertEqual(3,provider.count('throw UnsupportedOperationException()'))
        artwork=(ROOT/'app/src/tv/java/ar/com/miflix/client/TvHomeArtwork.kt').read_text()
        for marker in ('files.size<32','62L*1024*1024','2*1024*1024','callTimeout(3','16384'):
            self.assertIn(marker,artwork)
        self.assertNotIn('Prueba de Home',artwork)
    def test_credentials_are_user_supplied_hidden_and_never_logged_or_saved_to_activity(self):
        src=(ROOT/'app/src/tv/java/ar/com/miflix/client/TvHomeCredentials.kt').read_text()
        self.assertIn('password=true',src);self.assertIn('session.configure(apiId,apiHash)',src)
        self.assertIn('if (BuildConfig.TELEGRAM_API_ID > 0 && BuildConfig.TELEGRAM_API_HASH.isNotBlank()) null',src)
        src=src[src.index('internal fun TvHomeCredentials('):]
        for forbidden in ('Log.','rememberSaveable {','putString(','TELEGRAM_API_HASH'):
            self.assertNotIn(forbidden,src)
    def test_restore_does_not_change_authorization_playback_or_home_runtime(self):
        baseline='b4cfe6e14793cad9d71b9112bb6166ed2e477620'
        tracked=subprocess.check_output(['git','ls-tree','-r','--name-only',baseline,'--','app'],cwd=ROOT,text=True).splitlines()
        allowed={'app/build.gradle.kts','app/src/tv/java/ar/com/miflix/client/TvHomeCredentials.kt'}
        for path in tracked:
            if path not in allowed:
                self.assertEqual(subprocess.check_output(['git','show',baseline+':'+path],cwd=ROOT),(ROOT/path).read_bytes(),path)
        session=(ROOT/'app/src/main/java/ar/com/miflix/client/TelegramSession.kt').read_text()
        self.assertIn('if (preconfigured) configure(BuildConfig.TELEGRAM_API_ID, BuildConfig.TELEGRAM_API_HASH)',session)
        login=(ROOT/'app/src/main/java/ar/com/miflix/client/PlaybackScreen.kt').read_text()
        self.assertLess(login.index('if (BuildConfig.TELEGRAM_API_ID > 0 && BuildConfig.TELEGRAM_API_HASH.isNotBlank())'),login.index('TvHomeCredentials(session)'))
if __name__=='__main__':unittest.main()
