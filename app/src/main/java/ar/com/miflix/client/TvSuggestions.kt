package ar.com.miflix.client

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal const val TV_REQUESTS_URL = "https://t.me/Dario_Devia"
internal const val TV_REQUESTS_CONTACT = "@Dario_Devia"
internal const val TV_REQUESTS_QR_ASSET = "miflix_tv_requests_qr.png"

@Composable
internal fun TvSuggestionsDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val closeFocus = remember { FocusRequester() }
    val qr = remember(context) { runCatching {
        context.assets.open(TV_REQUESTS_QR_ASSET).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
    }.getOrNull() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { withFrameNanos { }; closeFocus.requestFocus() }
        Column(Modifier.fillMaxWidth(.85f).widthIn(max = 820.dp)
            .background(Color(0xFF0B0D12)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Sugerencias y pedidos", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (qr != null) Image(qr, "Código QR para $TV_REQUESTS_URL",
                    Modifier.size(260.dp).background(Color.White))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("Escaneá el QR con tu celular para enviar una sugerencia o pedir un título por Telegram.", color = Color.White)
                    Text(TV_REQUESTS_CONTACT, color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(TV_REQUESTS_URL, color = Color.White)
                    if (qr == null) Text("No se pudo cargar el QR. Podés usar el enlace desde tu celular.", color = Color.LightGray)
                }
            }
            TvAction("Cerrar", Modifier.focusRequester(closeFocus), onClick = onClose)
        }
    }
}
