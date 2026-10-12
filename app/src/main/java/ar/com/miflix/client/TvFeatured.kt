package ar.com.miflix.client

/** Select only an existing playable destination; keep the ordinary resume/progress flow. */
internal fun tvFeaturedTarget(title: Title, valid: (String) -> Boolean = { link ->
    runCatching { val u = java.net.URI(link.trim()); u.scheme.equals("https", true) &&
        u.host?.lowercase() in setOf("t.me", "www.t.me", "telegram.me") &&
        !u.path.isNullOrBlank() && u.path != "/" }.getOrDefault(false)
}): EpisodeNavigationTarget? {
    if (title.tipo in setOf("serie", "anime") && title.temporadas.orEmpty().isNotEmpty()) {
        title.temporadas.orEmpty().forEachIndexed { si, season ->
            season.episodios.orEmpty().forEachIndexed { ei, episode ->
                val link = episode.telegramUrl?.takeIf(valid)
                if (link != null) return EpisodeNavigationTarget(si, ei,
                    ProgressKeys.episode(title, season, episode), link,
                    episode.titulo?.takeIf(String::isNotBlank) ?: "Episodio ${episode.numero}",
                    "T${season.numero} · E${episode.numero}")
            }
        }
        return null
    }
    return title.telegramUrl?.takeIf(valid)?.let {
        EpisodeNavigationTarget(-1, -1, ProgressKeys.title(title), it, title.titulo, title.titulo)
    }
}
