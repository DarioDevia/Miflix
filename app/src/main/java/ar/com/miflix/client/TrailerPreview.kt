package ar.com.miflix.client

import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.delay

/** Vista independiente del Player de Telegram. Solo se monta en la ficha visible. */
@Composable
internal fun TrailerPreview(
    target: YouTubeTrailer,
    backdrop: @Composable () -> Unit,
    onFinished: () -> Unit
) {
    val tag = "MiFlixTrailer"
    val owner = LocalLifecycleOwner.current
    var webView by remember(target) { mutableStateOf<WebView?>(null) }
    var ready by remember(target) { mutableStateOf(false) }
    var revealRequested by remember(target) { mutableStateOf(false) }
    var playing by remember(target) { mutableStateOf(false) }
    var muted by remember(target) { mutableStateOf(true) }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                Log.d(tag, "TRAILER_BACKGROUND")
                webView?.evaluateJavascript("if (window.player) player.pauseVideo()", null)
                webView?.onPause()
                onFinished()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(target) {
        delay(15_000)
        if (!playing) {
            Log.w(tag, "TRAILER_ERROR timeout")
            onFinished()
        }
    }

    Column {
        Box(Modifier.fillMaxWidth().height(310.dp)) {
            if (!ready) backdrop()
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        val view = this
                        visibility = View.INVISIBLE
                        // Color diagnóstico: identifica la superficie si falla el video.
                        setBackgroundColor(AndroidColor.rgb(31, 55, 91))
                        addOnLayoutChangeListener { _, left, top, right, bottom,
                            oldLeft, oldTop, oldRight, oldBottom ->
                            if (right - left != oldRight - oldLeft ||
                                bottom - top != oldBottom - oldTop) {
                                Log.d(tag, "TRAILER_SIZE width=${right - left} height=${bottom - top}")
                            }
                        }
                        Log.d(tag, "TRAILER_WEBVIEW_CREATED accelerated=$isHardwareAccelerated")
                        keepScreenOn = false
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(v: WebView, url: String?) {
                                if (webView === view) Log.d(tag, "TRAILER_PAGE_READY")
                            }

                            override fun onReceivedError(v: WebView, request: WebResourceRequest,
                                error: WebResourceError) {
                                if (request.isForMainFrame && webView === view) {
                                    Log.w(tag, "TRAILER_ERROR main_frame ${error.errorCode}")
                                    onFinished()
                                }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                                if (webView !== view) return false
                                when (message.message()) {
                                    "MIFLIX_TRAILER_PLAYER_READY" -> {
                                        Log.d(tag, "TRAILER_PLAYER_READY")
                                        view.logTrailerGeometry(tag)
                                    }
                                    "MIFLIX_TRAILER_CUED" -> if (!revealRequested) {
                                        revealRequested = true
                                        Log.d(tag, "TRAILER_CUED id=${target.videoId}")
                                        view.logTrailerGeometry(tag)
                                        // Un WebView INVISIBLE debe esperar su primer dibujo antes
                                        // de pasar a VISIBLE y comenzar el video.
                                        view.postVisualStateCallback(1L,
                                            object : WebView.VisualStateCallback() {
                                                override fun onComplete(requestId: Long) {
                                                    if (webView !== view) return
                                                    view.visibility = View.VISIBLE
                                                    ready = true
                                                    view.post {
                                                        val visible = Rect()
                                                        val inViewport = view.isAttachedToWindow &&
                                                            view.width > 0 && view.height > 0 &&
                                                            view.getGlobalVisibleRect(visible) &&
                                                            visible.width().toLong() * visible.height() * 2 >
                                                            view.width.toLong() * view.height
                                                        Log.d(tag, "TRAILER_VISIBLE width=${view.width} " +
                                                            "height=${view.height} rect=${visible.width()}x" +
                                                            "${visible.height()} alpha=${view.alpha} " +
                                                            "accelerated=${view.isHardwareAccelerated} " +
                                                            "inViewport=$inViewport")
                                                        view.logTrailerGeometry(tag)
                                                        if (inViewport) {
                                                            view.evaluateJavascript("startTrailer()", null)
                                                        } else onFinished()
                                                    }
                                                }
                                            })
                                    }
                                    "MIFLIX_TRAILER_PLAYING" -> {
                                        playing = true
                                        Log.d(tag, "TRAILER_PLAYING id=${target.videoId}")
                                        view.logTrailerGeometry(tag)
                                    }
                                    "MIFLIX_TRAILER_ENDED" -> {
                                        Log.d(tag, "TRAILER_END id=${target.videoId}")
                                        onFinished()
                                    }
                                    "MIFLIX_TRAILER_ERROR", "MIFLIX_TRAILER_BLOCKED" -> {
                                        Log.w(tag, "TRAILER_ERROR ${message.message()} id=${target.videoId}")
                                        onFinished()
                                    }
                                }
                                return false
                            }
                        }
                        webView = this
                        Log.d(tag, "TRAILER_URL youtube id=${target.videoId} start=${target.startSeconds}")
                        loadDataWithBaseURL("https://ar.com.miflix.client/", trailerHtml(target),
                            "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onReset = null,
                onRelease = { view ->
                    Log.d(tag, "TRAILER_DISPOSE id=${target.videoId}")
                    if (webView === view) webView = null
                    view.keepScreenOn = false
                    view.onPause()
                    view.stopLoading()
                    view.loadUrl("about:blank")
                    view.removeAllViews()
                    view.destroy()
                    onFinished()
                },
                update = { it.keepScreenOn = false }
            )
        }
        if (ready) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    muted = !muted
                    webView?.evaluateJavascript(if (muted) "setTrailerMuted(true)"
                        else "setTrailerMuted(false)", null)
                    Log.d(tag, if (muted) "TRAILER_MUTED" else "TRAILER_UNMUTED")
                }) {
                    Icon(if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (muted) "Activar sonido" else "Silenciar",
                        tint = Color.White)
                }
                Text("Tráiler", color = Color.White)
            }
        }
    }
}

private fun WebView.logTrailerGeometry(tag: String) {
    evaluateJavascript("""(function() {
      function size(element) {
        if (!element) return 'missing';
        var rect = element.getBoundingClientRect();
        return Math.round(rect.width) + 'x' + Math.round(rect.height);
      }
      return 'viewport=' + innerWidth + 'x' + innerHeight +
        ' player=' + size(document.getElementById('player')) +
        ' iframe=' + size(document.querySelector('iframe'));
    })()""".trimIndent()) { result ->
        Log.d(tag, "TRAILER_DOM_SIZE $result")
    }
}

private fun trailerHtml(target: YouTubeTrailer): String = """
<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>
html, body { margin: 0; width: 100%; height: 100%; overflow: hidden; background: #1f375b; }
#player, iframe { display: block; width: 100%; height: 100%; border: 0; }
</style></head>
<body><div id="player"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
var player;
function onYouTubeIframeAPIReady() {
  player = new YT.Player('player', {
    width: '100%', height: '100%', videoId: '${target.videoId}',
    playerVars: {playsinline: 1, controls: 1, fs: 0, rel: 0,
                 origin: 'https://ar.com.miflix.client'},
    events: {
      onReady: function(e) {
        console.log('MIFLIX_TRAILER_PLAYER_READY');
        e.target.mute();
        e.target.cueVideoById({videoId: '${target.videoId}', startSeconds: ${target.startSeconds}});
      },
      onStateChange: function(e) {
        if (e.data === YT.PlayerState.CUED) console.log('MIFLIX_TRAILER_CUED');
        if (e.data === YT.PlayerState.PLAYING) console.log('MIFLIX_TRAILER_PLAYING');
        if (e.data === YT.PlayerState.ENDED) console.log('MIFLIX_TRAILER_ENDED');
      },
      onError: function(e) { console.log('MIFLIX_TRAILER_ERROR'); },
      onAutoplayBlocked: function(e) { console.log('MIFLIX_TRAILER_BLOCKED'); }
    }
  });
}
function startTrailer() { if (player) { player.mute(); player.playVideo(); } }
function setTrailerMuted(muted) { if (player) { if (muted) player.mute(); else player.unMute(); } }
</script></body></html>
""".trimIndent()
