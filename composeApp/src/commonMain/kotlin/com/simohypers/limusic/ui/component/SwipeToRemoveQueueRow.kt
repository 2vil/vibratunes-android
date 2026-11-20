package com.simohypers.limusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.simohypers.limusic.domain.mediaservice.handler.MediaPlayerHandler
import com.simohypers.limusic.ui.icon.Delete
import com.simohypers.limusic.ui.icon.LimusicIcons
import kotlinx.coroutines.delay
import limusic.composeapp.generated.resources.Res
import limusic.composeapp.generated.resources.delete_from_queue
import org.jetbrains.compose.resources.stringResource
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

/**
 * A queue row that a leftward swipe takes off the queue, over a red strip with a bin. Only
 * end-to-start: elsewhere a rightward swipe adds a song to the queue, so on the queue itself the
 * other way does nothing rather than mean something else.
 *
 * [index] and [videoId] are the row's identity: the swipe state is kept per pair, so a row that
 * inherits a removed row's lazy key (a radio repeating a song) does not come up already swiped away.
 * [onRemove] says whether the row really went; if not, it springs back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToRemoveQueueRow(
    index: Int,
    videoId: String,
    enabled: Boolean,
    onRemove: () -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    key(index, videoId) {
        // 40% of the row, not Material's 56dp: a vertical scroll that drifts sideways must not
        // throw a song away.
        val state = rememberSwipeToDismissBoxState(positionalThreshold = { total -> total * 0.4f })
        val currentOnRemove by rememberUpdatedState(onRemove)
        LaunchedEffect(state.currentValue) {
            if (state.currentValue != SwipeToDismissBoxValue.EndToStart) return@LaunchedEffect
            if (currentOnRemove()) {
                // The row normally leaves with the queue update and this is cancelled; should the
                // update never come (a remote player that ignores it), don't leave a blank gap.
                delay(2_000)
            }
            state.reset()
        }
        val strip = MaterialTheme.colorScheme.errorContainer
        val onStrip = MaterialTheme.colorScheme.onErrorContainer
        SwipeToDismissBox(
            state = state,
            backgroundContent = {
                // Rows are see-through over the cover, so the strip only covers what the swipe
                // uncovered, not the whole row behind the text.
                if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .layout { measurable, constraints ->
                                val width = (-state.requireOffset()).roundToInt().coerceIn(0, constraints.maxWidth)
                                val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                                layout(width, constraints.maxHeight) { placeable.place(0, 0) }
                            }.background(strip),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(LimusicIcons.Delete, contentDescription = stringResource(Res.string.delete_from_queue), tint = onStrip)
                    }
                }
            },
            modifier = modifier,
            enableDismissFromStartToEnd = false,
            enableDismissFromEndToStart = true,
            gesturesEnabled = enabled,
            content = content,
        )
    }
}
