package app.remotio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.remotio.R

/** Walks through Fire TV pairing: the TV shows a PIN, the user types it here. */
@Composable
fun PairingDialog(state: PairingState, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by rememberSaveable(state.tv.id) { mutableStateOf("") }
    val canSubmit = state is PairingState.EnterPin && pin.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pair_title, state.tv.name)) },
        text = {
            if (state is PairingState.RequestingPin) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.pair_waking))
                }
            } else {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                val wrongPin = state is PairingState.EnterPin && state.wrongPin
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.pair_enter_pin))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { input -> pin = input.filter(Char::isDigit).take(8) },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        label = { Text(stringResource(R.string.pair_pin)) },
                        singleLine = true,
                        // Read-only rather than disabled while verifying, so the keyboard stays up for a retry.
                        readOnly = state !is PairingState.EnterPin,
                        isError = wrongPin,
                        supportingText = if (wrongPin) {
                            { Text(stringResource(R.string.pair_wrong_pin)) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit(pin) }),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(pin) }, enabled = canSubmit) { Text(stringResource(R.string.pair)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
