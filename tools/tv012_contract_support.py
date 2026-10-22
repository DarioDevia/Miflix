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
