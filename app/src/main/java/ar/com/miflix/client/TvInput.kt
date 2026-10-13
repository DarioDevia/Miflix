package ar.com.miflix.client

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp

/** TV editor: OK explicitly opens the IME; DPAD can leave the editor for its action. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun TvEditableField(value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, type: KeyboardType = KeyboardType.Text,
    password: Boolean = false, initialFocus: Boolean = false,
    onDone: () -> Unit = {}) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(label, initialFocus) {
        if (initialFocus) { withFrameNanos { }; focus.requestFocus() }
    }
    OutlinedTextField(value, onValueChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); onDone() }),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White,
            unfocusedTextColor = Color.White, cursorColor = Color.White,
            focusedBorderColor = Color(0xFFEF4B4B), focusedLabelColor = Color.White,
            unfocusedLabelColor = Color.LightGray),
        modifier = modifier.tvFocusTarget("input:$label", entry = initialFocus)
            .focusRequester(focus).onFocusChanged { focused = it.isFocused }
            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent,
                RoundedCornerShape(8.dp))
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode in listOf(23, 66)) {
                    if (event.type == KeyEventType.KeyDown) { focus.requestFocus(); keyboard?.show() }
                    true
                } else if (event.nativeKeyEvent.keyCode in listOf(19, 20)) {
                    if (event.type == KeyEventType.KeyDown) {
                        keyboard?.hide()
                        focusManager.moveFocus(if (event.nativeKeyEvent.keyCode == 19) FocusDirection.Up else FocusDirection.Down)
                    }; true
                } else false
            })
}

@Composable
internal fun TvAuthEntry(label: String, value: String, onValueChange: (String) -> Unit,
    phone: Boolean, password: Boolean, submitting: Boolean, onSubmit: () -> Unit, code: Boolean = false) {
    Column(Modifier.widthIn(max = 540.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row {
            if (phone) Text("+54", color = Color.White, modifier = Modifier.padding(end = 12.dp, top = 18.dp))
            TvEditableField(value, onValueChange, label, Modifier.weight(1f),
                type = if (phone) KeyboardType.Phone else if (password) KeyboardType.Password else if (code) KeyboardType.Number else KeyboardType.Text,
                password = password, initialFocus = true,
                onDone = { if (value.isNotBlank() && !submitting) onSubmit() })
        }
        TvAction("Continuar", enabled = value.isNotBlank() && !submitting, onClick = onSubmit)
    }
}
