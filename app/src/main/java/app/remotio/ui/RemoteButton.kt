package app.remotio.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay

/**
 * A remote button with a haptic tick. With [repeatOnHold], holding it keeps pressing, the way
 * holding volume or an arrow does on a physical remote.
 */
@Composable
fun RemoteButton(
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    repeatOnHold: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = contentColorFor(containerColor),
    shape: Shape = CircleShape,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val currentOnPress by rememberUpdatedState(onPress)
    val press = {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        currentOnPress()
    }
    val interactionSource = remember { MutableInteractionSource() }
    // Set while a hold is repeating, so letting go doesn't add one more press via onClick.
    var repeating by remember { mutableStateOf(false) }

    if (repeatOnHold) {
        val pressed by interactionSource.collectIsPressedAsState()
        LaunchedEffect(pressed) {
            if (!pressed) return@LaunchedEffect
            delay(HOLD_DELAY_MS)
            repeating = true
            while (true) {
                press()
                delay(REPEAT_INTERVAL_MS)
            }
        }
    }

    Surface(
        onClick = { if (repeating) repeating = false else press() },
        modifier = modifier,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

private const val HOLD_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 120L
