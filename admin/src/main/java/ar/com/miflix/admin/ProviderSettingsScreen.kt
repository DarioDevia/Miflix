package ar.com.miflix.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun ProviderSettingsScreen(vm: AdminViewModel) {
    val enabled=!vm.busy && !vm.testingProvider
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("APIs y proveedores",style=MaterialTheme.typography.titleLarge)
        Text("Las consultas se ejecutan solo al solicitarlas. Las credenciales quedan en este teléfono y no forman parte del catálogo.")
        Text("TMDB",style=MaterialTheme.typography.titleMedium)
        Text(if(vm.config?.tmdbToken?.isNotBlank()==true) "Token local cifrado configurado." else "Usa la configuración existente del Worker. Token local opcional en Configuración del servidor.")
        OutlinedButton(onClick={vm.testProvider("TMDB")},enabled=enabled && vm.config!=null,modifier=Modifier.fillMaxWidth()) {Text("Probar conexión TMDB")}
        Text("TVmaze",style=MaterialTheme.typography.titleMedium)
        Text("API pública sin credenciales. Los datos importados conservan atribución y CC BY-SA 4.0.")
        SourceLink("https://www.tvmaze.com/api","TVmaze · API y condiciones")
        OutlinedButton(onClick={vm.testProvider("TVmaze")},enabled=enabled,modifier=Modifier.fillMaxWidth()) {Text("Probar conexión TVmaze")}
        if(vm.testingProvider) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(vm.providerStatus,style=MaterialTheme.typography.bodyMedium)
        if(vm.credentialCleanupNotice.isNotBlank()) Text(vm.credentialCleanupNotice)
        Text("Los cambios históricos retirados se detallan en Inicio → Revisión de metadatos históricos. Las demás credenciales se conservan.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={vm.leaveProviders()},modifier=Modifier.fillMaxWidth()) {Text(if(vm.testingProvider) "Cancelar prueba y volver" else "Volver a Configuración")}
    }
}
