package ar.com.miflix.client

import java.security.MessageDigest

internal const val HOME_PROBE_PREFIX = "miflix.homeprobe.v1:"
internal const val HOME_PROBE_CHANNEL = "miflix.homeprobe.channel.v1"
internal fun homeProbeHash(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

internal data class HomeProbeCard(val key: String, val title: String, val description: String,
    val type: String, val image: String?, val synthetic: Boolean)

/** Stable catalog order, balanced types, never Telegram links as external identity or payload. */
internal fun homeProbeCards(titles: List<Title>): List<HomeProbeCard> {
    val groups = listOf("pelicula", "serie", "anime").map { type -> titles.asSequence()
        .filter { it.tipo == type && it.id.isNotBlank() && it.titulo.isNotBlank() && tvFeaturedTarget(it) != null }
        .distinctBy { it.id }.iterator() }
    val chosen = mutableListOf<Title>()
    while (chosen.size < 5 && groups.any { it.hasNext() }) {
        for (group in groups) if (group.hasNext() && chosen.size < 5) chosen += group.next()
    }
    val real = chosen.distinctBy { it.id }.map { title -> HomeProbeCard(
        HOME_PROBE_PREFIX + homeProbeHash("catalog:" + title.id), title.titulo,
        title.sinopsis?.takeIf(String::isNotBlank)?.take(1000) ?: "Prueba de apertura de MiFlix. No inicia reproducción.",
        title.tipo, tvDetailArtwork(title).backgrounds.firstOrNull(), false) }
    // Explicit synthetic cards fill only missing slots; never claim they exist in the remote catalog.
    return real + (real.size until 5).map { i -> HomeProbeCard(
        HOME_PROBE_PREFIX + homeProbeHash("fixture:$i"), "PRUEBA SINTÉTICA ${i + 1}",
        "Tarjeta experimental, no es un título del catálogo. Abre MiFlix, no una ficha.", "pelicula", null, true) }
}

internal data class HomeProbeStored(val id: Long, val key: String?, val payload: Map<String, String?>)
internal data class HomeProbeDesired(val key: String, val payload: Map<String, String?>)
internal data class HomeProbeUpsert(val existingId: Long?, val desired: HomeProbeDesired)
internal data class HomeProbePlan(val upserts: List<HomeProbeUpsert>, val deleteIds: List<Long>)

/** Called only after a successful own-channel query. Foreign/unmarked records are never deleted. */
internal fun homeProbePlan(existing: List<HomeProbeStored>, desired: List<HomeProbeDesired>): HomeProbePlan {
    require(desired.all { it.key.startsWith(HOME_PROBE_PREFIX) })
    require(desired.map { it.key }.distinct().size == desired.size)
    val owned = existing.filter { it.key?.startsWith(HOME_PROBE_PREFIX) == true }.sortedBy { it.id }
    val byKey = owned.groupBy { it.key }
    val keep = desired.map { it.key }.toSet()
    val deletes = owned.filter { it.key !in keep }.map { it.id }.toMutableList()
    val upserts = desired.mapNotNull { target ->
        val matches = byKey[target.key].orEmpty()
        deletes += matches.drop(1).map { it.id }
        val old = matches.firstOrNull()
        if (old != null && target.payload.all { (key, value) -> old.payload[key] == value }) null
        else HomeProbeUpsert(old?.id, target)
    }
    return HomeProbePlan(upserts, deletes.distinct())
}
