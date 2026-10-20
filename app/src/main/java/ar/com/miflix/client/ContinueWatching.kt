package ar.com.miflix.client

internal data class ContinueWatchingItem(
    val title: Title,
    val key: String,
    val progress: SavedProgress,
    val seasonIndex: Int? = null,
    val episodeIndex: Int? = null
) {
    val episode: Episode? get() = seasonIndex?.let { season ->
        episodeIndex?.let { title.temporadas[season].episodios[it] }
    }
    val label: String? get() = seasonIndex?.let { season ->
        episode?.let { "T${title.temporadas[season].numero} · E${it.numero} · ${it.titulo.orEmpty()}" }
    }
    val fraction: Float? get() = progress.durationMs.takeIf { it > 0L }?.let {
        (progress.positionMs.toDouble() / it).toFloat().coerceIn(0f, 1f)
    }
}

/** Read-only projection of pending progress and watched state; never deletes persisted records. */
internal fun continueWatching(titles: List<Title>, store: PlaybackProgressStore,
    library: PersonalLibraryStore? = null): List<ContinueWatchingItem> {
    val items = mutableListOf<ContinueWatchingItem>()
    titles.forEach { title ->
        // Movie publications are alternative identities of the same movie.
        // Series/anime remain episode-scoped: a parent title mark never fans out.
        if (title.tipo == "pelicula" && library?.isTitleWatched(title) == true) return@forEach
        title.temporadas.orEmpty().forEachIndexed { seasonIndex, season ->
            season.episodios.orEmpty().forEachIndexed { episodeIndex, episode ->
                if (!episode.telegramUrl.isNullOrBlank()) {
                    val key = ProgressKeys.episode(title, season, episode)
                    if (library?.isWatched(key) != true) store.resumableProgress(key)?.let {
                        items += ContinueWatchingItem(title, key, it, seasonIndex, episodeIndex)
                    }
                }
            }
        }
        if (!title.telegramUrl.isNullOrBlank()) {
            val key = ProgressKeys.title(title)
            if (library?.isWatched(key) != true && items.none { it.key == key })
                store.resumableProgress(key)?.let {
                    items += ContinueWatchingItem(title, key, it)
                }
        }
    }
    return items.distinctBy { it.key }.sortedByDescending { it.progress.updatedAtMs }
}
