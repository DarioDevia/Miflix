package ar.com.miflix.client

import android.media.MediaCodecList
import android.util.Log

/** Registry evidence only: listing an AC3 decoder does not prove a Format is playable. */
internal fun logTvAc3Decoders() {
    try {
        val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { codec ->
            !codec.isEncoder && codec.supportedTypes.any { it.equals("audio/ac3", ignoreCase = true) }
        }
        Log.d("MiFlixTracks", "AC3_PLATFORM_DECODERS count=${decoders.size}")
        decoders.forEach { Log.d("MiFlixTracks", "AC3_PLATFORM_DECODER name=${it.name}") }
        Log.d("MiFlixTracks", "AC3_SOFTWARE_EXTENSION packaged=false")
    } catch (error: Exception) {
        Log.w("MiFlixTracks", "AC3_CODEC_QUERY_FAILED class=${error.javaClass.simpleName}")
    }
}
