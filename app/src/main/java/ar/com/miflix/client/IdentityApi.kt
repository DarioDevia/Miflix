package ar.com.miflix.client

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

internal class IdentityApiFailure(val reason: IdentityFailure) : Exception()
internal object IdentityApi {
    // Separate bounded client: never changes catalog/Telegram/Media3 networking.
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    suspend fun verify(config: IdentityConfig, token: String, nonce: String): ConfirmedIdentity = withContext(Dispatchers.IO) {
        if (!config.enabled || token.length > 8192) throw IdentityApiFailure(IdentityFailure.REJECTED)
        val body = com.google.gson.JsonObject().apply { addProperty("idToken", token); addProperty("nonce", nonce) }
        val request = Request.Builder().url(config.baseUrl.trimEnd('/') + "/auth/google")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            if (response.code != 200) throw IdentityApiFailure(when (response.code) {
                400, 401 -> IdentityFailure.REJECTED
                else -> IdentityFailure.UNAVAILABLE
            })
            val input = response.body?.byteStream() ?: throw IdentityApiFailure(IdentityFailure.REJECTED)
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (output.size() <= 8192) {
                val count = input.read(buffer, 0, minOf(buffer.size, 8193 - output.size()))
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            if (output.size() > 8192) throw IdentityApiFailure(IdentityFailure.REJECTED)
            parseResponse(output.toString("UTF-8"), System.currentTimeMillis() / 1000)
        }
    }
    internal fun parseResponse(text: String, now: Long): ConfirmedIdentity {
        try {
            require(text.length <= 8192)
            val root = JsonParser.parseString(text).asJsonObject
            require(root.get("authenticated").asBoolean)
            require(!root.getAsJsonObject("authorization").get("privateDataAllowed").asBoolean)
            val identity = root.getAsJsonObject("identity")
            require(identity.get("provider").asString == "google")
            val sub = identity.get("sub").asString
            val expiry = root.get("expiresAt").asLong
            require(sub.matches(Regex("[!-~]{1,255}")) && expiry > now && expiry - now <= 7200)
            fun optional(key: String, max: Int): String? = identity.get(key)?.takeUnless { it.isJsonNull }
                ?.asString?.takeIf { it.isNotBlank() && it.length <= max }
            return ConfirmedIdentity(sub, optional("name", 200), optional("email", 320), expiry)
        } catch (_: Exception) { throw IdentityApiFailure(IdentityFailure.REJECTED) }
    }
}
