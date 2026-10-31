package ar.com.miflix.admin

import android.content.Context
import android.util.AtomicFile
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

class CollectionFileStore(context:Context):StateStore {
    private val file=AtomicFile(File(context.filesDir,"collections-state.json"))
    @Synchronized override fun read():JsonObject? = try {JsonParser.parseString(file.openRead().bufferedReader().use {it.readText()}).asJsonObject}
        catch(_:java.io.FileNotFoundException) {null}
    @Synchronized override fun write(state:JsonObject) {
        val output=file.startWrite()
        try {output.write(state.toString().toByteArray(Charsets.UTF_8));file.finishWrite(output)}
        catch(_:Exception) {file.failWrite(output);error("No se pudieron guardar las colecciones. Conservamos el estado anterior.")}
    }
}
