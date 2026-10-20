package app.remotio.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.remotio.R
import app.remotio.tv.Tv
import app.remotio.tv.supportsLiveTyping

/** Type or speak text into whatever text box is open on the TV (search, sign-in, Wi-Fi password...). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEntrySheet(
    tv: Tv,
    mode: TextEntryMode,
    recents: List<String>,
    onLiveText: (String) -> Unit,
    onSend: (String) -> Unit,
    onBackspaceOnTv: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canLiveType = tv.platform.supportsLiveTyping
    var liveTyping by rememberSaveable { mutableStateOf(canLiveType) }
    var text by rememberSaveable { mutableStateOf("") }
    var voiceStarted by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    fun update(newText: String) {
        text = newText
        if (liveTyping) onLiveText(newText)
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) update(spoken)
        focusRequester.requestFocus()
    }
    val voicePrompt = stringResource(R.string.voice_prompt, tv.name)
    val voiceUnavailable = stringResource(R.string.voice_unavailable)
    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, voiceUnavailable, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (mode == TextEntryMode.Voice && !voiceStarted) {
            voiceStarted = true
            listen()
        } else {
            focusRequester.requestFocus()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.type_to_tv, tv.name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.type_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = text,
                onValueChange = ::update,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                label = { Text(stringResource(R.string.text_for_tv)) },
                singleLine = true,
                trailingIcon = {
                    Row {
                        if (text.isNotEmpty()) {
                            IconButton(onClick = { update("") }) {
                                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.clear_text))
                            }
                        }
                        IconButton(onClick = ::listen) {
                            Icon(painterResource(R.drawable.ic_mic), contentDescription = stringResource(R.string.speak))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend(text) }),
            )

            if (canLiveType) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = liveTyping,
                        onCheckedChange = { on ->
                            liveTyping = on
                            if (on) onLiveText(text)
                        },
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.live_typing), style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (recents.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.recent),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recents.forEach { recent ->
                            SuggestionChip(
                                onClick = { update(recent) },
                                label = { Text(recent) },
                                icon = { Icon(painterResource(R.drawable.ic_history), contentDescription = null, Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canLiveType) {
                    FilledTonalButton(onClick = onBackspaceOnTv) {
                        Icon(painterResource(R.drawable.ic_backspace), contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_on_tv))
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSend(text) }) {
                    Text(stringResource(R.string.send))
                    Spacer(Modifier.width(8.dp))
                    Icon(painterResource(R.drawable.ic_keyboard_return), contentDescription = null, Modifier.size(18.dp))
                }
            }
        }
    }
}
