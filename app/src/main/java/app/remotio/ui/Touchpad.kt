package app.remotio.ui

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.remotio.R
import app.remotio.tv.RemoteKey
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * A trackpad for the TV: tap for OK, each swipe of [STEP] moves one step, and resting your finger at
 * the end of a swipe keeps moving that way, like holding an arrow key.
 */
@Composable
fun Touchpad(onKey: (RemoteKey) -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val currentOnKey by rememberUpdatedState(onKey)
    // The last key sent, briefly lighting up its edge chevron (or the centre ring for OK).
    var flash by remember { mutableStateOf<RemoteKey?>(null) }
    var flashes by remember { mutableIntStateOf(0) }
    LaunchedEffect(flashes) {
        delay(FLASH_MS)
        flash = null
    }
    fun send(key: RemoteKey) {
        view.performHapticFeedback(
            if (key == RemoteKey.Select) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.CLOCK_TICK,
        )
        currentOnKey(key)
        flash = key
        flashes++
    }

    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)
    val description = stringResource(R.string.touchpad_description)
    val accessibilityActions = listOf(RemoteKey.Up, RemoteKey.Down, RemoteKey.Left, RemoteKey.Right, RemoteKey.Select)
        .map { key -> CustomAccessibilityAction(stringResource(key.label)) { send(key); true } }

    Box(
        modifier
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant, shape)
            .drawBehind {
                val spacing = 22.dp.toPx()
                val radius = 1.2.dp.toPx()
                var y = spacing / 2
                while (y < size.height) {
                    var x = spacing / 2
                    while (x < size.width) {
                        drawCircle(colors.outlineVariant, radius, Offset(x, y))
                        x += spacing
                    }
                    y += spacing
                }
            }
            .pointerInput(Unit) { detectTouchpadGestures(onStep = ::send, onTap = { send(RemoteKey.Select) }) }
            .semantics {
                contentDescription = description
                customActions = accessibilityActions
            },
        contentAlignment = Alignment.Center,
    ) {
        for ((key, alignment) in listOf(
            RemoteKey.Up to Alignment.TopCenter,
            RemoteKey.Down to Alignment.BottomCenter,
            RemoteKey.Left to Alignment.CenterStart,
            RemoteKey.Right to Alignment.CenterEnd,
        )) {
            Icon(
                painterResource(key.icon),
                contentDescription = null,
                modifier = Modifier.align(alignment).padding(14.dp).size(28.dp),
                tint = if (flash == key) colors.primary else colors.outline,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                Modifier.size(84.dp).border(2.dp, colors.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (flash == RemoteKey.Select) 64.dp else 40.dp)
                        .background(colors.primary.copy(alpha = if (flash == RemoteKey.Select) 0.6f else 0.3f), CircleShape),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.touchpad_hint_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.touchpad_hint_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private suspend fun PointerInputScope.detectTouchpadGestures(onStep: (RemoteKey) -> Unit, onTap: () -> Unit) {
    val stepPx = STEP.toPx()
    awaitEachGesture {
        val down = awaitFirstDown()
        val slop = viewConfiguration.touchSlop
        var anchor = down.position
        var lastStep: RemoteKey? = null
        var swiped = false
        var repeatAt = Long.MAX_VALUE

        while (true) {
            val wait = repeatAt - SystemClock.uptimeMillis()
            val event = when {
                repeatAt == Long.MAX_VALUE -> awaitPointerEvent()
                wait <= 0 -> null
                else -> withTimeoutOrNull(wait) { awaitPointerEvent() }
            }
            if (event == null) {
                // The finger is resting after a swipe: keep going that way.
                lastStep?.let(onStep)
                repeatAt = SystemClock.uptimeMillis() + REPEAT_MS
                continue
            }
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (!swiped && (change.position - down.position).getDistance() < slop) onTap()
                break
            }
            val delta = change.position - anchor
            val direction = delta.direction()
            if (maxOf(abs(delta.x), abs(delta.y)) >= stepPx) {
                onStep(direction)
                lastStep = direction
                swiped = true
                anchor = change.position
                repeatAt = SystemClock.uptimeMillis() + HOLD_MS
            } else if (lastStep != null && direction != lastStep && delta.getDistance() > slop) {
                // Heading somewhere else: stop repeating the old direction until the next full step.
                repeatAt = Long.MAX_VALUE
            }
            change.consume()
        }
    }
}

private fun Offset.direction(): RemoteKey =
    if (abs(x) > abs(y)) {
        if (x > 0) RemoteKey.Right else RemoteKey.Left
    } else {
        if (y > 0) RemoteKey.Down else RemoteKey.Up
    }

/** How far a finger travels for one step on the TV. */
private val STEP = 48.dp
private const val HOLD_MS = 450L
private const val REPEAT_MS = 140L
private const val FLASH_MS = 160L
