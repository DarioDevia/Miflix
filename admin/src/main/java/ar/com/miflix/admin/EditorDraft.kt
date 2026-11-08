package ar.com.miflix.admin

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Copy only the edited path. Shared branches are immutable editor snapshots. */
internal object EditorDraft {
    fun root(item:JsonObject)=JsonObject().apply {item.entrySet().forEach {(key,value)->add(key,value)}}
    fun field(item:JsonObject,key:String,value:String)=root(item).apply {
        if(key=="generos") add(key,JsonArray().apply {value.split(',').map(String::trim).filter(String::isNotBlank).forEach {add(it)}})
        else addProperty(key,value)
        if(key=="fondo_url") {if(value.isBlank()) remove("backdrop_url") else addProperty("backdrop_url",value.trim())}
    }
    fun season(item:JsonObject,number:Long,key:String,value:String)=part(item,number,null,key,value)
    fun episode(item:JsonObject,season:Long,episode:Long,key:String,value:String)=part(item,season,episode,key,value)
    private fun part(item:JsonObject,season:Long,episode:Long?,key:String,value:String)=root(item).apply {
        add("temporadas",JsonArray().apply {
            item.array("temporadas").forEach {entry ->
                val old=entry.asJsonObject
                add(if(old.number("numero")!=season) old else root(old).apply {
                    if(episode==null) addProperty(key,value)
                    else add("episodios",JsonArray().apply {old.array("episodios").forEach {e ->
                        add(if(e.asJsonObject.number("numero")!=episode) e else root(e.asJsonObject).apply {addProperty(key,value)})
                    }})
                })
            }
        })
    }
}
