package ar.com.miflix.client

import android.net.Uri
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
    private val ranges = LinkedHashMap<Long, ByteArray>(16, .75f, true)
    private var disposed = false

    @Synchronized
    fun read(position: Long, output: ByteArray, offset: Int, length: Int): Int {
        if (disposed) throw IOException("Se cerró el video.")
        val start = position / CHUNK_BYTES * CHUNK_BYTES
        val chunk = ranges[start] ?: try {
            val count = minOf(CHUNK_BYTES.toLong(), size - start).toInt()
            runBlocking { session.downloadRange(fileId, start, count) }.also {
                ranges[start] = it
                while (ranges.size > MAX_CHUNKS) ranges.remove(ranges.keys.first())
            }
        } catch (failure: Exception) {
            throw IOException("No se pudo cargar el video desde Telegram: ${failure.message}", failure)
        }
        val within = (position - start).toInt()
        val amount = minOf(length, chunk.size - within)
        System.arraycopy(chunk, within, output, offset, amount)
        return amount
    }

    @Synchronized
    fun clear() {
        disposed = true
        ranges.clear()
    }

    fun factory(): DataSource.Factory = DataSource.Factory { TelegramRangeDataSource(this) }

    companion object {
        const val CHUNK_BYTES = 1024 * 1024
        const val MAX_CHUNKS = 8 // At most 8 MiB of video in app memory.
    }
}

/** Media3 calls open again at a new DataSpec.position when playback seeks. */
private class TelegramRangeDataSource(private val video: TelegramVideo) : BaseDataSource(true) {
    private var uri: Uri? = null
    private var position = 0L
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
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
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val count = video.read(position, buffer, offset, minOf(length.toLong(), remaining).toInt())
        position += count
        remaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
