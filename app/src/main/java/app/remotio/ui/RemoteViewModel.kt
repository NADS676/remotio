package app.remotio.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.remotio.tv.CommandRefusedException
import app.remotio.tv.LiveTypingRemote
import app.remotio.tv.Platform
import app.remotio.tv.RecentTextStore
import app.remotio.tv.RemoteKey
import app.remotio.tv.SavedTvStore
import app.remotio.tv.Tv
import app.remotio.tv.TvDiscovery
import app.remotio.tv.TvHttpException
import app.remotio.tv.TvRemote
import app.remotio.tv.firetv.FireTvRemote
import app.remotio.tv.normalizeHost
import app.remotio.tv.remote
import app.remotio.tv.roku.RokuRemote
import app.remotio.tv.supportedKeys
import app.remotio.tv.supportsLiveTyping
import app.remotio.tv.textEdit
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class RemoteUiState(
    val savedTvs: List<Tv> = emptyList(),
    val selected: Tv? = null,
    /** Whether [selected] answered last time we tried; null while we don't know yet. */
    val online: Boolean? = null,
    val picking: Boolean = false,
    val scanning: Boolean = false,
    val discovered: List<Tv> = emptyList(),
    val connecting: Boolean = false,
    val pairing: PairingState? = null,
    val layout: RemoteLayout = RemoteLayout.Touchpad,
    /** The type-to-TV sheet, when it's open. */
    val textEntry: TextEntryMode? = null,
    val recentTexts: List<String> = emptyList(),
    val message: UserMessage? = null,
)

enum class RemoteLayout { Touchpad, OneThumb }

enum class TextEntryMode { Keyboard, Voice }

sealed interface PairingState {
    val tv: Tv

    data class RequestingPin(override val tv: Tv) : PairingState
    data class EnterPin(override val tv: Tv, val wrongPin: Boolean = false) : PairingState
    data class Verifying(override val tv: Tv) : PairingState
}

sealed interface UserMessage {
    data class Unreachable(val tvName: String) : UserMessage
    data object RokuControlDisabled : UserMessage
    data class NotFound(val host: String, val platform: Platform) : UserMessage
    data class PairingFailed(val tvName: String) : UserMessage
    data class CommandFailed(val tvName: String) : UserMessage
}

/** Everything sent to the TV goes through one queue, so it arrives in order, one request at a time. */
private sealed interface Command {
    data class Key(val key: RemoteKey) : Command
    data class Type(val text: String) : Command
    /** Live typing: make the TV's text box match [text]. */
    data class SyncText(val text: String) : Command
    data object Backspace : Command
    data object Submit : Command
    /** A new typing session: assume the TV's text box starts out empty. */
    data object StartText : Command
}

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SavedTvStore(application)
    private val recents = RecentTextStore(application)
    private val uiPrefs = application.getSharedPreferences("ui", Context.MODE_PRIVATE)
    private val discovery = TvDiscovery(application)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<RemoteUiState> = _state.asStateFlow()

    private val commands = Channel<Command>(capacity = 64)
    private var remote: Pair<Tv, TvRemote>? = null
    private var scanJob: Job? = null

    /** What we've typed into the TV's text box this session; only touched by the command queue. */
    private var shownOnTv = ""

    init {
        viewModelScope.launch {
            for (command in commands) run(command)
        }
        if (_state.value.selected == null) scan()
    }

    private fun initialState(): RemoteUiState {
        val saved = store.load()
        val selected = saved.find { it.id == store.selectedId } ?: saved.firstOrNull()
        val layout = uiPrefs.getString(KEY_LAYOUT, null)
            ?.let { name -> RemoteLayout.entries.find { it.name == name } }
            ?: RemoteLayout.Touchpad
        return RemoteUiState(savedTvs = saved, selected = selected, layout = layout, recentTexts = recents.load())
    }

    fun toggleLayout() {
        val next = when (_state.value.layout) {
            RemoteLayout.Touchpad -> RemoteLayout.OneThumb
            RemoteLayout.OneThumb -> RemoteLayout.Touchpad
        }
        uiPrefs.edit { putString(KEY_LAYOUT, next.name) }
        _state.update { it.copy(layout = next) }
    }

    fun press(key: RemoteKey) {
        commands.trySend(Command.Key(key))
    }

    /** Hardware volume keys: returns true if the press went to the TV instead of the phone. */
    fun pressFromHardwareKey(key: RemoteKey): Boolean {
        val current = _state.value
        val tv = current.selected ?: return false
        if (current.picking || current.pairing != null || key !in tv.platform.supportedKeys) return false
        press(key)
        return true
    }

    fun openTextEntry(mode: TextEntryMode) {
        commands.trySend(Command.StartText)
        _state.update { it.copy(textEntry = mode) }
    }

    fun closeTextEntry() {
        _state.update { it.copy(textEntry = null) }
    }

    fun syncText(text: String) {
        commands.trySend(Command.SyncText(text))
    }

    /** Finishes typing: makes sure the TV shows [text], presses Enter where the TV has one, and closes the sheet. */
    fun sendText(text: String) {
        val tv = _state.value.selected ?: return
        if (text.isNotBlank()) _state.update { it.copy(recentTexts = recents.add(text.trim())) }
        if (tv.platform.supportsLiveTyping) {
            // Only types whatever live typing hasn't already put on the TV.
            commands.trySend(Command.SyncText(text))
            commands.trySend(Command.Submit)
        } else if (text.isNotEmpty()) {
            commands.trySend(Command.Type(text))
        }
        closeTextEntry()
    }

    /** Deletes a character on the TV, for clearing text that was already in its box. */
    fun backspaceOnTv() {
        commands.trySend(Command.Backspace)
    }

    fun refreshStatus() {
        val tv = _state.value.selected ?: return
        viewModelScope.launch {
            val reachable = when (tv.platform) {
                Platform.Roku -> {
                    val info = runCatching { RokuRemote(tv.host).deviceInfo() }.getOrNull()
                    // Picks up a name changed on the TV since it was added.
                    if (info != null && info.name != tv.name) save(tv.copy(name = info.name))
                    info != null
                }
                Platform.FireTv -> FireTvRemote.isFireTv(tv.host)
            }
            _state.update { if (it.selected?.id == tv.id) it.copy(online = reachable) else it }
        }
    }

    fun openPicker() {
        _state.update { it.copy(picking = true) }
        scan()
    }

    fun closePicker() {
        _state.update { it.copy(picking = false) }
    }

    fun scan() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            _state.update { it.copy(scanning = true) }
            val found = runCatching { discovery.discover() }.getOrDefault(emptyList())
            _state.update { it.copy(scanning = false, discovered = found) }
        }
    }

    /** Picks a TV from the list, pairing first if it's a Fire TV we have no token for. */
    fun choose(tv: Tv) {
        val saved = _state.value.savedTvs.find { it.id == tv.id } ?: tv
        if (saved.needsPairing) startPairing(saved) else select(saved)
    }

    fun addManually(input: String, platform: Platform) {
        val host = normalizeHost(input)
        if (host.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(connecting = true) }
            val tv = when (platform) {
                Platform.Roku -> runCatching { RokuRemote(host).deviceInfo() }.getOrNull()
                Platform.FireTv -> if (FireTvRemote.isFireTv(host)) Tv(Platform.FireTv, host, "Fire TV") else null
            }
            _state.update { it.copy(connecting = false) }
            if (tv != null) {
                choose(tv)
            } else {
                _state.update { it.copy(message = UserMessage.NotFound(host, platform)) }
            }
        }
    }

    fun forget(tv: Tv) {
        val remaining = _state.value.savedTvs.filterNot { it.id == tv.id }
        store.save(remaining)
        val selected = _state.value.selected?.takeUnless { it.id == tv.id } ?: remaining.firstOrNull()
        store.selectedId = selected?.id
        _state.update { it.copy(savedTvs = remaining, selected = selected, online = null) }
        if (selected == null) scan() else refreshStatus()
    }

    private fun startPairing(tv: Tv) {
        _state.update { it.copy(pairing = PairingState.RequestingPin(tv)) }
        viewModelScope.launch {
            try {
                FireTvRemote(tv.host).showPin()
                updatePairing(tv) { PairingState.EnterPin(tv) }
            } catch (e: IOException) {
                pairingFailed(tv)
            }
        }
    }

    fun submitPin(pin: String) {
        val tv = (_state.value.pairing as? PairingState.EnterPin)?.tv ?: return
        _state.update { it.copy(pairing = PairingState.Verifying(tv)) }
        viewModelScope.launch {
            try {
                val fireTv = FireTvRemote(tv.host)
                val token = fireTv.verifyPin(pin.trim())
                if (_state.value.pairing?.tv?.id != tv.id) return@launch // cancelled meanwhile
                _state.update { it.copy(pairing = null) }
                select(tv.copy(token = token, certSha256 = fireTv.certSha256))
            } catch (e: CommandRefusedException) {
                updatePairing(tv) { PairingState.EnterPin(tv, wrongPin = true) }
            } catch (e: IOException) {
                pairingFailed(tv)
            }
        }
    }

    fun cancelPairing() {
        _state.update { it.copy(pairing = null) }
    }

    fun messageShown() {
        _state.update { it.copy(message = null) }
    }

    private fun updatePairing(tv: Tv, next: () -> PairingState) {
        _state.update { if (it.pairing?.tv?.id == tv.id) it.copy(pairing = next()) else it }
    }

    private fun pairingFailed(tv: Tv) {
        _state.update {
            if (it.pairing?.tv?.id != tv.id) it
            else it.copy(pairing = null, message = UserMessage.PairingFailed(tv.name))
        }
    }

    private fun select(tv: Tv) {
        save(tv)
        store.selectedId = tv.id
        _state.update { it.copy(selected = tv, online = true, picking = false) }
    }

    /** Adds or replaces [tv] in the saved list. */
    private fun save(tv: Tv) {
        val saved = _state.value.savedTvs
        val updated = if (saved.any { it.id == tv.id }) saved.map { if (it.id == tv.id) tv else it } else saved + tv
        store.save(updated)
        _state.update { state ->
            state.copy(savedTvs = updated, selected = state.selected?.let { if (it.id == tv.id) tv else it })
        }
    }

    private suspend fun run(command: Command) {
        if (command == Command.StartText) {
            shownOnTv = ""
            return
        }
        val tv = _state.value.selected ?: return
        val remote = remote?.takeIf { it.first == tv }?.second ?: tv.remote().also { remote = tv to it }
        try {
            when (command) {
                is Command.Key -> remote.press(command.key)
                is Command.Type -> remote.type(command.text)
                is Command.SyncText -> (remote as? LiveTypingRemote)?.let { syncText(it, command.text) }
                Command.Backspace -> (remote as? LiveTypingRemote)?.backspace(1)
                Command.Submit -> (remote as? LiveTypingRemote)?.submit()
                Command.StartText -> Unit
            }
            if (_state.value.online != true) _state.update { it.copy(online = true) }
        } catch (e: CommandRefusedException) {
            dropQueuedCommands()
            when (tv.platform) {
                Platform.Roku -> _state.update { it.copy(message = UserMessage.RokuControlDisabled) }
                // The Fire TV forgot us (reset, or the pairing was removed): ask for a new PIN.
                Platform.FireTv -> startPairing(tv.copy(token = null, certSha256 = null))
            }
        } catch (e: TvHttpException) {
            Log.w(TAG, "${tv.platform} rejected $command", e)
            dropQueuedCommands()
            _state.update { it.copy(online = true, message = UserMessage.CommandFailed(tv.name)) }
        } catch (e: IOException) {
            dropQueuedCommands()
            _state.update { it.copy(online = false, message = UserMessage.Unreachable(tv.name)) }
        }
    }

    private suspend fun syncText(remote: LiveTypingRemote, text: String) {
        val (deletes, append) = textEdit(shownOnTv, text)
        if (deletes > 0) {
            remote.backspace(deletes)
            shownOnTv = shownOnTv.substring(0, shownOnTv.commonPrefixWith(text).length)
        }
        if (append.isNotEmpty()) {
            remote.type(append)
            shownOnTv = text
        }
    }

    /** Once the TV is back, a burst of stale presses from while it was unreachable would be a surprise. */
    private fun dropQueuedCommands() {
        while (commands.tryReceive().isSuccess) {}
    }

    private companion object {
        const val TAG = "RemoteViewModel"
        const val KEY_LAYOUT = "layout"
    }
}
