package app.remotio.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.remotio.R
import app.remotio.tv.Platform
import app.remotio.tv.RemoteKey
import app.remotio.tv.Tv
import app.remotio.ui.theme.RemoteLayoutTheme

/** What the remote screens can ask for; grouped so each layout doesn't need a dozen callback parameters. */
class RemoteActions(
    val onKey: (RemoteKey) -> Unit,
    val onTypeText: () -> Unit,
    val onSpeakText: () -> Unit,
    val onToggleLayout: () -> Unit,
    val onSelectTv: (Tv) -> Unit,
    val onAddTv: () -> Unit,
    val onForgetTv: (Tv) -> Unit,
)

@Composable
fun RemoteScreen(
    tv: Tv,
    online: Boolean?,
    savedTvs: List<Tv>,
    layout: RemoteLayout,
    snackbarHostState: SnackbarHostState,
    actions: RemoteActions,
) {
    RemoteLayoutTheme(layout) {
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            when (layout) {
                RemoteLayout.Touchpad -> TouchpadLayout(tv, online, savedTvs, actions, padding)
                RemoteLayout.OneThumb -> OneThumbLayout(tv, online, savedTvs, actions, padding)
            }
        }
    }
}

/** Saved TVs to switch to, plus adding and forgetting one. Anchored to whatever opens it. */
@Composable
fun TvMenu(expanded: Boolean, onDismiss: () -> Unit, tv: Tv, savedTvs: List<Tv>, actions: RemoteActions) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        savedTvs.forEach { saved ->
            DropdownMenuItem(
                text = { Text(saved.name) },
                onClick = {
                    onDismiss()
                    actions.onSelectTv(saved)
                },
                leadingIcon = { Icon(painterResource(saved.platform.icon), contentDescription = null) },
                trailingIcon = if (saved.id == tv.id) {
                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                } else {
                    null
                },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.add_tv)) },
            onClick = {
                onDismiss()
                actions.onAddTv()
            },
            leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.forget_tv, tv.name)) },
            onClick = {
                onDismiss()
                actions.onForgetTv(tv)
            },
            leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
        )
    }
}

/** The selected TV's name and connection status; tapping it opens [TvMenu]. */
@Composable
fun TvTitle(
    tv: Tv,
    online: Boolean?,
    savedTvs: List<Tv>,
    actions: RemoteActions,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = stringResource(R.string.switch_tv)) { menuOpen = true }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (showName) {
                    Text(
                        tv.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusLine(tv.platform, online)
            }
            Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null)
        }
        TvMenu(menuOpen, { menuOpen = false }, tv, savedTvs, actions)
    }
}

@Composable
fun StatusLine(platform: Platform, online: Boolean?) {
    val (status, color) = when (online) {
        true -> R.string.status_online to Color(0xFF22A06B)
        false -> R.string.status_offline to MaterialTheme.colorScheme.error
        null -> R.string.status_checking to MaterialTheme.colorScheme.outline
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.status_line, stringResource(platform.label), stringResource(status)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Flips between the touchpad and one-thumb layouts; shows the layout you'd switch to. */
@Composable
fun LayoutToggleButton(current: RemoteLayout, onToggle: () -> Unit) {
    val (icon, label) = when (current) {
        RemoteLayout.Touchpad -> R.drawable.ic_gamepad to R.string.switch_to_dpad
        RemoteLayout.OneThumb -> R.drawable.ic_touch_app to R.string.switch_to_touchpad
    }
    IconButton(onClick = onToggle) {
        Icon(painterResource(icon), contentDescription = stringResource(label))
    }
}

@Composable
fun PowerButton(onKey: (RemoteKey) -> Unit, size: Dp = 48.dp) {
    KeyButton(
        key = RemoteKey.Power,
        onKey = onKey,
        modifier = Modifier.size(size),
        containerColor = MaterialTheme.colorScheme.errorContainer,
    )
}

/** A remote button showing [key]'s icon, labelled for screen readers. */
@Composable
fun KeyButton(
    key: RemoteKey,
    onKey: (RemoteKey) -> Unit,
    modifier: Modifier = Modifier,
    repeatOnHold: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = contentColorFor(containerColor),
    iconSize: Dp = 24.dp,
    shape: Shape = CircleShape,
) {
    RemoteButton(
        onPress = { onKey(key) },
        modifier = modifier,
        repeatOnHold = repeatOnHold,
        containerColor = containerColor,
        contentColor = contentColor,
        shape = shape,
    ) {
        Icon(painterResource(key.icon), contentDescription = stringResource(key.label), Modifier.size(iconSize))
    }
}

/** The arrows and OK, sized to fit; arrows repeat while held. */
@Composable
fun DPad(onKey: (RemoteKey) -> Unit, size: Dp, modifier: Modifier = Modifier) {
    val arrowSize = size * 0.29f
    Box(
        modifier
            .size(size)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
    ) {
        for ((key, alignment) in listOf(
            RemoteKey.Up to Alignment.TopCenter,
            RemoteKey.Down to Alignment.BottomCenter,
            RemoteKey.Left to Alignment.CenterStart,
            RemoteKey.Right to Alignment.CenterEnd,
        )) {
            KeyButton(
                key = key,
                onKey = onKey,
                modifier = Modifier.align(alignment).padding(4.dp).size(arrowSize),
                repeatOnHold = true,
                containerColor = Color.Transparent,
                iconSize = 34.dp,
            )
        }
        RemoteButton(
            onPress = { onKey(RemoteKey.Select) },
            modifier = Modifier.align(Alignment.Center).size(size * 0.37f),
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            Text(stringResource(R.string.key_select), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@get:DrawableRes
val Platform.icon: Int
    get() = when (this) {
        Platform.Roku -> R.drawable.ic_tv
        Platform.FireTv -> R.drawable.ic_hdmi
    }

@get:StringRes
val Platform.label: Int
    get() = when (this) {
        Platform.Roku -> R.string.platform_roku
        Platform.FireTv -> R.string.platform_fire_tv
    }

@get:DrawableRes
val RemoteKey.icon: Int
    get() = when (this) {
        RemoteKey.Power -> R.drawable.ic_power
        RemoteKey.Home -> R.drawable.ic_home
        RemoteKey.Back -> R.drawable.ic_back
        RemoteKey.Options -> R.drawable.ic_options
        RemoteKey.Up -> R.drawable.ic_chevron_up
        RemoteKey.Down -> R.drawable.ic_chevron_down
        RemoteKey.Left -> R.drawable.ic_chevron_left
        RemoteKey.Right -> R.drawable.ic_chevron_right
        RemoteKey.Select -> R.drawable.ic_check
        RemoteKey.InstantReplay -> R.drawable.ic_replay
        RemoteKey.Rewind -> R.drawable.ic_rewind
        RemoteKey.PlayPause -> R.drawable.ic_play_pause
        RemoteKey.FastForward -> R.drawable.ic_fast_forward
        RemoteKey.VolumeUp -> R.drawable.ic_add
        RemoteKey.VolumeDown -> R.drawable.ic_remove
        RemoteKey.Mute -> R.drawable.ic_volume_off
        RemoteKey.ChannelUp -> R.drawable.ic_add
        RemoteKey.ChannelDown -> R.drawable.ic_remove
    }

@get:StringRes
val RemoteKey.label: Int
    get() = when (this) {
        RemoteKey.Power -> R.string.key_power
        RemoteKey.Home -> R.string.key_home
        RemoteKey.Back -> R.string.key_back
        RemoteKey.Options -> R.string.key_options
        RemoteKey.Up -> R.string.key_up
        RemoteKey.Down -> R.string.key_down
        RemoteKey.Left -> R.string.key_left
        RemoteKey.Right -> R.string.key_right
        RemoteKey.Select -> R.string.key_select
        RemoteKey.InstantReplay -> R.string.key_instant_replay
        RemoteKey.Rewind -> R.string.key_rewind
        RemoteKey.PlayPause -> R.string.key_play_pause
        RemoteKey.FastForward -> R.string.key_fast_forward
        RemoteKey.VolumeUp -> R.string.key_volume_up
        RemoteKey.VolumeDown -> R.string.key_volume_down
        RemoteKey.Mute -> R.string.key_mute
        RemoteKey.ChannelUp -> R.string.key_channel_up
        RemoteKey.ChannelDown -> R.string.key_channel_down
    }
