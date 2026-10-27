package ar.com.miflix.admin

import com.google.gson.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** One session per ViewModel; tokens stay in memory and never enter the catalog or configuration file. */
class TvdbApi(private val credentials: ()->TvdbCredentials?,
    private val client: OkHttpClient=OkHttpClient.Builder().callTimeout(25,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build(),
    private val base: String="https://api4.thetvdb.com/v4",
    private val clock: ()->Long={System.currentTimeMillis()},private val spacingMs: Long=350) {
    private val generation=AtomicInteger()
    private val lock=Any()
    private var bearer: String?=null
    private var expires: Long?=null
    private var nextRequest=0L
    private var blockedUntil=0L
    fun reset() {generation.incrementAndGet();synchronized(lock) {bearer=null;expires=null};client.dispatcher.cancelAll()}
    fun cancel() {generation.incrementAndGet();client.dispatcher.cancelAll()}
    private fun active(epoch: Int) {check(epoch==generation.get()) {"Consulta TheTVDB cancelada."}}
    private fun pace(epoch: Int) {
        val wait=synchronized(lock) {
            check(clock()>=blockedUntil) {"TheTVDB pidió esperar antes de consultar otra vez (HTTP 429)."}
            val slot=maxOf(clock(),nextRequest);nextRequest=slot+spacingMs;(slot-clock()).coerceAtLeast(0)
        }
        var remaining=wait
        while(remaining>0) {active(epoch);val part=minOf(remaining,100);Thread.sleep(part);remaining-=part}
        active(epoch)
    }
    private class Reply(val code: Int,val body: JsonObject?)
    private fun request(path: String,token: String?,body: JsonObject?,epoch: Int): Reply {
        active(epoch);pace(epoch)
        val req=Request.Builder().url(base+path).header("Accept","application/json").header("User-Agent","MiFlix-Admin-Android/0.1.7")
        if(token!=null) req.header("Authorization","Bearer $token")
        if(body!=null) req.post(body.toString().toRequestBody("application/json".toMediaType()))
        try {
            return client.newCall(req.build()).execute().use {r ->
                active(epoch)
                if(r.code==429) {
                    val seconds=r.header("Retry-After")?.toLongOrNull() ?: runCatching {
                        (java.time.ZonedDateTime.parse(r.header("Retry-After"),java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-clock())/1000
                    }.getOrNull() ?: 60
                    synchronized(lock) {blockedUntil=clock()+seconds.coerceIn(1,86400)*1000}
                    error("TheTVDB: límite de solicitudes (HTTP 429). Esperá y reintentá; no se guardaron datos.")
                }
                if(r.code in setOf(401,403,404)) return@use Reply(r.code,null)
                check(r.isSuccessful) {"TheTVDB respondió HTTP ${r.code}. Reintentá más tarde."}
                val bytes=r.body?.byteStream()?.use {stream ->
                    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(out.size()<=5_000_000) {val n=stream.read(buffer,0,minOf(buffer.size,5_000_001-out.size()));if(n<0) break;out.write(buffer,0,n)}
                    out.toByteArray()
                } ?: error("TheTVDB: respuesta vacía.")
                check(bytes.size<=5_000_000) {"TheTVDB: respuesta demasiado grande."}
                val json=try {JsonParser.parseString(String(bytes,Charsets.UTF_8)).asJsonObject} catch(_:Exception) {error("TheTVDB: JSON inválido.")}
                check(json.text("status")=="success" && json.has("data")) {"TheTVDB: respuesta incompleta o solicitud rechazada."}
                Reply(r.code,json)
            }
        } catch(e:IllegalStateException) {throw e}
        catch(_:java.net.SocketTimeoutException) {error("TheTVDB: tiempo de espera agotado. Revisá Internet y reintentá.")}
        catch(_:java.io.InterruptedIOException) {active(epoch);error("TheTVDB: tiempo de espera agotado o consulta interrumpida.")}
        catch(_:Exception) {active(epoch);error("No se pudo conectar con TheTVDB. Revisá Internet y reintentá.")}
    }
    private fun token(epoch: Int): String {
        active(epoch)
        synchronized(lock) {bearer?.takeIf {expires==null || clock()+30_000<expires!!}?.let {return it}}
        val c=credentials()?.normalized() ?: error("TheTVDB no está configurado. Ingresá tu API Key en Configuración → APIs y proveedores.")
        val body=JsonObject().apply {addProperty("apikey",c.apiKey);if(c.pin.isNotEmpty()) addProperty("pin",c.pin)}
        val reply=request("/login",null,body,epoch)
        check(reply.code!=401) {"TheTVDB: API Key o PIN inválidos (HTTP 401). Revisá la modalidad de tu clave."}
        check(reply.code!=403) {"TheTVDB: acceso denegado (HTTP 403). Revisá los permisos de tu proyecto."}
        val value=reply.body?.get("data")?.takeIf {it.isJsonObject}?.asJsonObject?.text("token").orEmpty()
        check(value.isNotBlank() && value.length<=16384 && value.none {it.isWhitespace()}) {"TheTVDB: autenticación sin token válido."}
        // exp is an optimization only; 401 is authoritative. No hard-coded month/TTL assumption.
        val exp=runCatching {
            val claim=JsonParser.parseString(String(Base64.getUrlDecoder().decode(value.split('.')[1]),Charsets.UTF_8)).asJsonObject
            claim.number("exp")?.takeIf {it>0 && it<Long.MAX_VALUE/1000}?.times(1000)
        }.getOrNull()
        synchronized(lock) {active(epoch);bearer=value;expires=exp}
        return value
    }
    fun get(path: String,missing: Boolean=false): JsonObject? {
        require(path.startsWith('/') && !path.contains("..") && !path.contains('#')) {"Ruta TheTVDB inválida."}
        val epoch=generation.get();var t=token(epoch)
        var r=request(path,t,null,epoch)
        if(r.code==401) {
            synchronized(lock) {bearer=null;expires=null}
            t=token(epoch);r=request(path,t,null,epoch)
        }
        active(epoch)
        if(r.code==404 && missing) return null
        check(r.code!=401) {synchronized(lock) {bearer=null;expires=null};"TheTVDB: sesión rechazada (HTTP 401). Revisá la clave y los permisos."}
        check(r.code!=403) {"TheTVDB: acceso denegado (HTTP 403). Tu clave no autoriza esta consulta."}
        check(r.code!=404) {"TheTVDB: no hay datos para este contenido (HTTP 404)."}
        return r.body ?: error("TheTVDB: respuesta incompleta.")
    }
    fun testConnection() {val data=get("/languages")!!.get("data");check(data.isJsonArray && data.asJsonArray.size()>0) {"TheTVDB: autenticación aceptada, pero no devolvió los idiomas disponibles."}}
}
