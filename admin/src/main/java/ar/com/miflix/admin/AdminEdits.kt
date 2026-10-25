package ar.com.miflix.admin

import com.google.gson.JsonObject

/** The normal form changes both links; old drafts can still retain distinct values. */
object AdminEdits {
    fun telegramLink(draft: JsonObject,value: String): JsonObject = draft.deepCopy().apply {
        addProperty("telegram_url",value)
        addProperty("_admin_original",value)
    }
    fun hasPart(item: JsonObject,season: Long,episode: Long?=null): Boolean {
        val s=item.array("temporadas").firstOrNull {it.asJsonObject.number("numero")==season}?.asJsonObject ?: return false
        return episode==null || s.array("episodios").any {it.asJsonObject.number("numero")==episode}
    }
    fun removePart(item: JsonObject,season: Long,episode: Long?=null): JsonObject {
        require(item.text("tipo") in setOf("serie","anime")) {"Esta eliminación corresponde a series y anime."}
        require(hasPart(item,season,episode)) {"La temporada o el episodio ya no existe."}
        return item.deepCopy().apply {
            val seasons=array("temporadas")
            val index=seasons.indexOfFirst {it.asJsonObject.number("numero")==season}
            if(episode==null) seasons.remove(index)
            else {
                val episodes=seasons[index].asJsonObject.array("episodios")
                episodes.remove(episodes.indexOfFirst {it.asJsonObject.number("numero")==episode})
            }
        }
    }
}

/** Opening or cancelling a confirmation has no access to storage or the network. */
data class DeletionRequest(val itemId: String,val title: String,val season: Long?=null,val episode: Long?=null,val episodeCount: Int=0,val clearsDraft: Boolean=false) {
    fun confirm(repository: AdminRepository): Boolean {
        return if(season==null) repository.removeItem(itemId) else repository.removePart(itemId,season,episode)
    }
    val heading: String get()=when {
        episode!=null -> "¿Eliminar episodio $episode de la temporada $season?"
        season!=null -> "¿Eliminar Temporada $season?"
        else -> "¿Eliminar \"$title\" del catálogo?"
    }
    val explanation: String get()=when {
        episode!=null -> "Se quitará este episodio de MiFlix. Los demás episodios se conservarán."
        season!=null -> "Se quitarán del catálogo los $episodeCount episodios de esta temporada. Las demás temporadas se conservarán."
        else -> "Se quitará este contenido completo del catálogo MiFlix." + if(clearsDraft) " También se descartará su borrador de edición." else ""
    } + " No se eliminará ningún archivo ni publicación de Telegram. Cloudflare se actualizará cuando sincronices."
    val button: String get()=if(season!=null && episode==null) "Eliminar temporada" else "Eliminar"
}

fun SearchResult.originalTitle(): String {
    val candidates=if(source=="AniList") raw.getAsJsonObject("title")?.let {listOf(it.text("romaji"),it.text("native"))}.orEmpty()
        else listOf(raw.text("original_title"),raw.text("original_name"))
    return candidates.firstOrNull {it.isNotBlank() && !it.equals(title,true)}.orEmpty()
}
