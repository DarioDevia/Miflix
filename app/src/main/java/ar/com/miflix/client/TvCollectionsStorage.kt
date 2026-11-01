package ar.com.miflix.client

import android.content.Context
import org.json.JSONArray

/** Separate, disposable rotation hint; never touches personal-library or progress preferences. */
internal class TvSagaPreferences(context: Context): TvSagaStorage {
    private val prefs = context.applicationContext.getSharedPreferences("miflix_tv_sagas", Context.MODE_PRIVATE)
    override fun read(): List<String> = runCatching {
        val array = JSONArray(prefs.getString("previous", "[]"))
        (0 until array.length()).mapNotNull { array.opt(it) as? String }.take(10)
    }.getOrDefault(emptyList())
    override fun write(ids: List<String>) { prefs.edit().putString("previous", JSONArray(ids).toString()).apply() }
}
