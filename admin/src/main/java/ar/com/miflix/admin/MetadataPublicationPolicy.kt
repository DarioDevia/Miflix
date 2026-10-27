package ar.com.miflix.admin

import com.google.gson.JsonElement

/** Fail closed at both repository and JSON publication boundaries; credentials can never lift this guard. */
object MetadataPublicationPolicy {
    const val BLOCKED="Hay metadatos de TheTVDB guardados localmente. No se publicaron: falta verificar los permisos de distribución y mostrar su atribución con enlace en Mobile y Android TV. Revertí la recuperación y guardá, o esperá una actualización de los clientes."
    fun containsTvdb(value: JsonElement?): Boolean = when {
        value==null -> false
        value.isJsonArray -> value.asJsonArray.any {containsTvdb(it)}
        value.isJsonObject -> {
            val obj=value.asJsonObject
            obj.text("fuente").equals("TheTVDB",ignoreCase=true) || obj.text("provider").equals("TheTVDB",ignoreCase=true) || obj.text("publicacion_bloqueada")=="true" ||
                obj.entrySet().any {containsTvdb(it.value)}
        }
        else -> false
    }
    fun requirePublishable(value: JsonElement) {check(!containsTvdb(value)) {BLOCKED}}
}
