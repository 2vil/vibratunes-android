package com.simohypers.limusic.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.simohypers.limusic.domain.mediaservice.handler.MediaPlayerHandler
import com.simohypers.limusic.ui.icon.Delete
import com.simohypers.limusic.ui.icon.LimusicIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import limusic.composeapp.generated.resources.Res
import limusic.composeapp.generated.resources.delete_from_queue
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Takes the queue row at [index] out, but only if [videoId] is still the track there and it is not
 * the one playing. A radio trims played history off the front, so between the swipe and this call
 * every position can have moved; then nothing is removed rather than whatever slid into its place.
 * The same check the song sheet's "Remove from queue" makes.
 */
internal fun MediaPlayerHandler.removeFromQueueIfStillThere(
    index: Int,
    videoId: String,
): Boolean {
    if (followingRoom) return false
    val stillThere = queueData.value?.data?.listTracks?.getOrNull(index)?.videoId == videoId
    if (!stillThere || index == currentOrderIndex()) return false
    removeMediaItem(index)
    return true
}

/** How far towards the start, as a share of the row, a released row has to be to go. */
private const val REMOVE_FRACTION = 0.4f

/**
 * A queue row that a swipe towards the start takes off the queue, over a red strip with a bin.
 * Only that way: elsewhere a rightward swipe adds a song to the queue, so on the queue itself the
 * other way does nothing rather than mean something else.
 *
 * The row follows the finger and nothing happens until it lifts: then the row goes if it is past
 * [REMOVE_FRACTION] of its width or was flung that way, and otherwise springs back. Going, it slides
 * fully out first and only then [onRemove] is asked; if that says no (the queue moved meanwhile),
 * it slides back.
 *
 * [index] and [videoId] are the row's identity: when they change (a lazy key handed on to another
 * copy of the song, history trimmed off the front) the row starts flat and any swipe is dropped.
 */
@Composable
internal fun SwipeToRemoveQueueRow(
    index: Int,
    videoId: String,
    enabled: Boolean,
    onRemove: () -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Read only in layout and placement, never in composition: dragging doesn't recompose the row.
    val offsetX = remember { Animatable(0f) }
    var removing by remember { mutableStateOf(false) }
    val currentOnRemove by rememberUpdatedState(onRemove)

    LaunchedEffect(index, videoId) {
        removing = false
        offsetX.snapTo(0f)
    }
    LaunchedEffect(enabled) {
        if (!enabled && !removing) offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
    }

    val strip = MaterialTheme.colorScheme.errorContainer
    val onStrip = MaterialTheme.colorScheme.onErrorContainer
    Box(
        modifier
            // On the row's frame, not on the part that slides: positions measured on something
            // that moves with the finger would eat half of every drag.
            .pointerInput(enabled, index, videoId) {
                if (!enabled) return@pointerInput
                val flingVelocity = 800.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (removing) return@awaitEachGesture
                    var target = offsetX.value
                    val drag =
                        awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            // Only towards the start, unless the row is already out and coming back,
                            // and only mostly sideways: a scroll that drifts is the list's.
                            val moved = change.position - down.position
                            if ((over < 0f || target < 0f) && abs(moved.x) > abs(moved.y)) {
                                change.consume()
                                target = (target + over).coerceIn(-size.width.toFloat(), 0f)
                            }
                        } ?: return@awaitEachGesture
                    val tracker = VelocityTracker()
                    tracker.addPosition(drag.uptimeMillis, drag.position)
                    scope.launch { offsetX.snapTo(target) }
                    // From here the pointer is the row's until it lifts: every move is consumed, the
                    // vertical part too, so the list doesn't scroll and the sheet isn't pulled down.
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == drag.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val dx = change.position.x - change.previousPosition.x
                        event.changes.forEach { it.consume() }
                        if (dx != 0f) {
                            target = (target + dx).coerceIn(-size.width.toFloat(), 0f)
                            val to = target
                            scope.launch { offsetX.snapTo(to) }
                        }
                    }
                    val width = size.width.toFloat()
                    val velocity = tracker.calculateVelocity().x
                    val flung = abs(velocity) >= flingVelocity
                    val go = target < 0f && ((flung && velocity < 0f) || (!flung && target <= -width * REMOVE_FRACTION))
                    scope.launch {
                        if (!go) {
                            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow), initialVelocity = velocity)
                            return@launch
                        }
                        removing = true
                        // Quicker the faster it was flung, but never a jump.
                        val left = width + offsetX.value
                        val ms = if (velocity < 0f) (left / -velocity * 1000f).toInt().coerceIn(90, 220) else 220
                        offsetX.animateTo(-width, tween(ms, easing = FastOutLinearInEasing))
                        if (currentOnRemove()) {
                            // The row leaves with the queue update and this scope with it. Should
                            // that never come (a remote player that ignored it), don't leave a gap.
                            delay(2_000)
                            // Handed on to another row meanwhile (see the identity reset above).
                            if (!removing) return@launch
                        }
                        offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                        removing = false
                    }
                }
            },
    ) {
        // The strip is exactly as wide as what the slide has uncovered, so it is gone whenever the
        // row is home, and the row stays see-through over the cover.
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .layout { measurable, constraints ->
                        val width = (-offsetX.value).roundToInt().coerceIn(0, constraints.maxWidth)
                        val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                        layout(width, constraints.maxHeight) { placeable.place(0, 0) }
                    }.clipToBounds()
                    .background(strip),
                contentAlignment = Alignment.Center,
            ) {
                // requiredSize: a narrow strip clips the bin instead of squashing it.
                Icon(
                    LimusicIcons.Delete,
                    contentDescription = stringResource(Res.string.delete_from_queue),
                    tint = onStrip,
                    modifier = Modifier.requiredSize(24.dp),
                )
            }
        }
        Box(Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }) { content() }
    }
}
