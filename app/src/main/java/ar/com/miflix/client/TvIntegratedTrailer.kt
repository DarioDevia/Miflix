package ar.com.miflix.client

import android.graphics.Rect
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
    var problem by remember(target) { mutableStateOf<String?>(null) }
    val closeFocus = remember { FocusRequester() }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(target) {
        delay(20_000)
        if (!ready && problem == null) problem = "El tráiler no respondió. Revisá la conexión o probá abrirlo externamente."
    }
    LaunchedEffect(problem) { if (problem != null) web?.evaluateJavascript("tvPause()", null) }
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
            AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { context ->
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
                                        value.startsWith("MIFLIX_TV_STATE:") -> playing = value.substringAfter(':') == "1"
                                        value.startsWith("MIFLIX_TV_ERROR:") -> problem = tvTrailerError(value.substringAfter(':').toIntOrNull() ?: 0)
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
                                    if (request.isForMainFrame) problem = "No se pudo cargar el reproductor integrado."
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
            Text(problem ?: if (ready) "Tráiler integrado de YouTube" else "Cargando tráiler…", color = Color.LightGray, maxLines = 2)
            if (externalUnavailable) Text("No hay una aplicación externa disponible.", color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvAction(if (playing) "Pausar" else "Reproducir", enabled = ready && problem == null) {
                    val view = web
                    val visible = Rect()
                    if (view != null && view.isShown && view.getGlobalVisibleRect(visible) &&
                        visible.width() >= 200 && visible.height() >= 200 &&
                        visible.width().toLong() * visible.height() * 2 > view.width.toLong() * view.height)
                        view.evaluateJavascript("tvToggle()", null)
                    else problem = "El área de video no está visible. Cerrá y volvé a abrir el tráiler."
                }
                TvAction("Controles de YouTube", enabled = ready) { web?.requestFocus() }
                TvAction("Abrir externamente") { web?.evaluateJavascript("tvPause()", null); onExternal() }
                TvAction("Cerrar", Modifier.focusRequester(closeFocus), onClick = onClose)
            }
        }
    }
}
