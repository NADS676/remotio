package app.remotio.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.remotio.R
import app.remotio.tv.RemoteKey
import app.remotio.tv.Tv
import app.remotio.tv.supportedKeys

/** Layout C: everything you press often sits in the bottom half, within reach of one thumb. */
@Composable
fun OneThumbLayout(tv: Tv, online: Boolean?, savedTvs: List<Tv>, actions: RemoteActions, contentPadding: PaddingValues) {
    val supported = tv.platform.supportedKeys
    val colors = MaterialTheme.colorScheme

    // The control panel runs under the navigation bar, so only the top inset is applied here.
    BoxWithConstraints(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // With two or three TVs, tabs make switching a single tap.
                val showTabs = savedTvs.size in 2..3
                if (showTabs) TvTabs(tv, savedTvs, actions.onSelectTv)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvTitle(tv, online, savedTvs, actions, Modifier.weight(1f), showName = !showTabs)
                    LayoutToggleButton(RemoteLayout.OneThumb, actions.onToggleLayout)
                    PowerButton(actions.onKey, size = 46.dp)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextTile(R.drawable.ic_keyboard, R.string.type, R.string.type_hint, highlighted = false, actions.onTypeText, Modifier.weight(1f))
                    TextTile(R.drawable.ic_mic, R.string.speak, R.string.speak_hint, highlighted = true, actions.onSpeakText, Modifier.weight(1f))
                }
                Surface(
                    shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                    color = colors.surfaceContainerLowest,
                ) {
                    Column(
                        Modifier
                            .navigationBarsPadding()
                            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        DPadCluster(supported, actions.onKey)
                        if (RemoteKey.ChannelUp in supported) ChannelRow(actions.onKey)
                        MediaRow(supported, actions.onKey)
                    }
                }
            }
        }
    }
}

@Composable
private fun TvTabs(tv: Tv, savedTvs: List<Tv>, onSelect: (Tv) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(colors.surfaceContainerHigh, RoundedCornerShape(18.dp))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        savedTvs.forEach { saved ->
            val selected = saved.id == tv.id
            val content = if (selected) colors.onSurface else colors.onSurfaceVariant
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) colors.surfaceContainerLowest else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(saved) })
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(painterResource(saved.platform.icon), contentDescription = null, Modifier.size(18.dp), tint = content)
                Spacer(Modifier.width(8.dp))
                Text(
                    saved.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = content,
                )
            }
        }
    }
}

@Composable
private fun TextTile(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes hint: Int,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.height(88.dp),
        shape = RoundedCornerShape(24.dp),
        color = if (highlighted) colors.primary else colors.surfaceContainerLowest,
        contentColor = if (highlighted) colors.onPrimary else colors.onSurface,
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(if (highlighted) colors.onPrimary else colors.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = null, Modifier.size(22.dp), tint = colors.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(stringResource(hint), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** The D-pad, with Back and Home above it and volume below, where a thumb pivots naturally. */
@Composable
private fun DPadCluster(supported: Set<RemoteKey>, onKey: (RemoteKey) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val dpadSize = minOf(260.dp, maxWidth - 90.dp)
        Box(Modifier.fillMaxWidth().height(dpadSize + 30.dp)) {
            KeyButton(RemoteKey.Back, onKey, Modifier.align(Alignment.TopStart).size(60.dp))
            KeyButton(RemoteKey.Home, onKey, Modifier.align(Alignment.TopEnd).size(60.dp))
            if (RemoteKey.VolumeDown in supported) VolumeSatellite(RemoteKey.VolumeDown, onKey, Modifier.align(Alignment.BottomStart))
            if (RemoteKey.VolumeUp in supported) VolumeSatellite(RemoteKey.VolumeUp, onKey, Modifier.align(Alignment.BottomEnd))
            DPad(onKey, dpadSize, Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun VolumeSatellite(key: RemoteKey, onKey: (RemoteKey) -> Unit, modifier: Modifier = Modifier) {
    RemoteButton(onPress = { onKey(key) }, modifier = modifier.size(60.dp), repeatOnHold = true) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(key.icon), contentDescription = stringResource(key.label), Modifier.size(20.dp))
            Text(
                stringResource(R.string.rocker_volume),
                Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

/** Roku only: channel down, instant replay, channel up. */
@Composable
private fun ChannelRow(onKey: (RemoteKey) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        ChannelKey(RemoteKey.ChannelDown, onKey)
        KeyButton(RemoteKey.InstantReplay, onKey, Modifier.size(48.dp))
        ChannelKey(RemoteKey.ChannelUp, onKey)
    }
}

@Composable
private fun ChannelKey(key: RemoteKey, onKey: (RemoteKey) -> Unit) {
    RemoteButton(
        onPress = { onKey(key) },
        modifier = Modifier.height(48.dp).width(96.dp),
        repeatOnHold = true,
        shape = RoundedCornerShape(percent = 50),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(key.icon), contentDescription = stringResource(key.label), Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.rocker_channel),
                Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun MediaRow(supported: Set<RemoteKey>, onKey: (RemoteKey) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        for (key in listOf(RemoteKey.Options, RemoteKey.Rewind, RemoteKey.PlayPause, RemoteKey.FastForward, RemoteKey.Mute)) {
            if (key !in supported) continue
            if (key == RemoteKey.PlayPause) {
                KeyButton(key, onKey, Modifier.size(64.dp), containerColor = MaterialTheme.colorScheme.inverseSurface, iconSize = 26.dp)
            } else {
                KeyButton(key, onKey, Modifier.size(52.dp))
            }
        }
    }
}
