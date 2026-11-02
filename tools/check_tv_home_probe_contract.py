"""Experimental tvDebug only: stable TV/Mobile source and resources remain exact."""
import subprocess
import unittest
from xml.etree import ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE='e20be389c79e0176fc9fa068fd1c91354f824641'
SOURCE_SET='    // AGP creates combined-variant configurations only after the source set exists.\n    sourceSets.maybeCreate("tvDebug")\n'
ADDITION='    // Isolated experiment: absent from TV Release and every Mobile variant.\n    add("tvDebugImplementation", "androidx.tvprovider:tvprovider:1.1.0")\n'
MAIN_HOOKS=['    private val homeEntry = ExperimentalHomeEntry()\n\n    override fun onNewIntent(intent: Intent) {\n        super.onNewIntent(intent)\n        homeEntry.receive(intent)\n    }\n\n    override fun onSaveInstanceState(outState: Bundle) {\n        homeEntry.save(outState)\n        super.onSaveInstanceState(outState)\n    }\n\n', '        homeEntry.create(intent, savedInstanceState)\n', '                    homeEntry = homeEntry,\n', '    homeEntry: ExperimentalHomeEntry,\n', '    }\n    homeEntry.Dispatch(titles, collectionsReady && !busy, telegramState is TelegramSession.State.Ready) { title ->\n        // Only tvDebug dispatches: use the normal detail and Back route, never playback.\n        pendingResume = null\n        pendingNavigation = null\n        playbackIssue = null\n        episodeTransition = false\n        fullscreen = false\n        if (telegramState is TelegramSession.State.Ready) {\n            screen = Screen.HOME\n            selectedId = null\n            if (title != null) openDetail(title)\n        }\n']
class ProbeContract(unittest.TestCase):
    def test_all_preexisting_app_sources_tests_resources_and_tools_unchanged(self):
        files=subprocess.check_output(['git','ls-tree','-r','--name-only',BASE,'--','app','tools'],cwd=ROOT,text=True).splitlines()
        for path in files:
            before=subprocess.check_output(['git','show',BASE+':'+path],cwd=ROOT)
            after=(ROOT/path).read_bytes()
            if path=='app/src/main/java/ar/com/miflix/client/MainActivity.kt':
                after=after.decode()
                for addition in MAIN_HOOKS:
                    self.assertEqual(1,after.count(addition),addition)
                    after=after.replace(addition,'',1)
                after=after.encode()
            if path=='app/build.gradle.kts':after=after.decode().replace(ADDITION,'').replace(SOURCE_SET,'').encode()
            self.assertEqual(before,after,path)
    def test_working_image_and_app_opening_paths_are_preserved(self):
        path='app/src/tvDebug/java/ar/com/miflix/client/TvHomeProbeActivity.kt'
        old=subprocess.check_output(['git','show','1bed37f520dd14bb1f34ed35dda7dfbf76a11e69:'+path],cwd=ROOT,text=True)
        new=(ROOT/path).read_text()
        for start,end in [('    private fun image(', '    private suspend fun publish(')]:
            self.assertEqual(old[old.index(start):old.index(end)],new[new.index(start):new.index(end)])
    def test_ten_releases_accept_years_without_inventing_dates(self):
        source=(ROOT/'app/src/tvDebug/java/ar/com/miflix/client/TvHomeProbeReleases.kt').read_text()
        self.assertIn('HOME_PROBE_LIMIT = 10',source)
        self.assertIn('date>today',source)
        self.assertIn('title.year?.takeIf',source)
        activity=(ROOT/'app/src/tvDebug/java/ar/com/miflix/client/TvHomeProbeActivity.kt').read_text()
        self.assertLess(activity.index('if(cards.isEmpty()) return'),activity.index('val (channel,created)=ensureChannel()'))
        self.assertIn('VERIFY_REPLACEMENTS_BEFORE_DELETE',activity)
        self.assertIn('HOME_PROBE_DISPLAY_NAME = "MiFlix TV"',source)
        self.assertIn('"MiFlix - Prueba Home"',source)
        for forbidden in ('fecha_agregado','fecha_emision','setOf(2026','LocalDate.of(title.year','PRUEBA SINTÉTICA'):
            self.assertNotIn(forbidden,source)
    def test_external_details_are_debug_only_and_never_start_playback(self):
        root=ROOT/'app/src'
        for variant in ('mobile','tvRelease'):
            stub=(root/variant/'java/ar/com/miflix/client/ExperimentalHomeEntry.kt').read_text()
            self.assertEqual(4,stub.count('= Unit'))
            for forbidden in ('getStringExtra','Log.','startActivity','onOpen(', 'mutableStateOf'):
                self.assertNotIn(forbidden,stub)
        hook=next(x for x in MAIN_HOOKS if 'homeEntry.Dispatch' in x)
        self.assertIn('openDetail(title)',hook)
        self.assertIn('screen = Screen.HOME',hook)
        self.assertNotIn('startPlayback',hook)
        self.assertNotIn('requestPlayback',hook)
        adapter=(root/'tvDebug/java/ar/com/miflix/client/ExperimentalHomeEntry.kt').read_text()
        for marker in ('withTimeoutOrNull(HOME_DETAIL_WAIT_MS)','saved.getString(savedKey)',
            'inbox.consume(request.serial)','inbox.serial != request.serial','snapshotFlow { latestAuthorized }'):
            self.assertIn(marker,adapter)
        manifest=ET.parse(root/'tvDebug/AndroidManifest.xml').getroot()
        android='{http://schemas.android.com/apk/res/android}'
        main=next(x for x in manifest.find('application').findall('activity') if x.get(android+'name')=='.MainActivity')
        self.assertEqual('singleTask',main.get(android+'launchMode'))
        self.assertFalse(main.findall('intent-filter'))
        publisher=(root/'tvDebug/java/ar/com/miflix/client/TvHomeProbeActivity.kt').read_text()
        self.assertIn('putExtra(HOME_DETAIL_EXTRA,titleId)',publisher)
        self.assertIn('appIntent(requireNotNull(card.titleId))',publisher)
        self.assertIn('Intent.FLAG_ACTIVITY_SINGLE_TOP',publisher)
    def test_only_required_epg_write_permission_survives_library_manifest(self):
        root=ET.parse(ROOT/'app/src/tvDebug/AndroidManifest.xml').getroot()
        android='{http://schemas.android.com/apk/res/android}';tools='{http://schemas.android.com/tools}'
        permissions=root.findall('uses-permission')
        self.assertEqual(2,len(permissions))
        self.assertEqual('com.android.providers.tv.permission.WRITE_EPG_DATA',permissions[0].get(android+'name'))
        self.assertEqual('com.android.providers.tv.permission.READ_EPG_DATA',permissions[1].get(android+'name'))
        self.assertEqual('remove',permissions[1].get(tools+'node'))
    def test_dependency_is_scoped_to_tv_debug_only(self):
        s=(ROOT/'app/build.gradle.kts').read_text()
        self.assertEqual(1,s.count(ADDITION))
        self.assertEqual(1,s.count(SOURCE_SET))
        self.assertNotIn('tvImplementation',s)
        self.assertNotIn('engage-tv',s)
    def test_no_external_service_secrets_or_playback_changes(self):
        src=ROOT/'app/src/tvDebug/java/ar/com/miflix/client'
        for p in src.glob('TvHomeProbe*.kt'):
            s=p.read_text()
            for bad in ('telegram.local.properties','TELEGRAM_API','api_hash','READ_EPG_DATA','WRITE_SECURE_SETTINGS','READ_TV_LISTINGS','Runtime.getRuntime','Media3','TelegramSession','TelegramRangeDataSource'):
                self.assertNotIn(bad,s,p.name)
        activity=(src/'TvHomeProbeActivity.kt').read_text()
        for marker in ('requestChannelBrowsable','PROGRAMAS_VERIFICADOS','CANAL_EXISTENTE','VISIBILIDAD_EN_HOME=NO_OBSERVADA',
            'ChannelLogoUtils.storeChannelLogo','writes.withLock','scope.cancel()','Dispatchers.IO'):
            self.assertIn(marker,activity)
    def test_program_metadata_uses_public_framework_columns_without_suppressions(self):
        s=(ROOT/'app/src/tvDebug/java/ar/com/miflix/client/TvHomeProbeActivity.kt').read_text()
        self.assertIn('TvContract.PreviewPrograms.COLUMN_INTENT_URI',s)
        self.assertIn('TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO',s)
        for forbidden in ('@SuppressLint','@Suppress(','.setTitle(','.setDescription(','.setPosterArtUri(','.setPosterArtAspectRatio(','.setIntentUri(','.setInternalProviderId('):
            self.assertNotIn(forbidden,s)
    def test_exported_artwork_provider_is_read_only_and_path_limited(self):
        s=(ROOT/'app/src/tvDebug/java/ar/com/miflix/client/TvHomeProbeImages.kt').read_text()
        for marker in ('[a-f0-9]{64}','mode != "r"','tv_home_probe_art','ParcelFileDescriptor.MODE_READ_ONLY'):
            self.assertIn(marker,s)
        self.assertEqual(3,s.count('throw UnsupportedOperationException()'))
if __name__=='__main__':unittest.main()
