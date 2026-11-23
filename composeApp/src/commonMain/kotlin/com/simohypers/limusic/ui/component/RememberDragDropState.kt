package com.simohypers.limusic.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.simohypers.limusic.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * [coveredTop] and [coveredBottom] are how much of the list's top and bottom something else draws
 * over (a bar, the mini player), beyond its own content padding: the edge scroll starts where the
 * list can still be seen, not under them.
 *
 * [minIndex] is the first row a dragged row may take the place of (null: any): the row in hand is
 * drawn no higher than that row's top and is never dropped above it. [onBlocked] runs once per drag,
 * the first time the row is pulled well past that boundary, so the caller can say why it stops.
 */
@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    coveredTop: Dp = 0.dp,
    coveredBottom: Dp = 0.dp,
    minIndex: Int? = null,
    onBlocked: () -> Unit = {},
    onSwap: (Int, Int) -> Unit,
): DragDropState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val state =
        remember(lazyListState) {
            DragDropState(
                state = lazyListState,
                onSwap = onSwap,
                scope = scope,
                maxScrollPerFramePx = with(density) { AUTO_SCROLL_MAX_PER_FRAME.toPx() },
            )
        }
    state.coveredTopPx = with(density) { coveredTop.toPx() }
    state.coveredBottomPx = with(density) { coveredBottom.toPx() }
    state.topMarginPx = with(density) { AUTO_SCROLL_TOP_MARGIN.toPx() }
    state.minIndex = minIndex
    state.onBlocked = onBlocked
    return state
}

fun LazyListState.getVisibleItemInfoFor(absoluteIndex: Int): LazyListItemInfo? =
    this
        .layoutInfo
        .visibleItemsInfo
        .getOrNull(
            absoluteIndex -
                this.layoutInfo.visibleItemsInfo
                    .first()
                    .index,
        )

val LazyListItemInfo.offsetEnd: Int
    get() = this.offset + this.size

// The edge scroll is RecyclerView's ItemTouchHelper's (androidx.recyclerview, Apache-2.0): the most
// the list scrolls in one 60 Hz frame (16.67 ms), reached once the row is a whole row height past
// the edge and has been held there for AUTO_SCROLL_ACCELERATION_MS. AndroidX's
// item_touch_helper_max_drag_scroll_per_frame is believed to be 20dp; not checked against the source.
private val AUTO_SCROLL_MAX_PER_FRAME = 20.dp
private const val AUTO_SCROLL_ACCELERATION_MS = 2000L
private const val AUTO_SCROLL_FRAME_MS = 1000f / 60f

// Our deviation from AndroidX: ItemTouchHelper scales by t^5 of the held time alone, which is near
// zero for the first ~0.7 s (t^5 < 0.01 until 0.8 s of 2 s), so the list barely moves at first and
// the row gets pushed all the way to the screen edge. The time factor starts at this fraction of
// the full speed instead and still eases in to all of it: FLOOR + (1 - FLOOR) * t^5.
private const val AUTO_SCROLL_MIN_SPEED_FRACTION = 0.15f

// The edge scroll upwards starts this far below the list's visible top, so it starts a little
// before the row reaches the top. The bottom edge has none.
private val AUTO_SCROLL_TOP_MARGIN = 32.dp

// How far, as a fraction of its height, the row in hand is pulled past the minIndex boundary before
// onBlocked runs: a row resting on the boundary that moves a few px doesn't count.
private const val BLOCKED_PULL_FRACTION = 0.5f

@OptIn(ExperimentalFoundationApi::class)
@ExperimentalFoundationApi
@Composable
fun LazyItemScope.DraggableItem(
    dragDropState: DragDropState,
    index: Int,
    modifier: Modifier,
    content: @Composable ColumnScope.(isDragging: Boolean) -> Unit,
) {
    val current: Float by animateFloatAsState(dragDropState.draggingItemOffset)
    val previous: Float by animateFloatAsState(dragDropState.previousItemOffset.value)
    val dragging = index == dragDropState.currentIndexOfDraggedItem
    // The row in hand stays composed and placed when the list scrolls its slot out of view under
    // the finger, so it is still drawn where the finger is.
    val pinnable = LocalPinnableContainer.current
    DisposableEffect(dragging, pinnable) {
        val handle = if (dragging) pinnable?.pin() else null
        onDispose { handle?.release() }
    }
    val draggingModifier =
        if (dragging) {
            Modifier
                .zIndex(1f)
                .graphicsLayer {
                    translationY = current
                }
        } else if (index == dragDropState.previousIndexOfDraggedItem) {
            Modifier
                .zIndex(1f)
                .graphicsLayer {
                    translationY = previous
                }
        } else {
            Modifier.animateItem(
                fadeInSpec = null,
                fadeOutSpec = null,
                placementSpec = tween(easing = FastOutLinearInEasing),
            )
        }
    Column(modifier = modifier.then(draggingModifier)) {
        content(dragging)
    }
}

class DragDropState internal constructor(
    val state: LazyListState,
    private val scope: CoroutineScope,
    private val onSwap: (Int, Int) -> Unit,
    private val maxScrollPerFramePx: Float,
) {
    // Set by rememberDragDropState on every composition: see its coveredTop and coveredBottom.
    internal var coveredTopPx = 0f
    internal var coveredBottomPx = 0f
    internal var topMarginPx = 0f

    // Set by rememberDragDropState on every composition: see its minIndex and onBlocked.
    internal var minIndex: Int? = null
    internal var onBlocked: () -> Unit = {}

    // Whether onBlocked has run in this drag.
    private var blockedShown = false

    private var draggedDistance by mutableFloatStateOf(0f)
    private var draggingItemInitialOffset by mutableIntStateOf(0)

    // The row is drawn where the finger holds it, whatever the list scrolled meanwhile: its start
    // offset plus the finger's travel are both on-screen positions, its slot the current layout.
    internal val draggingItemOffset: Float
        get() =
            slotOffset(currentIndexOfDraggedItem)?.let { slot ->
                draggedTop() - slot
            } ?: 0f

    /**
     * The top of the row at [minIndex] as laid out now (a row offset), or null when there is no
     * boundary or it is out of view (scrolled off above: the row is free until the edge scroll
     * brings it back). Rows don't move during a drag (the swap waits for the drop), only the list
     * scrolls. Drop targets above it are ruled out in findDropTarget whether it is in view or not.
     */
    private fun boundaryTop(): Float? {
        val min = minIndex ?: return null
        return state.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == min }
            ?.offset
            ?.toFloat()
    }

    // Where the row in hand is drawn, as a row offset: where the finger holds it, but no higher
    // than the minIndex boundary.
    private fun draggedTop(): Float {
        val top = draggingItemInitialOffset + draggedDistance
        val boundary = boundaryTop() ?: return top
        return maxOf(top, boundary)
    }

    private var autoScrollJob: Job? = null

    // When the current run of edge-scrolling frames began (frame time, ns); null while not scrolling.
    // ItemTouchHelper's mDragScrollStartTimeInMs.
    private var autoScrollStartNanos: Long? = null

    /**
     * Where the row at [index] is laid out now. Once the list has scrolled it out of view (its pin
     * keeps it placed), the list puts it right next to the visible rows, above the first or below
     * the last.
     */
    private fun slotOffset(index: Int?): Float? {
        index ?: return null
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        visible.firstOrNull { it.index == index }?.let { return it.offset.toFloat() }
        val first = visible.firstOrNull() ?: return null
        val last = visible.last()
        val size = initiallyDraggedElement?.size ?: return null
        return when {
            index < first.index -> (first.offset - info.mainAxisItemSpacing - size).toFloat()
            index > last.index -> (last.offsetEnd + info.mainAxisItemSpacing).toFloat()
            else -> null
        }
    }

    internal var previousIndexOfDraggedItem by mutableStateOf<Int?>(null)
        private set
    internal var previousItemOffset = Animatable(0f)
        private set

    // used to obtain initial offsets on drag start
    private var initiallyDraggedElement by mutableStateOf<LazyListItemInfo?>(null)

    var currentIndexOfDraggedItem by mutableStateOf<Int?>(null)

    private val initialOffsets: Pair<Int, Int>?
        get() = initiallyDraggedElement?.let { Pair(it.offset, it.offsetEnd) }

    private var currentSwapFromTo by mutableStateOf<Pair<Int, Int>?>(null)

    // A pointer y, measured from the list's top edge (content padding included), as a row offset:
    // those (LazyListItemInfo.offset) start after the top content padding (viewportStartOffset is
    // minus that padding).
    private fun toItemY(pointerY: Float): Float = pointerY + state.layoutInfo.viewportStartOffset

    /** The visible row under [offset], a pointer position in the list's own coordinates. */
    fun itemAt(offset: Offset): LazyListItemInfo? {
        val y = toItemY(offset.y)
        return state.layoutInfo.visibleItemsInfo.firstOrNull { item -> y >= item.offset && y < item.offsetEnd }
    }

    fun onDragStart(offset: Offset) {
        itemAt(offset)
            ?.also {
                currentIndexOfDraggedItem = it.index
                initiallyDraggedElement = it
                draggingItemInitialOffset = it.offset
                blockedShown = false
            }
    }

    fun onDragInterrupted(end: Boolean = false) {
        autoScrollJob?.cancel()
        autoScrollJob = null
        autoScrollStartNanos = null
        currentSwapFromTo?.let { (from, to) ->
            if (from != to && from >= 0 && to >= 0 && end) {
                Logger.d("QueueBottomSheet", "onDragInterrupted: $from, $to")
                onSwap(from, to)
            }
            currentIndexOfDraggedItem = to
        }
        currentSwapFromTo = null
        if (currentIndexOfDraggedItem != null) {
            previousIndexOfDraggedItem = currentIndexOfDraggedItem
            val startOffset = draggingItemOffset
            scope.launch {
                previousItemOffset.snapTo(startOffset)
                previousItemOffset.animateTo(
                    0f,
                    tween(easing = FastOutLinearInEasing),
                )
                previousIndexOfDraggedItem = null
            }
        }
        draggingItemInitialOffset = 0
        draggedDistance = 0f
        currentIndexOfDraggedItem = null
        initiallyDraggedElement = null
        blockedShown = false
    }

    fun onDrag(offset: Offset) {
        draggedDistance += offset.y
        findDropTarget()
        checkBlocked()
        if (autoScrollJob?.isActive != true && edgeOvershoot() != 0f) {
            autoScrollJob = scope.launch { autoScroll() }
        }
    }

    // The row the one in hand would take the place of, from where it is drawn now. Also run after
    // every auto-scroll step: the rows move under a finger that may be holding still.
    private fun findDropTarget() {
        val (topOffset, bottomOffset) = initialOffsets ?: return
        val current = currentIndexOfDraggedItem ?: return
        // Its own slot, which may by now be scrolled out of view.
        val hoveredOffset = slotOffset(current) ?: return
        // Where it is drawn: held at the minIndex boundary when pulled above it.
        val startOffset = draggedTop()
        val endOffset = startOffset + (bottomOffset - topOffset)
        val min = minIndex
        // Held at the boundary, it sits right on the first allowed row's slot: that is the target,
        // although its top never gets above that row's top as the check below wants. When that row
        // is the one in hand, it goes back to its own place.
        val boundary = boundaryTop()
        if (min != null && boundary != null && current >= min && draggingItemInitialOffset + draggedDistance < boundary) {
            currentSwapFromTo = Pair(current, min)
            return
        }

        state.layoutInfo.visibleItemsInfo
            .filterNot { item ->
                item.offsetEnd < startOffset || item.offset > endOffset || current == item.index ||
                    (min != null && item.index < min)
            }
            .apply {
                forEach { item ->
                    Logger.d("QueueBottomSheet", "onDrag: ${item.index}")
                }
            }.firstOrNull { item ->
                val delta = (startOffset - hoveredOffset)
                when {
                    delta > 0 -> (endOffset > item.offsetEnd)
                    else -> (startOffset < item.offset)
                }
            }?.also { item ->
                currentSwapFromTo = Pair(current, item.index)
            }
    }

    // Runs onBlocked, once per drag, when the finger has pulled the row well above the minIndex
    // boundary it is held at. Also run after every auto-scroll step: the boundary may scroll into
    // view under a finger that is holding still.
    private fun checkBlocked() {
        if (blockedShown || currentIndexOfDraggedItem == null) return
        val size = initiallyDraggedElement?.size ?: return
        val boundary = boundaryTop() ?: return
        if (draggingItemInitialOffset + draggedDistance < boundary - size * BLOCKED_PULL_FRACTION) {
            blockedShown = true
            onBlocked()
        }
    }

    /**
     * How far the row in hand, where it is drawn now, reaches past the list's visible top (negative)
     * or bottom (positive), in px; 0 when it doesn't. The visible top and bottom are inside its
     * content padding or what covers it, whichever reaches further. As in ItemTouchHelper's
     * scrollIfNecessary: the dragged row's own edges, not the finger, and the top only once the drag
     * has gone up overall (mDy < 0), the bottom only once it has gone down (mDy > 0).
     */
    private fun edgeOvershoot(): Float {
        if (currentIndexOfDraggedItem == null) return 0f
        val size = initiallyDraggedElement?.size ?: return 0f
        val info = state.layoutInfo
        // Its drawn top as a pointer y (from the list's top edge): toItemY the other way round.
        val rowTop = draggedTop() - info.viewportStartOffset
        val rowBottom = rowTop + size
        // The top starts AUTO_SCROLL_TOP_MARGIN lower, so scrolling up starts a little earlier.
        val visibleTop = maxOf(info.beforeContentPadding.toFloat(), coveredTopPx) + topMarginPx
        val visibleBottom = info.viewportSize.height - maxOf(info.afterContentPadding.toFloat(), coveredBottomPx)
        val topDiff = rowTop - visibleTop
        val bottomDiff = rowBottom - visibleBottom
        return when {
            draggedDistance < 0f && topDiff < 0f -> topDiff
            draggedDistance > 0f && bottomDiff > 0f -> bottomDiff
            else -> 0f
        }
    }

    // AndroidX ItemTouchHelper.interpolateOutOfBoundsScroll's curves: px to scroll in one 60 Hz frame,
    // eased out by how far past the edge the row is (a whole row height is the most) and eased in,
    // steeply, by how long the edge scroll has been going (full after AUTO_SCROLL_ACCELERATION_MS),
    // from AUTO_SCROLL_MIN_SPEED_FRACTION rather than from zero.
    private fun interpolateOutOfBoundsScroll(
        viewSize: Int,
        viewSizeOutOfBounds: Float,
        msSinceStartScroll: Long,
    ): Float {
        val direction = if (viewSizeOutOfBounds > 0f) 1 else -1
        val outOfBoundsRatio = if (viewSize > 0) min(1f, abs(viewSizeOutOfBounds) / viewSize) else 1f
        val cappedScroll = (direction * maxScrollPerFramePx * scrollCapInterpolation(outOfBoundsRatio)).toInt()
        val timeRatio =
            if (msSinceStartScroll > AUTO_SCROLL_ACCELERATION_MS) {
                1f
            } else {
                msSinceStartScroll.toFloat() / AUTO_SCROLL_ACCELERATION_MS
            }
        val timeFactor =
            AUTO_SCROLL_MIN_SPEED_FRACTION + (1f - AUTO_SCROLL_MIN_SPEED_FRACTION) * dragScrollInterpolation(timeRatio)
        val value = (cappedScroll * timeFactor).toInt()
        return if (value == 0) direction.toFloat() else value.toFloat()
    }

    // ItemTouchHelper's sDragViewScrollCapInterpolator (ease-out) and sDragScrollInterpolator (ease-in).
    private fun scrollCapInterpolation(t: Float): Float {
        val u = t - 1f
        return u * u * u * u * u + 1f
    }

    private fun dragScrollInterpolation(t: Float): Float = t * t * t * t * t

    // Scrolls a little every frame while the row in hand reaches past an edge it is dragged towards,
    // like ItemTouchHelper's scroll runnable. The timer starts on the first frame that scrolls and
    // starts over whenever a frame doesn't, so every edge scroll starts slow. Frame-rate independent:
    // the per-frame amount is for 16.67 ms and is scaled by the real frame time. Stops when the row
    // is back inside, the list can't go further, or the drag ends (onDragInterrupted cancels it).
    private suspend fun autoScroll() {
        var lastFrame = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val frameMs = ((now - lastFrame) / 1_000_000f).coerceAtMost(50f)
            lastFrame = now
            val overshoot = edgeOvershoot()
            val size = initiallyDraggedElement?.size
            if (overshoot == 0f || size == null) break
            val start = autoScrollStartNanos ?: now.also { autoScrollStartNanos = it }
            val perFrame = interpolateOutOfBoundsScroll(size, overshoot, (now - start) / 1_000_000L)
            val consumed = state.scrollBy(perFrame * frameMs / AUTO_SCROLL_FRAME_MS)
            if (consumed == 0f && frameMs > 0f) break
            findDropTarget()
            checkBlocked()
        }
        autoScrollStartNanos = null
    }
}