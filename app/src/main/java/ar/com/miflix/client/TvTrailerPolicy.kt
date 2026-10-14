package ar.com.miflix.client

import java.net.URI

internal fun tvTrailerExternalUrl(raw: String?): String? {
    val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    return value.takeIf { uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() &&
        uri.userInfo == null && (uri.port == -1 || uri.port == 443) }
}
internal fun tvTrailerError(code: Int): String = when (code) {
    2 -> "El enlace del tráiler no contiene un identificador válido de YouTube."
    101, 150 -> "El propietario no permite reproducir este video dentro de otras aplicaciones."
    100 -> "Este video no está disponible o es privado."
    153 -> "YouTube no pudo identificar esta aplicación para la reproducción integrada."
    5 -> "El WebView de este dispositivo no pudo reproducir el video."
    else -> "No se pudo cargar este tráiler dentro de MiFlix."
}
internal fun tvTrailerOrigin(applicationId: String): String {
    require(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(applicationId))
    return "https://$applicationId"
}
internal fun tvTrailerHtml(target: YouTubeTrailer, applicationId: String): String {
    require(Regex("[A-Za-z0-9_-]{11}").matches(target.videoId))
    require(target.startSeconds in 0..86_400)
    val origin = tvTrailerOrigin(applicationId)
    return """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="referrer" content="strict-origin-when-cross-origin">
<style>html,body,#player,iframe{margin:0;width:100%;height:100%;border:0;background:#000;overflow:hidden;}</style>
</head><body><div id="player"></div><script>
var player;

function fitPlayer(){if(player && player.setSize) player.setSize(Math.max(200,window.innerWidth),Math.max(200,window.innerHeight));}
function onYouTubeIframeAPIReady(){
 try { player=new YT.Player('player',{width:Math.max(200,window.innerWidth),height:Math.max(200,window.innerHeight),videoId:'${target.videoId}',
  playerVars:{autoplay:0,controls:1,playsinline:1,fs:0,rel:0,origin:'$origin',start:${target.startSeconds}},
  events:{onReady:function(){fitPlayer();console.log('MIFLIX_TV_READY');},
   onStateChange:function(e){console.log('MIFLIX_TV_STATE:'+e.data);},
   onError:function(e){console.log('MIFLIX_TV_ERROR:'+e.data);},
   onAutoplayBlocked:function(){console.log('MIFLIX_TV_BLOCKED');}}});
 } catch(e) {console.log('MIFLIX_TV_SCRIPT_ERROR');}
}
window.addEventListener('resize',fitPlayer);
function tvToggle(){if(!player || !player.getPlayerState)return;
 if(player.getPlayerState()===1)player.pauseVideo();else {console.log('MIFLIX_TV_PLAY_REQUEST');player.playVideo();}}
function tvPause(){if(player && player.pauseVideo)player.pauseVideo();}
</script><script src="https://www.youtube.com/iframe_api" onerror="console.log('MIFLIX_TV_SCRIPT_ERROR')"></script></body></html>
""".trimIndent()
}

/** API readiness is not proof that a playback request produced video. */
internal fun tvTrailerPlaybackTimedOut(requested: Boolean, state: Int, failed: Boolean): Boolean =
    requested && state !in listOf(0, 1, 2) && !failed
