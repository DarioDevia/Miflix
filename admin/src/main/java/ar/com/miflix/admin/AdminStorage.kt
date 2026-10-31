package ar.com.miflix.admin

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import com.google.gson.*
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface StateStore {
    fun read(): JsonObject?; fun write(state: JsonObject)
    fun archive(state:JsonObject):String = error("No hay un respaldo privado disponible para retirar metadatos históricos. Conservamos el estado original.")
    fun readArchive(id:String):String = error("Respaldo privado no disponible en este almacenamiento.")
}
class FileStateStore(private val file: File): StateStore {
    override fun read(): JsonObject? = if (!file.exists()) null else JsonParser.parseString(file.readText()).asJsonObject
    override fun write(state: JsonObject) {
        file.parentFile?.mkdirs()
        val tmp=File(file.path+".tmp")
        java.io.FileOutputStream(tmp).use { out -> out.write(state.toString().toByteArray()); out.fd.sync() }
        check(tmp.renameTo(file)) { "No se pudo conservar el trabajo local." }
    }
}
class AndroidStateStore(context: Context): StateStore {
    private val file=AtomicFile(File(context.filesDir,"admin-state.json"))
    private val recovery=File(context.filesDir,"admin-state-recuperacion.json")
    private val archives=RetiredMetadataArchives(File(context.noBackupFilesDir,"retired-metadata"))
    override fun archive(state:JsonObject)=archives.preserve(state)
    override fun readArchive(id:String)=archives.read(id)
    @Synchronized override fun read(): JsonObject? = try {
        val raw=file.openRead().bufferedReader().use { it.readText() }
        try { JsonParser.parseString(raw).asJsonObject.also { saved -> saved.get("catalog")?.let { CatalogRules.parse(it.toString()) } } }
        catch(e: Exception) { recovery.writeText(raw);throw e }
    }
        catch (_: java.io.FileNotFoundException) { null }
    @Synchronized override fun write(state: JsonObject) {
        val out=file.startWrite()
        try { out.write(state.toString().toByteArray());file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out);throw e }
    }
}

data class AdminConfig(val baseUrl: String, val token: String, val tmdbToken: String = "") {
    fun normalized(): AdminConfig {
        val url=java.net.URI(baseUrl.trim())
        require(url.scheme == "https" && !url.host.isNullOrBlank() && url.userInfo == null && url.query == null && url.fragment == null) { "Usá una URL HTTPS del Worker, sin credenciales ni parámetros." }
        require(token.trim().isNotBlank()) { "Falta la credencial administrativa." }
        val suffix=url.path.trimEnd('/').removeSuffix("/admin/catalogo").removeSuffix("/catalogo.json")
        require(!suffix.contains("..")) { "Ruta de servidor inválida." }
        return copy(baseUrl="https://${url.rawAuthority}$suffix",token=token.trim(),tmdbToken=tmdbToken.trim())
    }
}
class SecureConfig(context: Context) {
    private val file=AtomicFile(File(context.noBackupFilesDir,"admin-config.enc"))
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias="miflix-admin-config-v1"
        (store.getKey(alias,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load(): AdminConfig? = try {
        val raw=JsonParser.parseString(file.openRead().bufferedReader().use { it.readText() }).asJsonObject
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(raw.text("iv"),Base64.NO_WRAP)))
        Gson().fromJson(String(cipher.doFinal(Base64.decode(raw.text("data"),Base64.NO_WRAP))),AdminConfig::class.java).normalized()
    } catch (_: Exception) { null }
    fun save(config: AdminConfig) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val raw=JsonObject().apply {
            addProperty("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
            addProperty("data",Base64.encodeToString(cipher.doFinal(Gson().toJson(config.normalized()).toByteArray()),Base64.NO_WRAP))
        }
        val out=file.startWrite()
        try { out.write(raw.toString().toByteArray());file.finishWrite(out) } catch(e:Exception) {file.failWrite(out);throw e}
    }
}
