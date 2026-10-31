package ar.com.miflix.admin

import com.google.gson.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

interface AdminRemote {
    fun download(): JsonObject
    fun publish(catalog: JsonObject)
}
class HttpAdminRemote(private val config: AdminConfig, private val client: OkHttpClient =
    OkHttpClient.Builder().callTimeout(25,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()): AdminRemote {
    private fun request(path: String, body: JsonObject? = null): JsonObject {
        val builder=Request.Builder().url(config.baseUrl+path).header("Authorization","Bearer ${config.token}").header("Cache-Control","no-cache")
        if(body!=null) builder.put(body.toString().toRequestBody("application/json".toMediaType()))
        try {
            return client.newCall(builder.build()).execute().use { response ->
                if(response.code in setOf(401,403)) throw SecurityException("El servidor rechazó la credencial administrativa.")
                check(response.isSuccessful) { when(response.code) {
                    401,403 -> "El servidor rechazó la credencial administrativa."
                    404 -> "El servidor no tiene instalada esta función administrativa (HTTP 404)."
                    else -> "El servidor respondió HTTP ${response.code}."
                } }
                val stream=response.body?.byteStream() ?: error("Respuesta vacía.")
                val out=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(8192)
                while(out.size() <= CatalogRules.MAX_BYTES) {
                    val n=stream.read(buffer,0,minOf(buffer.size,CatalogRules.MAX_BYTES+1-out.size()))
                    if(n<0) break
                    out.write(buffer,0,n)
                }
                val bytes=out.toByteArray()
                check(bytes.size <= CatalogRules.MAX_BYTES) { "Respuesta demasiado grande." }
                try { JsonParser.parseString(String(bytes,Charsets.UTF_8)).asJsonObject } catch (_: Exception) { error("El servidor devolvió una respuesta inválida.") }
            }
        } catch(e: SecurityException) { throw e }
        catch(e: IllegalStateException) { throw e }
        catch (_: Exception) { error("No fue posible completar la conexión. Revisá Internet y reintentá.") }
    }
    override fun download(): JsonObject = CatalogRules.parse(request("/catalogo.json").toString())
    override fun publish(catalog: JsonObject) { check(request("/admin/catalogo",CatalogRules.publicCopy(catalog)).get("ok")?.asBoolean == true) { "El servidor no confirmó la publicación." } }

}

/** One atomic local envelope keeps catalog, base and pending flags together across process death. */
class AdminRepository(private val store: StateStore) {
    var recoveryNotice: String = ""
        private set
    var state: JsonObject = try {
        (store.read() ?: JsonObject()).also { saved -> saved.get("catalog")?.let { CatalogRules.parse(it.toString()) } }
    } catch (_: Exception) {
        recoveryNotice="La copia local no pudo leerse. Se necesita descargar un catálogo válido antes de editar."
        JsonObject()
    }
        private set
    var writable=false
        private set
    val catalog: JsonObject? get()=state.get("catalog")?.takeIf { it.isJsonObject }?.asJsonObject
    private var checkpointsReady = true
    val cleanupIssues get()=state.get(RetiredMetadata.STATE)?.takeIf {it.isJsonObject}?.asJsonObject?.array("issues")?.map {it.asJsonObject.deepCopy()}.orEmpty()
    val cleanupBackups get()=state.get(RetiredMetadata.STATE)?.takeIf {it.isJsonObject}?.asJsonObject?.array("backups")?.map {it.asString}.orEmpty()
    fun privateCleanupBackup(id:String):String {require(id in cleanupBackups);return store.readArchive(id)}
    init {
        try {
            val retired=prepare(state,null)
            val migrated=AdminCheckpoints.migrate(retired)
            if (migrated != state) {store.write(migrated);state=migrated}
        } catch (_: Exception) {
            checkpointsReady = false
            recoveryNotice = "No se pudo conservar el respaldo privado o recuperar el estado local. No se alteró el catálogo. Revisá el espacio disponible y reintentá sin borrar los datos de la app."
        }
    }
    fun checkpoint(type: String): JsonObject? = AdminCheckpoints.get(state, type)
    fun checkpointLink(type: String): String? = checkpoint(type)?.text("telegram_url")?.let(TelegramLinks::valid)
    val unclassifiedCheckpoint get() = AdminCheckpoints.unclassifiedLegacy(state)
    val pending: Boolean get()=state.get("pending")?.asBoolean == true
    val lastSync: String get()=state.text("last_sync")
    private fun prepare(next:JsonObject,previous:JsonObject?=state):JsonObject {
        val result=RetiredMetadata.migrate(next,previous)
        if(!result.changed) return next
        val id=store.archive(next) // durable, immutable snapshot BEFORE changing any active value
        result.state.get("catalog")?.takeIf {it.isJsonObject}?.let {CatalogRules.parse(it.toString())}
        val ledger=result.state.get(RetiredMetadata.STATE)?.takeIf {it.isJsonObject}?.asJsonObject ?: JsonObject().also {result.state.add(RetiredMetadata.STATE,it)}
        val ids=ledger.array("backups").map {it.asString}.toMutableList();if(id !in ids) ids.add(id)
        ledger.add("backups",JsonArray().apply {ids.forEach {add(it)}})
        return result.state
    }
    private fun commit(next: JsonObject) {val safe=prepare(next);store.write(safe);state=safe}
    fun lock() { writable=false }
    fun refresh(remote: AdminRemote, discard: Boolean = false): String {
        writable=false
        check(checkpointsReady) { recoveryNotice }
        val incoming=remote.download() // old state remains untouched until the entire response validates
        CatalogRules.parse(incoming.toString())
        if(pending && !discard) {
            val base=state.get("base")?.takeIf { it.isJsonObject }?.asJsonObject
            check(base!=null && CatalogRules.fingerprint(incoming)==CatalogRules.fingerprint(base)) {
                "El remoto cambió y tenés trabajo pendiente. Conservamos tu copia; sincronizá desde el dispositivo correcto o descartá explícitamente para descargar."
            }
        }
        val next=state.deepCopy()
        if(!pending || discard) { next.add("catalog",incoming.deepCopy());next.add("base",incoming.deepCopy());next.addProperty("pending",false) }
        next.addProperty("last_download",now())
        commit(next);writable=true
        return ""
    }

    fun saveItem(item: JsonObject, cp: JsonObject?=null) {
        check(writable) { "Actualizá el catálogo antes de editar." }
        require(cp == null || cp.text("tipo") == item.text("tipo")) { "El checkpoint no corresponde al tipo de contenido." }
        val next=if(cp == null) state.deepCopy() else AdminCheckpoints.put(state,cp)
        val current=catalog ?: error("Sin catálogo.")
        val saved=CatalogRules.save(current,item)
        next.add("catalog",saved);next.addProperty("pending",true)
        val undo=next.get("metadata_undo")?.takeIf {it.isJsonObject}?.asJsonObject
        if(item.text("id").isBlank() && undo!=null && undo.getAsJsonObject("before").text("id").isBlank() &&
            undo.getAsJsonObject("after").text("_admin_metadata_recovery").isNotBlank() &&
            undo.getAsJsonObject("after").text("_admin_metadata_recovery")==draft()?.text("_admin_metadata_recovery")) {
            val previousIds=CatalogRules.items(current).map {it.text("id")}.toSet()
            val inserted=CatalogRules.items(saved).single {it.text("id") !in previousIds}.text("id")
            undo.getAsJsonObject("before").addProperty("id",inserted);undo.getAsJsonObject("after").addProperty("id",inserted)
        }
        val original=draft()
        if(original!=null && item.text("id").isBlank()) {
            val previousIds=CatalogRules.items(current).map {it.text("id")}.toSet()
            val inserted=CatalogRules.items(saved).singleOrNull {it.text("id") !in previousIds}
            if(inserted!=null) RetiredMetadata.reidentify(next,RetiredMetadata.identity(original),RetiredMetadata.identity(inserted))
        }
        commit(next)
    }
    fun setCheckpoint(cp: JsonObject) {
        check(writable) { "Actualizá el catálogo antes de procesar publicaciones." }
        commit(AdminCheckpoints.put(state,cp))
    }
    fun removeItem(id: String): Boolean {
        check(writable) {"Actualizá el catálogo antes de eliminar."}
        val next=state.deepCopy()
        next.add("catalog",CatalogRules.removeItem(catalog ?: error("Sin catálogo."),id))
        next.addProperty("pending",true)
        if(draft()?.text("id")==id) next.remove("draft")
        commit(next)
        return true
    }
    fun removePart(id: String,season: Long,episode: Long?=null): Boolean {
        check(writable) {"Actualizá el catálogo antes de eliminar."}
        val next=state.deepCopy()
        val saved=catalog?.let {CatalogRules.items(it).firstOrNull {item ->item.text("id")==id}}
        val review=draft()?.takeIf {it.text("id")==id}
        val savedHas=saved!=null && AdminEdits.hasPart(saved,season,episode)
        val reviewHas=review!=null && AdminEdits.hasPart(review,season,episode)
        require(savedHas || reviewHas) {"La temporada o el episodio ya no existe."}
        if(savedHas) {
            next.add("catalog",CatalogRules.save(catalog!!,AdminEdits.removePart(saved!!,season,episode)))
            next.addProperty("pending",true)
        }
        if(reviewHas) next.add("draft",AdminEdits.removePart(review!!,season,episode))
        // Keep saved deletion and the corresponding draft consistent in one atomic write.
        commit(next)
        return savedHas
    }
    fun draft(): JsonObject?=state.get("draft")?.takeIf { it.isJsonObject }?.asJsonObject?.deepCopy()
    fun persistDraft(draft: JsonObject?,verifiedPaths:Set<String> = emptySet()) {
        val next=if(draft!=null && verifiedPaths.isNotEmpty()) RetiredMetadata.verified(state,draft,verifiedPaths) else state.deepCopy()
        if(draft==null) next.remove("draft") else next.add("draft",draft.deepCopy());commit(next)
    }
    fun stageRecovery(draft: JsonObject, backup: JsonObject,verifiedPaths:Set<String> = emptySet()) {
        check(writable) {"El catálogo está en solo lectura."}
        val next=if(verifiedPaths.isEmpty()) state.deepCopy() else RetiredMetadata.verified(state,draft,verifiedPaths);next.add("draft",draft.deepCopy());next.add("metadata_undo",backup.deepCopy());commit(next)
    }
    fun canUndoRecovery(item: JsonObject): Boolean {
        val backup=state.get("metadata_undo")?.takeIf {it.isJsonObject}?.asJsonObject ?: return false
        val before=backup.getAsJsonObject("before")
        val same=if(item.text("id").isNotBlank()) before.text("id")==item.text("id") else
            item.text("_admin_metadata_recovery").isNotBlank() && item.text("_admin_metadata_recovery")==before.text("_admin_metadata_recovery")
        return same && runCatching {MetadataRecovery.undo(item,backup);true}.getOrDefault(false)
    }
    fun undoRecovery(draft: JsonObject): JsonObject {
        check(writable) {"El catálogo está en solo lectura."}
        val backup=state.get("metadata_undo")?.takeIf {it.isJsonObject}?.asJsonObject ?: error("No hay una recuperación para revertir.")
        val restored=MetadataRecovery.undo(draft,backup)
        // Keep the backup if the user later discards this reverted draft before saving.
        val next=state.deepCopy();next.add("draft",restored.deepCopy());commit(next)
        return this.draft() ?: error("No se pudo conservar la revisión restaurada.")
    }
    fun publish(remote: AdminRemote): String {
        check(writable) { "Actualizá y validá el catálogo remoto antes de sincronizar." }
        if(pending) {
            val current=catalog ?: error("Sin catálogo.")
            val base=state.get("base")?.takeIf {it.isJsonObject}?.asJsonObject ?: error("Actualizá el catálogo antes de publicar.")
            val latest=remote.download()
            check(CatalogRules.fingerprint(latest)==CatalogRules.fingerprint(base)) {"El catálogo remoto cambió desde la descarga. Se conservaron los pendientes; no se sobrescribieron títulos ni campos remotos. Revisá desde el dispositivo que publicó el cambio."}
            val publication=CatalogRules.publicCopy(current)
            CatalogRules.parse(publication.toString())
            remote.publish(publication)
            // Only a confirmed catalog publication clears catalog pending work.
            val next=state.deepCopy();next.add("base",publication.deepCopy());next.addProperty("pending",false);next.addProperty("last_sync",now());commit(next)
        }
        return "Sincronización confirmada."
    }
}
