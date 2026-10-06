package ar.com.miflix.client

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.LinkedHashMap

/** Only the currently needed Telegram ranges are kept; no whole-video download is started. */
internal class TelegramVideo(
    val fileId: Int,
    val size: Long,
    private val session: TelegramSession
) {
    private val tag = "MiFlixPlayback"
    private val ranges = LinkedHashMap<Long, ByteArray>(16, .75f, true)
    private var disposed = false

    @Synchronized
    fun read(position: Long, output: ByteArray, offset: Int, length: Int): Int {
        if (disposed) {
            Log.e(tag, "CACHE_READ_AFTER_CLEAR fileId=$fileId position=$position length=$length", Throwable("Read stack"))
            throw IOException("Se cerró el video.")
        }
        val start = position / CHUNK_BYTES * CHUNK_BYTES
        val hit = ranges.containsKey(start)
        if (!hit) Log.d(tag, "CACHE_MISS fileId=$fileId position=$position chunk=$start")
        val chunk = ranges[start] ?: try {
            val count = minOf(CHUNK_BYTES.toLong(), size - start).toInt()
            runBlocking { session.downloadRange(fileId, start, count) }.also {
                ranges[start] = it
                while (ranges.size > MAX_CHUNKS) {
                    val evicted = ranges.keys.first()
                    ranges.remove(evicted)
                    Log.d(tag, "CACHE_EVICT fileId=$fileId chunk=$evicted")
                }
            }
        } catch (failure: Exception) {
            Log.e(tag, "CACHE_LOAD_ERROR fileId=$fileId chunk=$start", failure)
            throw IOException("No se pudo cargar el video desde Telegram: ${failure.message}", failure)
        }
        val within = (position - start).toInt()
        val amount = minOf(length, chunk.size - within)
        System.arraycopy(chunk, within, output, offset, amount)
        if (position == start || amount <= 0) {
            Log.d(tag, "CACHE_READ fileId=$fileId position=$position requested=$length returned=$amount " +
                "hit=$hit cachedChunks=${ranges.size}")
        }
        return amount
    }

    @Synchronized
    fun clear() {
        Log.w(tag, "CACHE_CLEAR fileId=$fileId disposed=$disposed cachedChunks=${ranges.size}",
            Throwable("Clear caller stack"))
        disposed = true
        ranges.clear()
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun factory(): DataSource.Factory = DataSource.Factory { TelegramRangeDataSource(this) }

    companion object {
        const val CHUNK_BYTES = 1024 * 1024
        const val MAX_CHUNKS = 8 // At most 8 MiB of video in app memory.
    }
}

/** Media3 calls open again at a new DataSpec.position when playback seeks. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class TelegramRangeDataSource(private val video: TelegramVideo) : BaseDataSource(true) {
    private val tag = "MiFlixPlayback"
    private var uri: Uri? = null
    private var position = 0L
    private var remaining = 0L
    private var opened = false
    private var totalRead = 0L

    override fun open(dataSpec: DataSpec): Long {
        Log.d(tag, "DS_OPEN fileId=${video.fileId} uri=${dataSpec.uri} position=${dataSpec.position} " +
            "length=${dataSpec.length} videoSize=${video.size}")
        transferInitializing(dataSpec)
        if (dataSpec.position < 0 || dataSpec.position > video.size) {
            throw IOException("Posición fuera del video.")
        }
        uri = dataSpec.uri
        position = dataSpec.position
        remaining = video.size - position
        if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            remaining = minOf(remaining, dataSpec.length)
        }
        opened = true
        totalRead = 0
        transferStarted(dataSpec)
        Log.d(tag, "DS_OPENED position=$position remaining=$remaining")
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            Log.w(tag, "DS_ZERO_LENGTH position=$position")
            return 0
        }
        if (remaining == 0L) {
            Log.d(tag, "DS_EOF position=$position totalRead=$totalRead")
            return C.RESULT_END_OF_INPUT
        }
        val requested = minOf(length.toLong(), remaining).toInt()
        val count = try {
            video.read(position, buffer, offset, requested)
        } catch (failure: IOException) {
            Log.e(tag, "DS_READ_ERROR position=$position requested=$requested totalRead=$totalRead", failure)
            throw failure
        }
        if (count <= 0 || totalRead == 0L || totalRead / TelegramVideo.CHUNK_BYTES !=
            (totalRead + count) / TelegramVideo.CHUNK_BYTES) {
            Log.d(tag, "DS_READ position=$position requested=$requested returned=$count remaining=$remaining")
        }
        position += count
        remaining -= count
        totalRead += count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        Log.d(tag, "DS_CLOSE fileId=${video.fileId} position=$position totalRead=$totalRead opened=$opened")
        uri = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}

