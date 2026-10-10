package ar.com.miflix.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AboutMiFlix(onDismiss: () -> Unit) {
    val installed = AppUpdates.installedVersion()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Acerca de MiFlix") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("MiFlix", style = MaterialTheme.typography.titleLarge) }
                item { Text(installed.display) }
                item { Text("Desarrollador: ${AppUpdates.DEVELOPER}") }
                item { Text("Historial de actualizaciones", style = MaterialTheme.typography.titleMedium) }
                items(AppUpdates.history, key = { it.code }) { update ->
                    androidx.compose.foundation.layout.Column {
                        Text(update.name, style = MaterialTheme.typography.titleSmall)
                        update.changes.forEach { Text("• $it") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
internal fun UpdateNoticeDialog(onDismiss: () -> Unit) {
    val update = AppUpdates.history.firstOrNull { it.code == BuildConfig.VERSION_CODE }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novedades de MiFlix") },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(AppUpdates.installedVersion().display)
                update?.changes?.forEach { Text("• $it") }
                Text("Podés consultar el historial en Configuración → Acerca de.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendido") } }
    )
}
