package ar.com.miflix.client

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.Fetcher
import coil.request.Options
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/** Native Android instrumentation: no new testing libraries and no production test hooks.
 * Run only on a clean TV emulator with a build without private Telegram configuration.
 * Artwork uses synthetic fixtures and downloaded published-image bytes; real streaming is NOT tested.
 */
class TvDetailInstrumentation : Instrumentation() {
    private var activity: Activity? = null
    private val requests = ConcurrentLinkedQueue<String>()
    private val results = mutableListOf<String>()
    private val root get() = uiAutomation.rootInActiveWindow
        ?: uiAutomation.windows.firstOrNull { it.isFocused || it.isActive }?.root
    private var stage = "start"
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val cache = File(targetContext.filesDir, "catalogo.json")
        val backup = cache.takeIf(File::isFile)?.readBytes()
        val prefs = targetContext.getSharedPreferences("miflix_client", 0)
        val oldCheck = prefs.getLong("catalog_last_check", 0L)
        val oldUrl = prefs.getString("catalog_url", null)
        val oldLoader = Coil.imageLoader(targetContext)
        var failures = 0
        try {
            uiAutomation.serviceInfo = uiAutomation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            check(BuildConfig.IS_TV && BuildConfig.TELEGRAM_API_ID == 0 && BuildConfig.TELEGRAM_API_HASH.isBlank()) {
                "Usar un emulador limpio y una compilación sin configuración privada."
            }
            Coil.setImageLoader(ImageLoader.Builder(targetContext).components {
                add(object : Fetcher.Factory<Uri> {
                    override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher {
                        requests.add(data.toString())
                        return object : Fetcher {
                            override suspend fun fetch(): DrawableResult {
                                check(data.host in listOf("artwork.test", "image.tmdb.org")) { "Unexpected image network request" }
                                check(!data.path.orEmpty().contains("broken")) { "Fixture image failure" }
                                val bitmap = if (data.host == "image.tmdb.org") {
                                    val filename = if (data.path.orEmpty().endsWith("hSOyMqkIy9nN9jypcZWHzE8Dhgu.png"))
                                        "logo.png" else "backdrop.jpg"
                                    BitmapFactory.decodeFile(File(targetContext.getExternalFilesDir(null),
                                        "tv012/$filename").path) ?: error("Supply the documented real-image fixtures")
                                } else fixtureBitmap(data.path.orEmpty())
                                return DrawableResult(BitmapDrawable(targetContext.resources, bitmap), false, DataSource.MEMORY)
                            }
                        }
                    }
                })
            }.build())
            val cases = listOf(
                "movie-logo" to movie("Logo", backdropUrl=art("background"), logoUrl=art("logo")),
                "logo-no-background" to movie("Sólo logo", logoUrl=art("logo")),
                "movie-no-logo" to movie("Sin logo", backdropUrl=art("background")),
                "movie-poster" to movie("Sólo póster", posterUrl=art("poster")),
                "movie-neutral" to movie("Sin imágenes"),
                "movie-broken" to movie("Imágenes fallidas", backdropUrl=art("broken-background"), logoUrl=art("broken-logo")),
                "movie-legacy" to movie("Fondo histórico", fondoUrl=art("background"), logoUrl=art("logo")),
                "movie-retry" to movie("Alternativa", backdropUrl=art("broken-background"), fondoUrl=art("background"), logoUrl=art("logo")),
                "logo-wide" to movie("Logo ancho", backdropUrl=art("background"), logoUrl=art("wide")),
                "logo-tall" to movie("Logo alto", backdropUrl=art("background"), logoUrl=art("tall"))
            )
            for ((name, title) in cases) runCase(name) {
                openDetail(title)
                check(focused().contains("Reproducir")) { "Initial play focus: ${focused()}" }
                val logo = title.logoUrl
                if (logo != null && !logo.contains("broken")) {
                    eventually { !hasText(title.titulo) }
                } else eventually { hasText(title.titulo) }
                screenshot(name, when(name) { "logo-wide" -> 12f; "logo-tall" -> .125f;
                    "movie-logo", "movie-legacy", "movie-retry", "logo-no-background" -> 4f; else -> null })
                key(KeyEvent.KEYCODE_BACK)
                eventually { focused().contains("Ver detalle") }
            }.also { if (!it) failures++ }
            runCase("synopsis-dpad-back-and-player-return") {
                val title = movie("Sinopsis larga", backdropUrl=art("background"))
                openDetail(title)
                key(KeyEvent.KEYCODE_DPAD_RIGHT)
                eventually { focused().contains("Sinopsis completa") }
                stage = "open synopsis dialog"
                key(KeyEvent.KEYCODE_DPAD_CENTER)
                eventually { focused().contains("Cerrar") }
                stage = "scroll synopsis"
                repeat(3) { key(KeyEvent.KEYCODE_DPAD_DOWN) }
                screenshot("synopsis-scroll", null)
                stage = "close synopsis with Back"
                key(KeyEvent.KEYCODE_BACK)
                eventually { focused().contains("Sinopsis completa") }
                stage = "focus play after synopsis"
                key(KeyEvent.KEYCODE_DPAD_LEFT)
                eventually { focused().contains("Reproducir") }
                key(KeyEvent.KEYCODE_DPAD_CENTER)
                eventually { hasText("Conectar Telegram") }
                key(KeyEvent.KEYCODE_BACK)
                eventually { focused().contains("Reproducir") }
            }.also { if (!it) failures++ }
            for (type in listOf("serie", "anime")) runCase("$type-season-episode-player-return") {
                val title = movie("Episodios $type").copy(tipo=type, telegramUrl=null, temporadas=listOf(
                    Season(numero=1, episodios=listOf(Episode(id="e1",numero=1,titulo="Primer destino",telegramUrl="https://t.me/c/1/2"))),
                    Season(numero=2, episodios=listOf(Episode(id="e2",numero=1,titulo="Segundo destino",telegramUrl="https://t.me/c/1/3")))))
                openDetail(title)
                eventually { focused().contains("Elegir episodio") }
                key(KeyEvent.KEYCODE_DPAD_CENTER)
                eventually { focused().contains("Temporada 1") }
                key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER)
                eventually { hasText("E1 · Segundo destino") }
                key(KeyEvent.KEYCODE_DPAD_DOWN)
                eventually { focused().contains("Segundo destino") }
                key(KeyEvent.KEYCODE_DPAD_CENTER)
                eventually { hasText("Conectar Telegram") }
                key(KeyEvent.KEYCODE_BACK)
                eventually { focused().contains("Elegir episodio") }
                screenshot("$type-return", null)
            }.also { if (!it) failures++ }
            runCase("reported-film-published-artwork") {
                val title = CatalogRepository.parseCatalog("""{"schema_version":"1.2.0","items":[{
                    "id":"reported-film", "tipo":"pelicula", "titulo":"Los Ilusionistas: Nada es lo que Parece",
                    "telegram_url":"https://t.me/c/1/1", "year":2013,
                    "sinopsis":"Un equipo de ilusionistas sorprende al público con una serie de espectáculos.",
                    "fondo_url":"https://image.tmdb.org/t/p/original/xEY0MV2jSQBz9iOJfCFvLTiPGMA.jpg",
                    "backdrop_url":"https://image.tmdb.org/t/p/original/xEY0MV2jSQBz9iOJfCFvLTiPGMA.jpg",
                    "logo_url":"https://image.tmdb.org/t/p/original/hSOyMqkIy9nN9jypcZWHzE8Dhgu.png"
                }]}""").items.single()
                openDetail(title)
                eventually { !hasText(title.titulo) }
                screenshot("reported-film-real-published-images", null)
            }.also { if (!it) failures++ }
            for (type in listOf("pelicula", "serie", "anime")) runCase("player-controls-$type") {
                playerControls(type, art("logo"))
            }.also { if (!it) failures++ }
            runCase("player-controls-no-logo") { playerControls("pelicula", null) }
                .also { if (!it) failures++ }
            check(requests.none { it.contains("api.themoviedb") || it.contains("api.tmdb") })
            check(requests.all { Uri.parse(it).host in listOf("artwork.test", "image.tmdb.org") })
            results += "PASS artwork requests confined to synthetic/published-image fixtures; no API requests"
        } catch (error: Throwable) { failures++; results += "FAIL runner: ${error.stackTraceToString()}" }
        finally {
            runOnMainSync { activity?.finish() }
            if (backup == null) cache.delete() else cache.writeBytes(backup)
            prefs.edit().putLong("catalog_last_check", oldCheck).apply {
                if (oldUrl == null) remove("catalog_url") else putString("catalog_url",oldUrl)
            }.commit()
            Coil.setImageLoader(oldLoader)
        }
        val output = Bundle().apply {
            putString("stream", results.joinToString("\n")+"\nFailures: $failures\n")
            putInt("failures", failures)
        }
        finish(if (failures == 0) Activity.RESULT_OK else Activity.RESULT_CANCELED, output)
    }
    private fun playerControls(type: String, logo: String?) {
        openDetail(movie("Control $type"))
        var back = false
        var seek = 0L
        runOnMainSync {
            (activity as ComponentActivity).setContent {
                MaterialTheme {
                    var playing by remember { mutableStateOf(true) }
                    var visible by remember { mutableStateOf(true) }
                    var position by remember { mutableLongStateOf(60_000L) }
                    BackHandler { back = true }
                    Box(Modifier.fillMaxSize().background(ComposeColor(0xFF15202D))) {
                        TvPlaybackControls("Episodio de prueba", playing, position, 600_000L, visible,
                            { visible = it }, { playing = !playing }, { playing = true }, { playing = false },
                            { seek += it; position += it }, false, {}, "Título principal $type", logo)
                    }
                }
            }
        }
        eventually { focused().contains("Pausar") }
        check(!hasText("Pausar") && !hasText("Reproducir") && !hasText("Ocultar"))
        screenshot("player-$type-initial-${logo != null}", null)
        eventually { !labels(root).contains("Pausar") }
        screenshot("player-$type-clean-${logo != null}", null)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        eventually { focused().contains("Pausar") }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        eventually { focused().contains("Reproducir") }
        if (logo == null) eventually { hasText("Título principal $type") }
        else eventually { labels(root).contains("Logo de Título principal") }
        screenshot("player-$type-paused-${logo != null}", if (logo == null) null else 4f)
        SystemClock.sleep(4_500)
        check(focused().contains("Reproducir")) { "Paused controls must remain visible" }
        key(KeyEvent.KEYCODE_DPAD_RIGHT); check(seek == 10_000L)
        key(KeyEvent.KEYCODE_DPAD_LEFT); check(seek == 0L)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        eventually { focused().contains("Pausar") }
        check(!labels(root).contains("Logo de Título principal"))
        screenshot("player-$type-resumed-${logo != null}", null)
        eventually { !labels(root).contains("Pausar") }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        eventually { focused().contains("Pausar") }
        key(KeyEvent.KEYCODE_BACK)
        eventually { !labels(root).contains("Pausar") }
        check(!back)
        key(KeyEvent.KEYCODE_BACK)
        eventually { back }
    }

    private fun art(name: String) = "https://artwork.test/$name.png"
    private fun movie(name: String, backdropUrl: String?=null, fondoUrl: String?=null,
        logoUrl: String?=null, posterUrl: String?=null) = Title(id="tv-detail-test",tipo="pelicula",titulo=name,
        year=2025,puntuacion=7.5,generos=listOf("Drama"),
        sinopsis=("Una historia de prueba para comprobar legibilidad, control remoto y desplazamiento. ").repeat(24),
        telegramUrl="https://t.me/c/1/1",backdropUrl=backdropUrl,fondoUrl=fondoUrl,logoUrl=logoUrl,posterUrl=posterUrl)
    private fun openDetail(title: Title) {
        runOnMainSync { activity?.finish() }; waitForIdleSync()
        File(targetContext.filesDir,"catalogo.json").writeText(Gson().toJson(Catalog("1.2.0",items=listOf(title))))
        targetContext.getSharedPreferences("miflix_client",0).edit()
            .putLong("catalog_last_check",System.currentTimeMillis()).remove("catalog_url").commit()
        activity = startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        eventually { hasText("Conectar Telegram") }; key(KeyEvent.KEYCODE_BACK)
        eventually { focused().contains("Ver detalle") }; key(KeyEvent.KEYCODE_DPAD_CENTER)
        eventually { focused().contains("Reproducir") || focused().contains("Elegir episodio") }
    }
    private fun key(code: Int) { sendKeyDownUpSync(code); waitForIdleSync(); SystemClock.sleep(250) }
    private fun eventually(condition: () -> Boolean) {
        val end=SystemClock.uptimeMillis()+15_000
        while(SystemClock.uptimeMillis()<end) { waitForIdleSync(); if(condition())return; SystemClock.sleep(150) }
        error("UI timeout; focused=${focused()}; tree=${labels(root)}")
    }
    private fun labels(node: AccessibilityNodeInfo?): String {
        if(node==null)return ""
        return listOfNotNull(node.text?.toString(),node.contentDescription?.toString()).joinToString(" ")+" "+
            (0 until node.childCount).joinToString(" ") { labels(node.getChild(it)) }
    }
    private fun focused()=labels(root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT))
    private fun hasText(text: String): Boolean {
        fun contains(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.text?.toString() == text) return true
            return (0 until node.childCount).any { contains(node.getChild(it)) }
        }
        return contains(root)
    }
    private fun runCase(name: String, test: () -> Unit): Boolean = try {
        stage = name
        test(); record("PASS $name"); true
    } catch(error: Throwable) {
        runCatching { screenshot("$name-failure", null) }
        record("FAIL $name ($stage): ${error.message}"); false
    }
    private fun record(message: String) {
        results += message
        sendStatus(0, Bundle().apply { putString("stream", message + "\n") })
    }
    private fun fixtureBitmap(path: String): Bitmap {
        val (width,height)=when { path.contains("wide")->960 to 80; path.contains("tall")->80 to 640;
            path.contains("logo")->600 to 150; path.contains("poster")->342 to 512; else->1280 to 720 }
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);val paint=Paint()
        if(path.contains("logo")||path.contains("wide")||path.contains("tall")) {
            paint.color=Color.rgb(40,239,121)
            canvas.drawRect(width*.1f,height*.1f,width*.9f,height*.9f,paint)
            check(Color.alpha(bitmap.getPixel(0,0))==0)
        } else { canvas.drawColor(if(path.contains("poster")) Color.rgb(30,65,200) else Color.rgb(56,87,112)) }
        return bitmap
    }
    private fun screenshot(name: String, logoRatio: Float?) {
        SystemClock.sleep(500)
        val bitmap=uiAutomation.takeScreenshot() ?: error("No screenshot")
        val dir=File(targetContext.getExternalFilesDir(null),"tv-detail-tests").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        if(logoRatio!=null) {
            var minX=bitmap.width;var minY=bitmap.height;var maxX=-1;var maxY=-1
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            for(y in 0 until bitmap.height)for(x in 0 until bitmap.width) {
                val pixel=pixels[y * bitmap.width + x]
                if(Color.green(pixel)>215&&Color.red(pixel)<85&&Color.blue(pixel) in 85..155) {
                    minX=minOf(minX,x);maxX=maxOf(maxX,x);minY=minOf(minY,y);maxY=maxOf(maxY,y)
                }
            }
            check(maxX>=minX&&maxY>=minY) { "Logo did not render" }
            val ratio=(maxX-minX+1).toFloat()/(maxY-minY+1)
            check(kotlin.math.abs(ratio-logoRatio)/logoRatio<.12f) { "Logo distorted/cropped: $ratio expected $logoRatio" }
        }
        bitmap.recycle()
    }
}
