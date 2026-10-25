package ar.com.miflix.client

import android.content.Context
import java.security.MessageDigest

internal data class SavedProgress(val positionMs: Long, val durationMs: Long, val updatedAtMs: Long)

internal fun formatProgressTime(positionMs: Long): String {
    val seconds = positionMs.coerceAtLeast(0L) / 1_000L
    val minutes = seconds / 60L
    return if (minutes >= 60L) "%d:%02d:%02d".format(minutes / 60L,
        minutes % 60L, seconds % 60L)
    else "%d:%02d".format(minutes, seconds % 60L)
}

internal object ProgressKeys {
    fun title(title: Title): String {
        val matchingEpisode = title.temporadas.orEmpty().asSequence()
            .flatMap { season -> season.episodios.orEmpty().asSequence().map { season to it } }
            .firstOrNull { (_, episode) ->
                !title.telegramUrl.isNullOrBlank() && episode.telegramUrl == title.telegramUrl
            }
        return if (matchingEpisode != null)
            episode(title, matchingEpisode.first, matchingEpisode.second)
        else "title:${title.id}"
    }

    fun episode(title: Title, season: Season, episode: Episode): String =
        if (episode.id?.startsWith("${title.id}-publicacion-") == true && !episode.telegramUrl.isNullOrBlank()) {
            "publication:${title.id}:${digest(episode.telegramUrl)}"
        } else if (episode.tmdbId != null && episode.tmdbId > 0L) {
            "episode:${title.id}:${season.numero}:tmdb:${episode.tmdbId}"
        } else if (!episode.id.isNullOrBlank()) {
            // Conserva las claves persistidas por las versiones anteriores.
            "episode:${title.id}:${season.numero}:${episode.id}"
        } else if (!episode.telegramUrl.isNullOrBlank()) {
            "episode:${title.id}:${season.numero}:link:${digest(episode.telegramUrl)}"
        } else "episode:${title.id}:${season.numero}:number:${episode.numero}:${episode.titulo.orEmpty()}"

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

internal object ProgressRules {
    const val MIN_POSITION_MS = 30_000L
    const val SAVE_INTERVAL_MS = 15_000L

    fun isComplete(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L) return false
        val remaining = durationMs - positionMs
        val finishMargin = minOf(300_000L, durationMs / 20L)
        return remaining <= finishMargin
    }

    fun resumable(progress: SavedProgress?): Long? {
        if (progress == null) return null
        return progress.positionMs.takeIf {
            it >= MIN_POSITION_MS && !isComplete(it, progress.durationMs)
        }
    }
}

internal interface ProgressStorage {
    fun read(key: String): SavedProgress?
    fun write(key: String, progress: SavedProgress)
    fun remove(key: String)
}

internal class PlaybackProgressStore(private val storage: ProgressStorage) {
    fun resumableProgress(key: String): SavedProgress? = storage.read(key)
        ?.takeIf { ProgressRules.resumable(it) != null }

    fun resumablePosition(key: String): Long? {
        val saved = storage.read(key)
        val position = ProgressRules.resumable(saved)
        if (saved != null && position == null) storage.remove(key)
        return position
    }

    fun save(key: String, positionMs: Long, durationMs: Long, nowMs: Long) {
        val duration = durationMs.coerceAtLeast(0L)
        if (ProgressRules.isComplete(positionMs, duration)) {
            clear(key)
        } else if (positionMs >= ProgressRules.MIN_POSITION_MS) {
            storage.write(key, SavedProgress(positionMs, duration, nowMs))
        }
    }

    fun finish(key: String, positionMs: Long, durationMs: Long, nowMs: Long) {
        if (positionMs < ProgressRules.MIN_POSITION_MS) clear(key)
        else save(key, positionMs, durationMs, nowMs)
    }

    fun clear(key: String) = storage.remove(key)
}

internal class SharedPreferencesProgressStorage(context: Context) : ProgressStorage {
    private val prefs = context.getSharedPreferences("miflix_playback_progress", Context.MODE_PRIVATE)
    private fun prefix(key: String) = "video." + MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    override fun read(key: String): SavedProgress? {
        val prefix = prefix(key)
        if (!prefs.contains("$prefix.position")) return null
        return SavedProgress(prefs.getLong("$prefix.position", 0L),
            prefs.getLong("$prefix.duration", 0L), prefs.getLong("$prefix.updated", 0L))
    }

    override fun write(key: String, progress: SavedProgress) {
        val prefix = prefix(key)
        prefs.edit().putLong("$prefix.position", progress.positionMs)
            .putLong("$prefix.duration", progress.durationMs)
            .putLong("$prefix.updated", progress.updatedAtMs).commit()
    }

    override fun remove(key: String) {
        val prefix = prefix(key)
        prefs.edit().remove("$prefix.position").remove("$prefix.duration")
            .remove("$prefix.updated").commit()
    }
}
