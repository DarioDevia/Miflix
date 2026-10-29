package ar.com.miflix.client

/** Read-only TV projection. Unmark can only reveal actual pending progress, never create it. */
internal fun tvContinueWatching(titles: List<Title>, store: PlaybackProgressStore,
    library: TvPersonalLibraryStore?): List<ContinueWatchingItem> = continueWatching(titles, store).filter { item ->
    library?.isWatched(item.key) != true &&
        !(item.title.tipo == "pelicula" && library?.isTitleWatched(item.title) == true)
}
