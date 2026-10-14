package ar.com.miflix.client

import android.graphics.Rect
import android.util.Log
import android.view.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import android.webkit.*
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

/** Official IFrame API in the OS WebView; separate from Mobile and the Telegram player. */
@Composable
internal fun TvIntegratedTrailer(target: YouTubeTrailer, title: String,
    onExternal: () -> Unit, externalUnavailable: Boolean, onClose: () -> Unit) {
    var web by remember(target) { mutableStateOf<WebView?>(null) }
    var ready by remember(target) { mutableStateOf(false) }
    var playing by remember(target) { mutableStateOf(false) }
    var playerState by remember(target) { mutableIntStateOf(-1) }
    var playRequest by remember(target) { mutableIntStateOf(0) }
    val playFocus = remember { FocusRequester() }
    val externalFocus = remember { FocusRequester() }
    var problem by remember(target) { mutableStateOf<String?>(null) }
    val closeFocus = remember { FocusRequester() }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(target) {
        delay(20_000)
        if (!ready && problem == null) problem = "El tráiler no respondió. Revisá la conexión o probá abrirlo externamente."
    }
    LaunchedEffect(ready) { if (ready && problem == null) { withFrameNanos { }; playFocus.requestFocus() } }
    LaunchedEffect(playRequest) {
        if (playRequest > 0) {
            delay(15_000)
            if (tvTrailerPlaybackTimedOut(true, playerState, problem != null)) {
                Log.w("MiFlixTvTrailer", "PLAYBACK_TIMEOUT state=$playerState")
                problem = "El tráiler no comenzó a reproducirse. Probá abrirlo externamente o actualizar el WebView del dispositivo."
            }
        }
    }
    LaunchedEffect(problem) {
        if (problem != null) {
            web?.evaluateJavascript("tvPause()", null)
            withFrameNanos { }; externalFocus.requestFocus()
        }
    }
    DisposableEffect(owner, web) {
        val view = web
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { view?.evaluateJavascript("tvPause()", null); view?.onPause() }
            if (event == Lifecycle.Event.ON_RESUME) view?.onResume()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { withFrameNanos { }; closeFocus.requestFocus() }
        // Sibling rows, never overlays: branding/controls/video remain unobscured.
        Column(Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tráiler · $title", color = Color.White, maxLines = 1)
            AndroidView(modifier = Modifier.fillMaxWidth().weight(1f).onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) onClose()
                    true
                } else if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) closeFocus.requestFocus()
                    true
                } else false
            }, factory = { context ->
                FrameLayout(context).apply {
                    try {
                        val playerView = WebView(context)
                        addView(playerView, FrameLayout.LayoutParams(-1, -1))
                        web = playerView
                        playerView.apply {
                            setBackgroundColor(android.graphics.Color.BLACK)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.mediaPlaybackRequiresUserGesture = false
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                                    val value = message.message()
                                    when {
                                        value == "MIFLIX_TV_READY" -> ready = true
                                        value.startsWith("MIFLIX_TV_STATE:") -> {
                                            playerState = value.substringAfter(':').toIntOrNull() ?: -1
                                            playing = playerState == 1
                                            Log.d("MiFlixTvTrailer", "PLAYER_STATE state=$playerState")
                                        }
                                        value == "MIFLIX_TV_PLAY_REQUEST" -> { playerState = -1; playRequest++ }
                                        value == "MIFLIX_TV_SCRIPT_ERROR" -> problem = "No se pudo cargar el reproductor de YouTube. Revisá la conexión o abrilo externamente."
                                        value.startsWith("MIFLIX_TV_ERROR:") -> {
                                            val code = value.substringAfter(':').toIntOrNull() ?: 0
                                            Log.w("MiFlixTvTrailer", "YOUTUBE_ERROR code=$code")
                                            problem = tvTrailerError(code)
                                        }
                                        value == "MIFLIX_TV_BLOCKED" -> problem = "El dispositivo bloqueó el inicio. Podés usar los controles de YouTube o abrirlo externamente."
                                        else -> return false
                                    }
                                    return true
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                    request.isForMainFrame // External navigation is explicit via the native button.
                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                    // URLs/messages are deliberately not logged.
                                    Log.w("MiFlixTvTrailer", "WEB_RESOURCE_ERROR code=${error.errorCode} main=${request.isForMainFrame}")
                                    if (request.isForMainFrame) problem = "No se pudo cargar el reproductor integrado."
                                }
                                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                    Log.w("MiFlixTvTrailer", "WEB_HTTP_ERROR status=${response.statusCode} main=${request.isForMainFrame}")
                                    if (request.isForMainFrame) problem = "YouTube no pudo cargar el tráiler. Probá abrirlo externamente."
                                }
                                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                    web = null
                                    (view.parent as? FrameLayout)?.removeView(view)
                                    view.destroy()
                                    ready = false
                                    problem = "El reproductor del dispositivo se cerró. Podés volver a intentarlo o abrir el tráiler externamente."
                                    return true
                                }
                            }
                            loadDataWithBaseURL(tvTrailerOrigin(BuildConfig.APPLICATION_ID) + "/",
                                tvTrailerHtml(target, BuildConfig.APPLICATION_ID), "text/html", "UTF-8", null)
                        }
                    } catch (_: Exception) {
                        problem = "Este dispositivo no tiene un WebView disponible para mostrar el tráiler."
                    }
                }
            }, onRelease = { frame ->
                val view = frame.getChildAt(0) as? WebView
                web = null
                view?.apply { stopLoading(); onPause(); loadUrl("about:blank") }
                frame.removeAllViews()
                view?.destroy()
            })
            Text(problem ?: if (ready) "Presioná Reproducir para iniciar el tráiler" else "Cargando tráiler…", color = Color.LightGray, maxLines = 2)
            if (externalUnavailable) Text("No hay una aplicación externa disponible.", color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvAction(if (playing) "Pausar" else "Reproducir", Modifier.focusRequester(playFocus), enabled = ready && problem == null) {
                    val view = web
                    val visible = Rect()
                    Log.d("MiFlixTvTrailer", "PLAY_REQUEST ready=$ready width=${view?.width} height=${view?.height} hardware=${view?.isHardwareAccelerated}")
                    if (view != null && view.isShown && view.getGlobalVisibleRect(visible) &&
                        visible.width() >= 200 && visible.height() >= 200 &&
                        visible.width().toLong() * visible.height() * 2 > view.width.toLong() * view.height)
                        view.evaluateJavascript("tvToggle()", null)
                    else problem = "El área de video no está visible. Cerrá y volvé a abrir el tráiler."
                }
                TvAction("Controles de YouTube", enabled = ready) { web?.requestFocus() }
                TvAction("Abrir externamente", Modifier.focusRequester(externalFocus)) { web?.evaluateJavascript("tvPause()", null); onExternal() }
                TvAction("Cerrar", Modifier.focusRequester(closeFocus), onClick = onClose)
            }
        }
    }
}
