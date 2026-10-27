package ar.com.miflix.admin

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable fun ProviderSettingsScreen(vm: AdminViewModel) {
    val original=remember {vm.tvdbForEditing()}
    var savedKey by remember {mutableStateOf(original?.apiKey.orEmpty())}
    var savedPin by remember {mutableStateOf(original?.pin.orEmpty())}
    // Deliberately not rememberSaveable: no plaintext key in SavedState, backups or catalog exports.
    var key by remember {mutableStateOf(savedKey)}
    var pin by remember {mutableStateOf(savedPin)}
    var subscriber by remember {mutableStateOf(pin.isNotEmpty())}
    var visible by remember {mutableStateOf(false)}
    var remove by remember {mutableStateOf(false)}
    val context=LocalContext.current
    DisposableEffect(context) {
        val window=(context as? Activity)?.window
        val wasSecure=window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE)!=0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {if(!wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)}
    }
    val enabled=!vm.busy && !vm.testingProvider
    val dirty=key.trim()!=savedKey || (if(subscriber) pin.trim() else "")!=savedPin
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
        Text("TheTVDB",style=MaterialTheme.typography.titleMedium)
        Text("Modalidad gratuita del proyecto con atribución: ingresá solo la API Key. Admin obtiene y renueva el token automáticamente. No pegues un token.")
        OutlinedTextField(key,{key=it},label={Text("API Key TheTVDB")},modifier=Modifier.fillMaxWidth(),singleLine=true,enabled=enabled,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrect=false),
            visualTransformation=if(visible) VisualTransformation.None else PasswordVisualTransformation())
        TextButton(onClick={visible=!visible},enabled=enabled) {Text(if(visible) "Ocultar credenciales" else "Mostrar credenciales")}
        Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(subscriber,{subscriber=it},enabled=enabled)
            Text("Mi clave es de un proyecto sostenido por suscriptores y requiere PIN",Modifier.weight(1f))
        }
        if(subscriber) OutlinedTextField(pin,{pin=it},label={Text("PIN de suscriptor (no es la API Key)")},modifier=Modifier.fillMaxWidth(),singleLine=true,enabled=enabled,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrect=false),visualTransformation=if(visible) VisualTransformation.None else PasswordVisualTransformation())
        Button(onClick={
            vm.saveTvdb(key,if(subscriber) pin else "")
            val stored=vm.tvdbForEditing()
            if(stored!=null) {savedKey=stored.apiKey;savedPin=stored.pin}
        },enabled=enabled && key.isNotBlank(),modifier=Modifier.fillMaxWidth()) {Text("Guardar configuración")}
        OutlinedButton(onClick={vm.testProvider("TheTVDB")},enabled=enabled && savedKey.isNotBlank() && !dirty,modifier=Modifier.fillMaxWidth()) {Text("Probar conexión TheTVDB")}
        if(dirty) Text("Guardá la configuración antes de probarla.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={remove=true},enabled=enabled,modifier=Modifier.fillMaxWidth()) {Text("Eliminar clave, PIN y token")}
        if(vm.testingProvider) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(vm.providerStatus,style=MaterialTheme.typography.bodyMedium)
        Text("Cifrado local AES-GCM con Android Keystore, excluido de respaldos. El token permanece solo en memoria. Un dispositivo comprometido puede exponer credenciales mientras se usan.",style=MaterialTheme.typography.bodySmall)
        SourceLink("https://thetvdb.com","Metadatos proporcionados por TheTVDB")
        SourceLink("https://thetvdb.com/api-information","Licencia y atribución TheTVDB")
        SourceLink("https://thetvdb.com/tos","Condiciones de TheTVDB")
        Text("La clave debe estar autorizada para tu proyecto y su acceso directo. Guardar una clave no autoriza redistribución ilimitada. Los cambios TheTVDB se pueden revisar y guardar localmente; su sincronización queda bloqueada hasta verificar los permisos y añadir atribución en Mobile y TV. Las imágenes TheTVDB requieren derechos adicionales y no se importan.")
        OutlinedButton(onClick={vm.leaveProviders()},modifier=Modifier.fillMaxWidth()) {Text(if(vm.testingProvider) "Cancelar prueba y volver" else "Volver a Configuración")}
    }
    if(remove) AlertDialog(onDismissRequest={remove=false},title={Text("Eliminar TheTVDB del teléfono")},text={Text("Se eliminarán la API Key, el PIN y el token de acceso. El catálogo y las otras credenciales se conservan.")},
        confirmButton={TextButton(onClick={
            remove=false;vm.deleteTvdb()
            if(vm.tvdbForEditing()==null) {key="";pin="";savedKey="";savedPin="";subscriber=false;visible=false}
        }) {Text("Eliminar credenciales")}},dismissButton={TextButton(onClick={remove=false}) {Text("Cancelar")}})
}
