package app.remotio.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.remotio.R
import app.remotio.tv.Platform
import app.remotio.tv.Tv

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvPickerScreen(
    state: RemoteUiState,
    snackbarHostState: SnackbarHostState,
    onScan: () -> Unit,
    onChoose: (Tv) -> Unit,
    onAddManually: (String, Platform) -> Unit,
    onClose: (() -> Unit)?,
) {
    if (onClose != null) BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.picker_title)) },
                navigationIcon = {
                    if (onClose != null) {
                        IconButton(onClick = onClose) {
                            Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onScan, enabled = !state.scanning) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.scan_again))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val newTvs = state.discovered.filter { found -> state.savedTvs.none { it.id == found.id } }
        LazyColumn(
            Modifier.fillMaxSize().consumeWindowInsets(padding).imePadding(),
            contentPadding = padding,
        ) {
            if (state.savedTvs.isNotEmpty()) {
                item { SectionHeader(R.string.your_tvs) }
                items(state.savedTvs, key = { "saved:${it.id}" }) { tv -> TvRow(tv, onClick = { onChoose(tv) }) }
            }
            item { SectionHeader(R.string.on_your_network) }
            if (state.scanning) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) }
            }
            items(newTvs, key = { "found:${it.id}" }) { tv -> TvRow(tv, onClick = { onChoose(tv) }) }
            if (!state.scanning && newTvs.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.nothing_found),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { ManualEntry(state.connecting, onAddManually) }
        }
    }
}

@Composable
private fun SectionHeader(@StringRes text: Int) {
    Text(
        stringResource(text),
        Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun TvRow(tv: Tv, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(tv.name) },
        supportingContent = { Text(stringResource(R.string.status_line, stringResource(tv.platform.label), tv.host)) },
        leadingContent = { Icon(painterResource(tv.platform.icon), contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualEntry(connecting: Boolean, onAdd: (String, Platform) -> Unit) {
    var host by rememberSaveable { mutableStateOf("") }
    var platform by rememberSaveable { mutableStateOf(Platform.Roku) }
    val submit = { if (host.isNotBlank() && !connecting) onAdd(host, platform) }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.add_by_ip),
            Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Platform.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = platform == option,
                    onClick = { platform = option },
                    shape = SegmentedButtonDefaults.itemShape(index, Platform.entries.size),
                ) { Text(stringResource(option.label)) }
            }
        }
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.ip_address)) },
            placeholder = { Text("192.168.0.20") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )
        Text(
            stringResource(R.string.ip_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = submit, enabled = host.isNotBlank() && !connecting, modifier = Modifier.align(Alignment.End)) {
            if (connecting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.connect))
            }
        }
    }
}
