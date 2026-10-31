package ar.com.miflix.admin

import android.content.Context
import android.util.AtomicFile
import com.google.gson.JsonObject
import java.io.File
import java.security.KeyStore

/** Immutable by content hash. Separate from historical undo, collections and encrypted server configuration. */
class RetiredMetadataArchives(private val directory:File) {
    @Synchronized fun preserve(state:JsonObject):String {
        val raw=state.toString();val id=java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
        check(directory.isDirectory || directory.mkdirs()) {"No se pudo crear el respaldo privado. Conservamos el original."}
        val target=File(directory,"$id.json")
        if(target.exists()) {check(target.readText()==raw) {"El respaldo previo está dañado; no se sobrescribió."};return id}
        val atomic=AtomicFile(target);val output=atomic.startWrite()
        try {output.write(raw.toByteArray(Charsets.UTF_8));atomic.finishWrite(output);check(target.readText()==raw)}
        catch(_:Exception) {atomic.failWrite(output);error("No se pudo confirmar el respaldo privado. No se limpiaron datos.")}
        return id
    }
    @Synchronized fun read(id:String):String {require(id.matches(Regex("[a-f0-9]{64}")));return File(directory,"$id.json").readText()}
}
object RetiredCredentials {
    /** Does not decrypt the removed secret or open/delete the Cloudflare/TMDB configuration. */
    fun remove(context:Context,deleteKey:()->Unit={
        val keys=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        if(keys.containsAlias("miflix-admin-thetvdb-v1")) keys.deleteEntry("miflix-admin-thetvdb-v1")
    }) {
        val file=AtomicFile(File(context.noBackupFilesDir,"thetvdb-config.enc"));file.delete()
        listOf("thetvdb-config.enc","thetvdb-config.enc.bak","thetvdb-config.enc.new").forEach {name->val old=File(context.noBackupFilesDir,name);if(old.exists()) check(old.delete()) {"No se pudo retirar una copia de la credencial eliminada."}}
        check(listOf("thetvdb-config.enc","thetvdb-config.enc.bak","thetvdb-config.enc.new").none {File(context.noBackupFilesDir,it).exists()}) {"No se pudo retirar la credencial del proveedor eliminado. Las otras credenciales se conservaron."}
        deleteKey()
    }
}
