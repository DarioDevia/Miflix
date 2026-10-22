"""Normalize only the explicit TV 0.1.12 progress wiring for historical frozen-source guards."""
STORE = '''        val storage = SharedPreferencesProgressStorage(context)
        PlaybackProgressStore(if (BuildConfig.IS_TV) TvProgressStorage(storage) else storage)'''
LISTENER = '''    // TV Home also reacts to persistence changes without requiring screen/lifecycle changes.
    DisposableEffect(context, screen, catalog) {
        val prefs = context.getSharedPreferences("miflix_playback_progress", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            if (BuildConfig.IS_TV && screen == Screen.HOME)
                continuing = continueWatching(catalog.items.orEmpty(), progressStore)
        }
        val observing = BuildConfig.IS_TV && screen == Screen.HOME
        if (observing) prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { if (observing) prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

'''
def without_tv012_progress_wiring(text):
    assert text.count(STORE) == 1
    assert text.count(LISTENER) == 1
    return text.replace(STORE, '        PlaybackProgressStore(SharedPreferencesProgressStorage(context))').replace(LISTENER, '')

def without_tv012_apk_location(text):
    new = """    $tvApkPath = & py -3 tools/locate_tv_apk.py
    if ($LASTEXITCODE -ne 0) { throw 'Cannot locate a unique TV Debug APK from AGP metadata.' }
    & py -3 tools/verify_tv_physical_apk.py $tvApkPath --apksigner $apksigner"""
    old = """    & py -3 tools/verify_tv_physical_apk.py app/build/outputs/apk/tv/debug/app-tv-debug.apk `
        --apksigner $apksigner"""
    assert text.count(new) == 1
    return text.replace(new, old)
