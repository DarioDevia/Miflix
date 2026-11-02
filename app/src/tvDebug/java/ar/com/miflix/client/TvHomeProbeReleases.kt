package ar.com.miflix.client

import com.google.gson.JsonParser
import java.time.LocalDate

internal const val HOME_PROBE_LIMIT = 10
internal const val HOME_PROBE_DISPLAY_NAME = "MiFlix TV"
internal val HOME_PROBE_CHANNEL_NAMES = setOf(HOME_PROBE_DISPLAY_NAME,"MiFlix","MiFlix - Prueba Home")
internal data class HomeProbeChannel(val id:Long,val name:String?,val marker:String?,val owner:String,val preview:Boolean)

/** ID 2 is a preference, never proof of ownership or identity. Unknown channels are not adopted. */
internal fun homeProbeChannel(channels:List<HomeProbeChannel>,owner:String,savedId:Long):HomeProbeChannel? {
    val eligible=channels.filter { it.owner==owner && it.preview &&
        (it.marker==HOME_PROBE_CHANNEL || it.name in HOME_PROBE_CHANNEL_NAMES) }
    return eligible.firstOrNull { it.id==2L } ?: eligible.firstOrNull { it.id==savedId } ?:
        eligible.sortedWith(compareBy<HomeProbeChannel> { it.marker!=HOME_PROBE_CHANNEL }.thenBy { it.id }).firstOrNull()
}
internal data class HomeProbeDate(val date:LocalDate?,val issue:String?=null)
internal data class HomeProbeCatalog(val titles:List<Title>,val releases:Map<String,HomeProbeDate>)
internal data class HomeProbeSelection(val cards:List<HomeProbeCard>,val missingDates:Int,
    val invalidDates:Int,val conflictingDates:Int,val futureDates:Int,val yearMismatches:Int,val asOf:LocalDate) {
    val missingSlots get()=HOME_PROBE_LIMIT-cards.size
}

/** Only explicit whole-title fields. Never year, addedAt, season/episode air dates or current date. */
internal fun homeProbeReleaseDate(type:String,fields:Map<String,String?>):HomeProbeDate {
    val names=when(type) {
        "pelicula" -> listOf("fecha_estreno","release_date")
        "serie" -> listOf("fecha_estreno","first_air_date")
        "anime" -> listOf("fecha_estreno","first_air_date","release_date")
        else -> emptyList()
    }
    val values=names.mapNotNull { fields[it]?.takeIf(String::isNotBlank) }
    if(values.isEmpty())return HomeProbeDate(null,"missing")
    val dates=values.map { raw -> raw.takeIf { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) }
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { it.year>=1888 } }
    if(dates.any { it==null })return HomeProbeDate(null,"invalid")
    if(dates.distinct().size!=1)return HomeProbeDate(null,"conflict")
    return HomeProbeDate(dates.first())
}

/** Preserve optional raw fields in the experiment only; stable Catalog/Title and remote JSON unchanged. */
internal fun homeProbeParseCatalog(raw:String):HomeProbeCatalog {
    require(raw.toByteArray(Charsets.UTF_8).size<=6*1024*1024)
    val catalog=CatalogRepository.parseCatalog(raw)
    val root=JsonParser.parseString(raw).asJsonObject
    val releases=root.getAsJsonArray("items").associate { entry ->
        val item=entry.asJsonObject
        val fields=listOf("fecha_estreno","release_date","first_air_date").associateWith { name ->
            val value=item.get(name)
            when {
                value==null || value.isJsonNull -> null
                value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString.trim()
                else -> "<invalid>"
            }
        }
        item.get("id").asString to homeProbeReleaseDate(item.get("tipo").asString,fields)
    }
    return HomeProbeCatalog(catalog.items.orEmpty(),releases)
}

/** Descending full date naturally walks current year, then previous years, with stable ID ties. */
internal fun homeProbeCards(snapshot:HomeProbeCatalog,today:LocalDate):HomeProbeSelection {
    val available=snapshot.titles.asSequence().filter { it.id.isNotBlank() && it.titulo.isNotBlank() &&
        it.tipo in setOf("pelicula","serie","anime") && tvFeaturedTarget(it)!=null }.distinctBy { it.id }.toList()
    var missing=0;var invalid=0;var conflicting=0;var future=0;var mismatches=0
    val dated=available.mapNotNull { title ->
        val value=snapshot.releases[title.id] ?: HomeProbeDate(null,"missing")
        val date=value.date
        if(date==null) { when(value.issue) { "invalid" -> invalid++;"conflict" -> conflicting++;else -> missing++ };null }
        else if(date>today) { future++;null }
        else {
            if(title.year!=null && title.year!=date.year)mismatches++
            title to date // The explicit full date is authoritative; a coarse year is never substituted.
        }
    }.sortedWith(compareByDescending<Pair<Title,LocalDate>> { it.second }.thenBy { it.first.id })
    val cards=dated.take(HOME_PROBE_LIMIT).map { (title,_) -> HomeProbeCard(
        HOME_PROBE_PREFIX+homeProbeHash("catalog:"+title.id),title.titulo,
        title.sinopsis?.takeIf(String::isNotBlank)?.take(1000) ?: "Abre MiFlix. No inicia reproducción.",
        title.tipo,tvDetailArtwork(title).backgrounds.firstOrNull(),false) }
    return HomeProbeSelection(cards,missing,invalid,conflicting,future,mismatches,today)
}
