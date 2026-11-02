package ar.com.miflix.client

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun tvHomeCredentialMessage():String? =
    "La conexión usa tu configuración personal de Telegram; esta APK no incorpora credenciales del desarrollador."

/** This is provisioning UI only; TDLib authorization and its existing storage are unchanged. */
@Composable
internal fun TvHomeCredentials(session:TelegramSession) {
    var id by remember { mutableStateOf("") }
    var hash by remember { mutableStateOf("") } // Deliberately not rememberSaveable; never write to Activity state/logs.
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    Column(Modifier.widthIn(max=640.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Configuración personal de Telegram")
        Text("Obtené tu API ID y API hash en my.telegram.org → API development tools. No uses ni compartas las credenciales de otra persona. Podés escribir con el teclado del control remoto o del celular.")
        TvEditableField(id,{ if(it.length<=10 && it.all(Char::isDigit)) id=it },"API ID",Modifier.fillMaxWidth(),type=KeyboardType.Number,initialFocus=true)
        TvEditableField(hash,{ if(it.length<=32) hash=it.trim() },"API hash",Modifier.fillMaxWidth(),type=KeyboardType.Password,password=true)
        if(problem) Text("No se pudo guardar la configuración. Revisá los datos e intentá nuevamente.")
        TvAction("Continuar",enabled=!busy && tvHomeCredentialsValid(id,hash)) {
            val apiId=id.toIntOrNull() ?: return@TvAction
            val apiHash=hash
            busy=true;problem=false
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { session.configure(apiId,apiHash) }
                    id="";hash=""
                } catch(_:Exception) { problem=true }
                finally { busy=false }
            }
        }
    }
}
