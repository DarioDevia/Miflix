package ar.com.miflix.client

import java.net.URI

/** Public configuration only. Never an OAuth client secret or a Telegram credential. */
internal data class IdentityConfig(val webClientId: String, val baseUrl: String) {
    val enabled: Boolean get() = webClientId.matches(Regex("[0-9]+-[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com")) &&
        runCatching { val u = URI(baseUrl)
            u.scheme == "https" && !u.host.isNullOrBlank() && u.rawUserInfo == null &&
                u.rawQuery == null && u.rawFragment == null && (u.path.isNullOrEmpty() || u.path == "/")
        }.getOrDefault(false)
}
internal data class ConfirmedIdentity(val sub: String, val name: String?, val email: String?, val expiresAt: Long)
internal enum class IdentityFailure(val message: String) {
    CANCELLED("Inicio de sesión cancelado."),
    NETWORK("No se pudo conectar. Revisá tu conexión e intentá nuevamente."),
    REJECTED("No se pudo confirmar tu identidad. Volvé a iniciar sesión con Google."),
    UNAVAILABLE("La conexión de Cuenta MiFlix no está disponible. Intentá más tarde."),
    PROVIDER("No se pudo abrir Google. Revisá Google Play Services y la configuración de la aplicación.")
}
internal data class AccountState(val identity: ConfirmedIdentity? = null, val busy: Boolean = false,
    val message: String? = null) {
    fun start() = if (busy) this else AccountState(busy = true)
    fun failed(reason: IdentityFailure) = AccountState(message = reason.message)
    fun confirmed(value: ConfirmedIdentity, now: Long) = if (value.sub.isNotBlank() && value.expiresAt > now)
        AccountState(identity = value) else failed(IdentityFailure.REJECTED)
    fun expire(now: Long) = if (identity != null && identity.expiresAt <= now)
        AccountState(message = "La confirmación venció. Volvé a iniciar sesión con Google.") else this
    fun signedOut() = AccountState(message = "Cuenta MiFlix desconectada. Tu sesión Telegram no cambió.")
}
