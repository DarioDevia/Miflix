package ar.com.miflix.client

import java.security.MessageDigest

internal const val HOME_PROBE_PREFIX = "miflix.homeprobe.v1:"
internal const val HOME_PROBE_CHANNEL = "miflix.homeprobe.channel.v1"
internal fun homeProbeHash(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

internal data class HomeProbeCard(val key: String, val title: String, val description: String,
    val type: String, val image: String?, val synthetic: Boolean, val titleId: String? = null)

internal data class HomeProbeStored(val id: Long, val key: String?, val payload: Map<String, String?>)
internal data class HomeProbeDesired(val key: String, val payload: Map<String, String?>)
internal data class HomeProbeUpsert(val existingId: Long?, val desired: HomeProbeDesired)
internal data class HomeProbePlan(val upserts: List<HomeProbeUpsert>, val deleteIds: List<Long>)

/** Called only after a successful own-channel query. Foreign/unmarked records are never deleted. */
internal fun homeProbePlan(existing: List<HomeProbeStored>, desired: List<HomeProbeDesired>): HomeProbePlan {
    require(desired.all { it.key.startsWith(HOME_PROBE_PREFIX) })
    require(desired.map { it.key }.distinct().size == desired.size)
    // An empty/failed selection is never authorization to remove the last published set.
    if (desired.isEmpty()) return HomeProbePlan(emptyList(), emptyList())
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
