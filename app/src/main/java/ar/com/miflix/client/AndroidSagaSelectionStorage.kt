package ar.com.miflix.client

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser

/** Only last public collection IDs; independent of progress/favorites and all credentials. */
internal class AndroidSagaSelectionStorage(context: Context) : SagaSelectionStorage {
    private val prefs = context.applicationContext.getSharedPreferences("miflix_client", Context.MODE_PRIVATE)
    override fun read(): List<String> = runCatching {
        JsonParser.parseString(prefs.getString("sagas_previous_ids", "[]")).asJsonArray.mapNotNull {
            it.takeIf { value -> value.isJsonPrimitive && value.asJsonPrimitive.isString }
                ?.asString?.takeIf(String::isNotBlank)
        }.take(10)
    }.getOrDefault(emptyList())
    override fun write(ids: List<String>) {
        prefs.edit().putString("sagas_previous_ids", Gson().toJson(ids)).apply()
    }
}
