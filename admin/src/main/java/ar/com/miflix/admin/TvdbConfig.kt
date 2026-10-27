package ar.com.miflix.admin

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Not a data class: default logging must never reveal secrets. No format beyond documented strings is assumed. */
class TvdbCredentials(val apiKey: String, val pin: String = "") {
    fun normalized(): TvdbCredentials {
        val key=apiKey.trim();val subscriber=pin.trim()
        require(key.isNotEmpty() && key.length<=512 && key.all {it in '!'..'~'}) {"Ingresá una API Key sin espacios internos ni saltos de línea (máximo 512 caracteres)."}
        require(subscriber.length<=128 && subscriber.all {it in '!'..'~'}) {"El PIN no puede contener espacios ni saltos de línea."}
        return TvdbCredentials(key,subscriber)
    }
    override fun toString() = "TvdbCredentials([protegidas])"
}
interface TvdbCredentialStore {
    fun load(): TvdbCredentials?
    fun save(credentials: TvdbCredentials)
    fun clear()
}
/** AES-GCM, random IV for each atomic write. Key is non-exportable in Android Keystore. */
internal class TvdbSecretCodec(private val key: ()->SecretKey) {
    fun encrypt(credentials: TvdbCredentials): String {
        val c=credentials.normalized();val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        val plain=JsonObject().apply {addProperty("apikey",c.apiKey);addProperty("pin",c.pin)}
        return JsonObject().apply {
            addProperty("iv",Base64.getEncoder().encodeToString(cipher.iv))
            addProperty("data",Base64.getEncoder().encodeToString(cipher.doFinal(plain.toString().toByteArray(Charsets.UTF_8))))
        }.toString()
    }
    fun decrypt(raw: String): TvdbCredentials {
        val obj=JsonParser.parseString(raw).asJsonObject
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.getDecoder().decode(obj.text("iv"))))
        val plain=JsonParser.parseString(String(cipher.doFinal(Base64.getDecoder().decode(obj.text("data"))),Charsets.UTF_8)).asJsonObject
        return TvdbCredentials(plain.text("apikey"),plain.text("pin")).normalized()
    }
}
class SecureTvdbConfig private constructor(context: Context, private val codec: TvdbSecretCodec): TvdbCredentialStore {
    constructor(context: Context): this(context,TvdbSecretCodec {androidKey()})
    internal constructor(context: Context,key: ()->SecretKey): this(context,TvdbSecretCodec(key))
    private val file=AtomicFile(File(context.noBackupFilesDir,"thetvdb-config.enc"))
    private companion object {
        fun androidKey(): SecretKey {
            val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
            val alias="miflix-admin-thetvdb-v1"
            return (store.getKey(alias,null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
        }
    }
    @Synchronized override fun load(): TvdbCredentials? = try {
        codec.decrypt(file.openRead().bufferedReader().use {it.readText()})
    } catch(_:java.io.FileNotFoundException) {null}
    catch(_:Exception) {error("No se pudo descifrar TheTVDB. Volvé a guardar su clave; no borres los datos de Admin.")}
    @Synchronized override fun save(credentials: TvdbCredentials) {
        val encrypted=codec.encrypt(credentials)
        val out=file.startWrite()
        try {out.write(encrypted.toByteArray(Charsets.UTF_8));file.finishWrite(out)}
        catch(_:Exception) {file.failWrite(out);error("No se pudo guardar la configuración protegida de TheTVDB.")}
    }
    @Synchronized override fun clear() {file.delete();check(!file.baseFile.exists()) {"No se pudo eliminar la configuración de TheTVDB."}}
}
