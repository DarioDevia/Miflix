package ar.com.miflix.client

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable

/** Inactive adapter: no Intent interpretation, state, navigation or provider access. */
internal class ExperimentalHomeEntry {
    fun create(intent: Intent?, saved: Bundle?) = Unit
    fun receive(intent: Intent?) = Unit
    fun save(out: Bundle) = Unit
    @Composable
    fun Dispatch(titles: List<Title>, catalogReady: Boolean, authorized: Boolean, onOpen: (Title?) -> Unit) = Unit
}
