package com.simohypers.limusic.ui.component

import limusic.composeapp.generated.resources.Res
import limusic.composeapp.generated.resources.waiting_for_network
import org.jetbrains.compose.resources.stringResource
import com.simohypers.limusic.ui.theme.rounded
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.simohypers.limusic.domain.data.entities.SongEntity
import com.simohypers.limusic.domain.data.model.listentogether.LinkedDevice
import com.simohypers.limusic.domain.mediaservice.handler.MediaPlayerHandler
import com.simohypers.limusic.domain.mediaservice.handler.RepeatState
import com.simohypers.limusic.domain.utils.toSongEntity
import com.simohypers.limusic.logger.Logger
import com.simohypers.limusic.ui.icon.Favorite
import com.simohypers.limusic.ui.icon.FavoriteBorder
import com.simohypers.limusic.ui.icon.Pause
import com.simohypers.limusic.ui.icon.PlayArrow
import com.simohypers.limusic.ui.icon.LimusicIcons
import com.simohypers.limusic.ui.icon.SkipNext
import com.simohypers.limusic.ui.screen.player.ArtworkSeekAction
import com.simohypers.limusic.ui.screen.player.computeSeekAction
import com.simohypers.limusic.ui.screen.player.deriveOrderIndex
import com.simohypers.limusic.ui.theme.typo
import com.simohypers.limusic.viewModel.SharedViewModel
import com.simohypers.limusic.viewModel.UIEvent
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.koinInject
import kotlin.math.abs

private const val TAG = "LimusicMiniPlayer"

/** How far an upward drag has to travel before it opens the full player. */
private val SWIPE_UP = 22.dp

/**
 * The bar above the tabs while something plays. A tap or an upward swipe opens the full player; a
 * sideways swipe pages through the queue, the neighbouring song sliding in with the finger.
 *
 * The paging mirrors the full player's artwork pager (NowPlayingScreenContent), hazards included;
 * see the comments there for the long story behind each guard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LimusicMiniPlayer(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    sharedViewModel: SharedViewModel = koinInject(),
    mediaPlayerHandler: MediaPlayerHandler = koinInject(),
) {
    val nowPlaying by sharedViewModel.nowPlayingState.collectAsStateWithLifecycle()
    val control by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val timeline by sharedViewModel.timeline.collectAsStateWithLifecycle()
    val waitingForNetwork by sharedViewModel.waitingForNetwork.collectAsStateWithLifecycle()
    val song = nowPlaying?.songEntity
    val remoteDevice = rememberRemoteDevice()
    val progress = if (timeline.total > 0L && timeline.current >= 0L) timeline.current.toFloat() / timeline.total else 0f
    val onBackground = MaterialTheme.colorScheme.onSurface

    // The queue and position the full player's pager reads, but our own PagerState: both pagers can
    // be composed at once, and sharing one would let each drag the other around.
    val queueDataState by sharedViewModel.getQueueDataState().collectAsStateWithLifecycle()
    val queue by remember { derivedStateOf { queueDataState?.data?.listTracks ?: emptyList() } }
    // videoId, not mediaItem.mediaId: the latter carries the "Video" prefix for video items.
    val nowPlayingVideoId: String? = nowPlaying?.track?.videoId
    // currentOrderIndex() is not state; the keys are the two things that can move it.
    val currentOrderIndex by remember(queue, nowPlayingVideoId) {
        derivedStateOf { deriveOrderIndex(queue, nowPlayingVideoId, mediaPlayerHandler.currentOrderIndex()) }
    }
    val pagerState =
        rememberPagerState(
            initialPage = currentOrderIndex.coerceAtLeast(0),
            pageCount = { queue.size.coerceAtLeast(1) },
        )
    var isAnimatingFromPlayer by remember { mutableStateOf(false) }
    var isUserDraggingActive by remember { mutableStateOf(false) }
    // A real drag waiting to become a seek. settledPage also moves on first composition and on our
    // own scrolls, and treating such a page as a swipe would re-seek to a stale track.
    var pendingUserSwipe by remember { mutableStateOf(false) }
    val latestOrderIndex by rememberUpdatedState(currentOrderIndex)
    val latestQueueSize by rememberUpdatedState(queue.size)

    // isScrollInProgress is true for our own animateScrollToPage too; isAnimatingFromPlayer tells them apart.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress to isAnimatingFromPlayer }
            .collect { (scrolling, animating) ->
                isUserDraggingActive = scrolling && !animating
                if (isUserDraggingActive) pendingUserSwipe = true
            }
    }

    // Same track, new index (a shuffle, a trimmed radio history): jump in this very frame, so the
    // cover the new order put on the old page never shows. Only when the index moved, not just the
    // pager: between a swipe settling and its seek landing, the pager is ahead of the track on purpose.
    val lastTrackId = remember { arrayOfNulls<String>(1) }
    val lastOrderIndex = remember { intArrayOf(currentOrderIndex) }
    val jumpTarget = currentOrderIndex
    if (!isUserDraggingActive &&
        jumpTarget in queue.indices &&
        jumpTarget != pagerState.currentPage &&
        jumpTarget != lastOrderIndex[0] &&
        nowPlayingVideoId != null &&
        nowPlayingVideoId == lastTrackId[0]
    ) {
        SideEffect {
            pendingUserSwipe = false
            pagerState.requestScrollToPage(jumpTarget)
        }
    }
    SideEffect {
        lastTrackId[0] = nowPlayingVideoId
        lastOrderIndex[0] = jumpTarget
    }

    // Player → pager: the track changed elsewhere (buttons, notification, full player). Keyed on the
    // index alone; a changing key would cancel a slide in flight and park the pager between pages.
    LaunchedEffect(currentOrderIndex) {
        val target = currentOrderIndex
        if (!isUserDraggingActive && target in queue.indices && target != pagerState.currentPage) {
            // A drag that sprang back never moved settledPage, so its latch is still up; drop it, or
            // the page this scroll settles on (often a frame after the flag below is cleared) seeks.
            pendingUserSwipe = false
            isAnimatingFromPlayer = true
            try {
                // Slide to a neighbour; a far jump is not worth flinging through every cover between.
                if (abs(target - pagerState.currentPage) <= 1) {
                    pagerState.animateScrollToPage(target)
                } else {
                    pagerState.scrollToPage(target)
                }
                // A bar that is not laid out has nothing to slide through; a snap still holds.
                if (pagerState.currentPage != target) pagerState.scrollToPage(target)
            } finally {
                isAnimatingFromPlayer = false
            }
        }
    }

    // Pager → player: seek when a swipe settles on another page. Keyed on the pager alone, the index
    // read through rememberUpdatedState: re-keying on the track would build a new flow whose first
    // emission is the stale settledPage, dispatched as though it had just been swiped to.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                if (isAnimatingFromPlayer || !pendingUserSwipe) return@collect
                pendingUserSwipe = false
                val orderIndex = latestOrderIndex
                if (settled !in 0 until latestQueueSize || settled == orderIndex) return@collect
                runCatching {
                    when (val action = computeSeekAction(settled, orderIndex)) {
                        ArtworkSeekAction.Next -> sharedViewModel.onUIEvent(UIEvent.Next)
                        // SkipToPrevious, not Previous: a swipe means the previous song, never a restart.
                        ArtworkSeekAction.Previous -> sharedViewModel.onUIEvent(UIEvent.SkipToPrevious)
                        is ArtworkSeekAction.Skip -> mediaPlayerHandler.playMediaItemInMediaSource(action.index)
                        ArtworkSeekAction.NoOp -> Unit
                    }
                }.onFailure { error -> Logger.w(TAG, "Mini player seek failed: ${error.message}") }
            }
    }

    // The queue shrank under the current page: move back inside it, flagged as ours so it is no seek.
    LaunchedEffect(queue.size) {
        if (queue.isNotEmpty() && pagerState.currentPage >= queue.size) {
            pendingUserSwipe = false
            isAnimatingFromPlayer = true
            try {
                runCatching { pagerState.scrollToPage(queue.lastIndex) }
            } finally {
                isAnimatingFromPlayer = false
            }
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(rounded(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onOpen)
            .pointerInput(Unit) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        dragged += amount
                    },
                    onDragEnd = { if (dragged < -SWIPE_UP.toPx()) onOpen() },
                )
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Reaches the bar's start edge, the gutter inside each page, so a song slides out under
            // the rounded edge instead of being cut off 8dp short of it.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                userScrollEnabled = control.repeatState !is RepeatState.One && queue.size >= 2,
                key = { index -> "mini_${queue.getOrNull(index)?.videoId.orEmpty()}_$index" },
            ) { page ->
                if (page == currentOrderIndex || queue.isEmpty()) {
                    MiniPlayerTrack(
                        song = song,
                        remoteDevice = remoteDevice,
                        isPlaying = control.isPlaying,
                        waitingForNetwork = waitingForNetwork,
                    )
                } else {
                    val track = queue.getOrNull(page)
                    // Mapped the way the playing song's entity is, so the cover keeps its cache key
                    // once it starts playing. toSongEntity calls last() on the thumbnails, hence the guard.
                    val neighbour =
                        remember(track) { track?.let { it.copy(thumbnails = it.thumbnails?.takeIf { t -> t.isNotEmpty() }).toSongEntity() } }
                    MiniPlayerTrack(song = neighbour, remoteDevice = null, isPlaying = false, waitingForNetwork = false)
                }
            }
            IconButton(onClick = { sharedViewModel.onUIEvent(UIEvent.ToggleLike) }) {
                Icon(
                    imageVector = if (control.isLiked) LimusicIcons.Favorite else LimusicIcons.FavoriteBorder,
                    contentDescription = null,
                    tint = if (control.isLiked) MaterialTheme.colorScheme.primary else onBackground,
                    modifier = Modifier.size(22.dp),
                )
            }
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { sharedViewModel.onUIEvent(UIEvent.PlayPause) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (control.isPlaying) LimusicIcons.Pause else LimusicIcons.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = { sharedViewModel.onUIEvent(UIEvent.Next) }, enabled = control.isNextAvailable) {
                Icon(
                    LimusicIcons.SkipNext,
                    contentDescription = null,
                    tint = if (control.isNextAvailable) onBackground else onBackground.copy(alpha = 0.35f),
                )
            }
        }
        // Thin progress line along the bottom edge, as on the desktop player bar.
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(onBackground.copy(alpha = 0.12f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/**
 * One page of the mini player: cover, title and artists. Only the playing song's page gets the
 * "Playing on" and waiting-for-network lines; a neighbour passes null and false.
 */
@Composable
private fun MiniPlayerTrack(
    song: SongEntity?,
    remoteDevice: LinkedDevice?,
    isPlaying: Boolean,
    waitingForNetwork: Boolean,
) {
    Row(
        Modifier
            .fillMaxSize()
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalPlatformContext.current)
                    .data(song?.thumbnails)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .diskCacheKey(song?.thumbnails)
                    .crossfade(300)
                    .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = rememberHolderPainter(),
            error = rememberHolderPainter(),
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(rounded(6.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song?.title.orEmpty(),
                style = typo().titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (remoteDevice != null) {
                PlayingOnLabel(remoteDevice, playing = isPlaying, style = typo().bodySmall)
            } else if (waitingForNetwork) {
                Text(
                    text = stringResource(Res.string.waiting_for_network),
                    style = typo().bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            } else {
                Text(
                    text = song?.artistName?.joinToString(", ").orEmpty(),
                    style = typo().bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
