package ar.com.miflix.client

import android.content.MutableContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.*
import java.io.IOException
import java.security.SecureRandom
import java.util.Base64

/** Intentionally memory-only: process recreation never restores an unverified session or token. */
@Composable
internal fun rememberMiFlixAccount(): MutableState<AccountState> {
    val account = remember { mutableStateOf(AccountState()) }
    LaunchedEffect(account.value.identity?.expiresAt) {
        val expiry = account.value.identity?.expiresAt ?: return@LaunchedEffect
        delay(((expiry - System.currentTimeMillis() / 1000).coerceAtLeast(0) * 1000))
        account.value = account.value.expire(System.currentTimeMillis() / 1000)
    }
    return account
}

@Composable
internal fun MiFlixAccountSection(account: MutableState<AccountState>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { CredentialManager.create(context.applicationContext) }
    val config = remember { IdentityConfig(BuildConfig.GOOGLE_WEB_CLIENT_ID, BuildConfig.MIFLIX_IDENTITY_URL) }
    val state = account.value.expire(System.currentTimeMillis() / 1000)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Cuenta MiFlix", color = MiFlixStyle.primaryText, style = MaterialTheme.typography.titleLarge)
        Text("Identificación opcional para futuros servicios de MiFlix. Es independiente de Telegram y no sincroniza tu biblioteca.",
            color = MiFlixStyle.secondaryText)
        if (!config.enabled) {
            Text("Google no está configurado en esta compilación. Podés seguir usando MiFlix normalmente.", color = MiFlixStyle.secondaryText)
        }
        val identity = state.identity
        if (identity == null) {
            Text(if (state.busy) "Conectando…" else "Sin cuenta MiFlix conectada", color = MiFlixStyle.secondaryText)
            Button(enabled = config.enabled && !state.busy, onClick = {
                if (!account.value.busy) {
                    account.value = account.value.start()
                    scope.launch {
                        try {
                            val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
                            val option = GetSignInWithGoogleOption.Builder(config.webClientId).setNonce(nonce).build()
                            val response = withTimeout(120_000) {
                                manager.getCredential(MutableContextWrapper(context), GetCredentialRequest.Builder().addCredentialOption(option).build())
                            }
                            val credential = response.credential
                            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                account.value = account.value.failed(IdentityFailure.PROVIDER)
                            } else {
                                // Token stays in this request only. Never saved, logged or decoded as proof.
                                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                                val verified = IdentityApi.verify(config, token, nonce)
                                account.value = account.value.confirmed(verified, System.currentTimeMillis() / 1000)
                            }
                        } catch (_: GetCredentialCancellationException) {
                            account.value = account.value.failed(IdentityFailure.CANCELLED)
                        } catch (_: TimeoutCancellationException) {
                            account.value = account.value.failed(IdentityFailure.UNAVAILABLE)
                        } catch (e: CancellationException) { throw e
                        } catch (_: GetCredentialException) {
                            account.value = account.value.failed(IdentityFailure.PROVIDER)
                        } catch (e: IdentityApiFailure) {
                            account.value = account.value.failed(e.reason)
                        } catch (_: IOException) {
                            account.value = account.value.failed(IdentityFailure.NETWORK)
                        } catch (_: Exception) {
                            account.value = account.value.failed(IdentityFailure.UNAVAILABLE)
                        } finally {
                            if (account.value.busy) account.value = account.value.copy(busy = false)
                        }
                    }
                }
            }) { Text("Iniciar sesión con Google") }
        } else {
            identity.name?.let { Text(it, color = MiFlixStyle.primaryText, style = MaterialTheme.typography.titleMedium) }
            identity.email?.let { Text(it, color = MiFlixStyle.secondaryText) }
            Text("Identidad confirmada por el servidor", color = MiFlixStyle.secondaryText)
            TextButton(enabled = !state.busy, onClick = {
                account.value = account.value.signedOut().copy(busy = true)
                scope.launch {
                    try { withTimeout(10_000) { manager.clearCredentialState(ClearCredentialStateRequest()) } }
                    catch (e: CancellationException) { if (e !is TimeoutCancellationException) throw e }
                    catch (_: Exception) { /* Local identity is already gone; Google consent is not revoked. */ }
                    finally { account.value = account.value.signedOut() }
                }
            }) { Text("Cerrar sesión") }
        }
        state.message?.let { Text(it, color = MiFlixStyle.secondaryText) }
    }
}
