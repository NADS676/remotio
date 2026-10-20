package app.remotio.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.remotio.R
import app.remotio.ui.theme.remoteColorScheme

@Composable
fun RemotioApp(viewModel: RemoteViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val messageText = state.message?.let { messageText(it) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.messageShown()
        }
    }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshStatus()
        onPauseOrDispose { }
    }

    val selected = state.selected
    if (selected == null || state.picking) {
        TvPickerScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onScan = viewModel::scan,
            onChoose = viewModel::choose,
            onAddManually = viewModel::addManually,
            onClose = if (selected != null) viewModel::closePicker else null,
        )
    } else {
        val actions = remember(viewModel) {
            RemoteActions(
                onKey = viewModel::press,
                onTypeText = { viewModel.openTextEntry(TextEntryMode.Keyboard) },
                onSpeakText = { viewModel.openTextEntry(TextEntryMode.Voice) },
                onToggleLayout = viewModel::toggleLayout,
                onSelectTv = viewModel::choose,
                onAddTv = viewModel::openPicker,
                onForgetTv = viewModel::forget,
            )
        }
        RemoteScreen(
            tv = selected,
            online = state.online,
            savedTvs = state.savedTvs,
            layout = state.layout,
            snackbarHostState = snackbarHostState,
            actions = actions,
        )
        state.textEntry?.let { mode ->
            MaterialTheme(colorScheme = remoteColorScheme(state.layout)) {
                TextEntrySheet(
                    tv = selected,
                    mode = mode,
                    recents = state.recentTexts,
                    onLiveText = viewModel::syncText,
                    onSend = viewModel::sendText,
                    onBackspaceOnTv = viewModel::backspaceOnTv,
                    onDismiss = viewModel::closeTextEntry,
                )
            }
        }
    }

    state.pairing?.let { pairing ->
        PairingDialog(pairing, onSubmit = viewModel::submitPin, onDismiss = viewModel::cancelPairing)
    }
}

@Composable
private fun messageText(message: UserMessage): String = when (message) {
    is UserMessage.Unreachable -> stringResource(R.string.message_unreachable, message.tvName)
    UserMessage.RokuControlDisabled -> stringResource(R.string.message_roku_control_disabled)
    is UserMessage.NotFound ->
        stringResource(R.string.message_not_found, message.host, stringResource(message.platform.label))
    is UserMessage.PairingFailed -> stringResource(R.string.message_pairing_failed, message.tvName)
    is UserMessage.CommandFailed -> stringResource(R.string.message_command_failed, message.tvName)
}
