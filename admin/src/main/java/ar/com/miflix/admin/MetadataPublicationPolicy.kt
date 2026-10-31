package ar.com.miflix.admin

import com.google.gson.JsonObject

/** Publication removes only identified retired metadata. No blanket catalog rejection. */
object MetadataPublicationPolicy {
    fun projection(root:JsonObject):JsonObject=RetiredMetadata.projection(root)
}
