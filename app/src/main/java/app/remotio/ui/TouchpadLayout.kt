package app.remotio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.remotio.R
import app.remotio.tv.RemoteKey
import app.remotio.tv.Tv
import app.remotio.tv.supportedKeys

/** Layout B: a big swipe-and-tap pad instead of a D-pad, with the essentials around it. */
@Composable
fun TouchpadLayout(tv: Tv, online: Boolean?, savedTvs: List<Tv>, actions: RemoteActions, contentPadding: PaddingValues) {
    val supported = tv.platform.supportedKeys
    val tileColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvTitle(tv, online, savedTvs, actions, Modifier.weight(1f))
            LayoutToggleButton(RemoteLayout.Touchpad, actions.onToggleLayout)
            PowerButton(actions.onKey)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (key in listOf(RemoteKey.Back, RemoteKey.Home, RemoteKey.Options, RemoteKey.PlayPause)) {
                if (key !in supported) continue
                KeyButton(
                    key = key,
                    onKey = actions.onKey,
                    modifier = Modifier.weight(1f).height(54.dp),
                    containerColor = tileColor,
                    contentColor = if (key == RemoteKey.PlayPause) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(18.dp),
                )
            }
        }

        Touchpad(actions.onKey, Modifier.weight(1f).fillMaxWidth())

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KeyButton(RemoteKey.Rewind, actions.onKey, Modifier.size(58.dp), containerColor = tileColor)
            VolumePill(actions.onKey, Modifier.weight(1f))
            KeyButton(RemoteKey.FastForward, actions.onKey, Modifier.size(58.dp), containerColor = tileColor)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val buttonShape = RoundedCornerShape(20.dp)
            OutlinedButton(onClick = actions.onTypeText, modifier = Modifier.weight(1f).height(58.dp), shape = buttonShape) {
                Icon(painterResource(R.drawable.ic_keyboard), contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.type), fontWeight = FontWeight.Bold)
            }
            Button(onClick = actions.onSpeakText, modifier = Modifier.weight(1f).height(58.dp), shape = buttonShape) {
                Icon(painterResource(R.drawable.ic_mic), contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.speak), fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** Volume down and up on either end, mute on the speaker in the middle. */
@Composable
private fun VolumePill(onKey: (RemoteKey) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.height(58.dp), shape = RoundedCornerShape(percent = 50), color = colors.surfaceContainerHigh) {
        Row(
            Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            KeyButton(RemoteKey.VolumeDown, onKey, Modifier.size(46.dp), repeatOnHold = true, containerColor = colors.surfaceContainerHighest)
            RemoteButton(
                onPress = { onKey(RemoteKey.Mute) },
                modifier = Modifier.height(46.dp),
                containerColor = Color.Transparent,
                contentColor = colors.onSurfaceVariant,
                shape = RoundedCornerShape(percent = 50),
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_volume_off), contentDescription = stringResource(R.string.key_mute), Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.rocker_volume),
                        Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            KeyButton(RemoteKey.VolumeUp, onKey, Modifier.size(46.dp), repeatOnHold = true, containerColor = colors.surfaceContainerHighest)
        }
    }
}
