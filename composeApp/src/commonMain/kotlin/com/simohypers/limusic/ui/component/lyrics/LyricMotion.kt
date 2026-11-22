package com.simohypers.limusic.ui.component.lyrics

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.simohypers.limusic.extension.ParsedRichSyncLine
import java.text.BreakIterator
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

// How a synced lyric moves, on the lyrics page and in the line under the cover. Desktop's
// lyricmotion.ts (1.4.0), whose numbers are Better Lyrics' defaults (@braccato/core, MIT).

/** How much of the ink a word not sung yet keeps: clearly dimmer than a sung one (desktop #720). */
internal const val UNSUNG_ALPHA = 0.55f

/** A word the source gave no end runs this long when nothing comes after it. */
internal const val LAST_WORD_FALLBACK_MS = 600L

/** The soft edge of the sweep, in em: a short word and a long one light at the same softness. */
private const val FEATHER_EM = 0.5f

/** The space between two words, in em, desktop's. */
private const val WORD_GAP_EM = 0.26f

/** When word [i] ends: its own end, else when the next one starts, else the line's end. */
internal fun ParsedRichSyncLine.endOf(i: Int): Long {
    val w = words[i]
    w.endTimeMs?.takeIf { it > w.startTimeMs }?.let { return it }
    words.getOrNull(i + 1)?.let { return it.startTimeMs }
    return lineEndTimeMs.takeIf { it != Long.MAX_VALUE && it > w.startTimeMs } ?: (w.startTimeMs + LAST_WORD_FALLBACK_MS)
}

/** How far the singer has got through a span, 0 to 1. */
internal fun progress(
    startMs: Long,
    endMs: Long,
    ms: Long,
): Float {
    val span = endMs - startMs
    if (span <= 0) return if (ms >= startMs) 1f else 0f
    return ((ms - startMs).toFloat() / span).coerceIn(0f, 1f)
}

/** One letter of a held note, with its share of the note's time. */
internal data class Letter(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

/** A held note: long enough that its letters can light and lift one by one. */
private const val HELD_MS = 1000L
private const val MAX_LETTERS = 12

/**
 * A held word's letters, each with its share of the word's time, or null for a word that lights
 * whole. Only Latin, Greek and Cyrillic split: a joined script (Arabic, Devanagari, Thai...) split
 * into boxes would lose its shaping.
 */
internal fun heldLetters(
    text: String,
    startMs: Long,
    endMs: Long,
): List<Letter>? {
    val span = endMs - startMs
    if (span < HELD_MS || text.isEmpty() || !text.separable()) return null
    val parts = graphemes(text)
    if (parts.size !in 2..MAX_LETTERS) return null
    return parts.mapIndexed { i, s -> Letter(s, startMs + span * i / parts.size, startMs + span * (i + 1) / parts.size) }
}

private fun String.separable(): Boolean {
    var i = 0
    while (i < length) {
        val cp = codePointAt(i)
        val script = Character.UnicodeScript.of(cp)
        val ok =
            script == Character.UnicodeScript.LATIN ||
                script == Character.UnicodeScript.GREEK ||
                script == Character.UnicodeScript.CYRILLIC ||
                when (Character.getType(cp).toByte()) {
                    Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK,
                    Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER,
                    Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
                    Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
                    Character.OTHER_PUNCTUATION,
                    -> true
                    else -> false
                }
        if (!ok) return false
        i += Character.charCount(cp)
    }
    return true
}

private fun graphemes(text: String): List<String> {
    val it = BreakIterator.getCharacterInstance()
    it.setText(text)
    val out = mutableListOf<String>()
    var start = it.first()
    var end = it.next()
    while (end != BreakIterator.DONE) {
        out += text.substring(start, end)
        start = end
        end = it.next()
    }
    return out
}

/**
 * A held note's letter glows from when it is reached until a moment after the whole note ends,
 * so the glow builds across the note and lets go of it at once.
 */
internal fun glowing(
    letterStartMs: Long,
    noteEndMs: Long,
    ms: Long,
): Boolean = ms >= letterStartMs && ms < noteEndMs + 150

/** The page's scroll easing: a slow start, a quick middle, a long settle. */
internal val ScrollEase = CubicBezierEasing(0.86f, 0f, 0.2f, 1f)

/**
 * How long a line [rel] lines from the one scrolled to takes to get there. The lines ahead of the
 * motion arrive first, the ones trailing it later the further back they are, so the column moves
 * with some give instead of as one block.
 */
internal fun scrollMs(
    rel: Int,
    ahead: Boolean,
): Float {
    val n = abs(rel)
    if (ahead && n > 0) return 750f + min(n, 6) * 20f
    return 750f + ln(n + 2f) * 80f + (n + 1) * 20f
}

/** Where the line being sung sits on the page: a bit above the middle, so more of what's coming shows. */
internal const val LYRICS_FOCUS = 0.37f

/** How far ahead of a line the page sets off, so the line lands as the singer reaches it. */
internal const val SCROLL_LEAD_MS = 500L

private val BASE_MS = scrollMs(0, false)

/**
 * Desktop's lyrics scroll: the page moves so the line sits at [LYRICS_FOCUS], and every line on
 * screen eases from where it was on its own time ([scrollMs]). A glide that starts while one runs
 * adds to it, so lines that come fast keep moving instead of restarting from rest.
 *
 * A LazyColumn only has the rows it shows, so the list itself scrolls on the curve of the first row
 * to arrive and every row draws itself back ([offsetPx]) by what its own curve hasn't covered yet.
 * Rows only ever trail, into space the list has already laid out, so nothing pops in at an edge.
 */
@Stable
internal class LyricsGlide {
    private class Run(
        val delta: Float,
        val target: Int,
        // A jump of several lines tightens the stagger, so the lines don't run into each other.
        val stagger: Float,
        // The furthest rows on screen land by then; rows past them land with them.
        lastRow: Int,
        firstRow: Int,
    ) {
        var startNanos = -1L
        val leadMs = BASE_MS + (scrollMs(1, true) - BASE_MS) * stagger
        val maxMs = max(rowMs(firstRow, Float.MAX_VALUE), rowMs(lastRow, Float.MAX_VALUE))

        fun rowMs(
            row: Int,
            cap: Float = maxMs,
        ): Float {
            val rel = row - target
            val ahead = if (delta > 0) rel < 0 else rel > 0
            return min(cap, BASE_MS + (scrollMs(rel, ahead) - BASE_MS) * stagger)
        }

        fun eased(
            ms: Float,
            nowNanos: Long,
        ): Float {
            if (startNanos < 0) return 0f
            return ScrollEase.transform(((nowNanos - startNanos) / 1e6f / ms).coerceIn(0f, 1f))
        }
    }

    private val runs = mutableListOf<Run>()

    // Read by the rows' graphicsLayer, so a glide re-runs only those layers: no recomposition, no layout.
    private var count by mutableIntStateOf(0)
    private var nowNanos by mutableLongStateOf(0L)

    // How much of the running glides the list has scrolled.
    private var scrolled = 0f

    /** How far below (or above) its laid-out place row [index] is drawn right now, in px. */
    fun offsetPx(index: Int): Float {
        if (count == 0) return 0f
        val now = nowNanos
        var off = 0f
        for (r in runs) off += r.delta * (r.eased(r.leadMs, now) - r.eased(r.rowMs(index), now))
        return off
    }

    /** Stops every glide; the rows go back to where the list has them (a finger took the list). */
    fun clear() {
        runs.clear()
        count = 0
        scrolled = 0f
    }

    /** Moves row [index] to [LYRICS_FOCUS]: a glide when it is on screen and [smooth], else a jump. */
    suspend fun goTo(
        state: LazyListState,
        index: Int,
        smooth: Boolean,
    ) {
        val info = state.layoutInfo
        val h = info.viewportSize.height.toFloat()
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        if (item == null || !smooth || h <= 0f) {
            // ponytail: a line off screen (a seek, a scroll by hand) jumps; desktop glides it in
            // from a viewport away, which a LazyColumn can't without laying out what's in between.
            clear()
            if (item == null) {
                state.scrollToItem(index)
                withFrameNanos { }
            }
            val now = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
            state.scrollBy(now.offset + now.size / 2f - LYRICS_FOCUS * state.layoutInfo.viewportSize.height)
            return
        }
        val wanted = item.offset + item.size / 2f - LYRICS_FOCUS * h
        state.scroll {
            // What the running glides have still to scroll, then how far the list can really go:
            // asked and handed straight back, so the page never glides past either end.
            val pending = runs.sumOf { (it.delta * (1f - it.eased(it.leadMs, nowNanos))).toDouble() }.toFloat()
            val got = scrollBy(wanted)
            scrollBy(-got)
            val delta = got - pending
            if (abs(delta) >= 1f) {
                val near =
                    listOfNotNull(
                        info.visibleItemsInfo.firstOrNull { it.index == index - 1 },
                        info.visibleItemsInfo.firstOrNull { it.index == index + 1 },
                    ).map { abs(it.offset - item.offset).toFloat() }
                val step = near.minOrNull() ?: item.size.toFloat()
                val first = info.visibleItemsInfo.firstOrNull()?.index ?: index
                val last = (info.visibleItemsInfo.lastOrNull()?.index ?: index) + 2
                runs += Run(delta, index, min(1f, step / abs(delta)), last, first)
                count = runs.size
            }
            while (runs.isNotEmpty()) {
                withFrameNanos { now ->
                    for (r in runs) if (r.startNanos < 0) r.startNanos = now
                    nowNanos = now
                    var target = 0f
                    for (r in runs) target += r.delta * r.eased(r.leadMs, now)
                    scrolled += scrollBy(target - scrolled)
                    // A run whose last row has landed is done: its whole delta is scrolled.
                    val done = runs.filter { now - it.startNanos >= it.maxMs * 1e6f }
                    if (done.isNotEmpty()) {
                        for (r in done) scrolled -= r.delta
                        runs.removeAll(done)
                        count = runs.size
                    }
                }
            }
            scrolled = 0f
        }
    }
}

/**
 * The playhead a sweep is drawn from. Positions arrive every 50 ms, a frame or two late and never
 * on a frame; restarting from each one makes a continuous sweep stall and then jump by that much
 * several times a second. This one keeps its own time on the frame clock and only leans toward the
 * reports, so it moves at a steady speed and never steps back. A report further off than
 * [SWEEP_SNAP_MS] is a seek, and it goes straight there. While [running] is false it simply
 * follows the reports. [rawMs] is read on the frame clock, never in composition, so a caller
 * holding the position as state doesn't recompose on every report.
 */
@Composable
internal fun rememberSweepPlayhead(
    rawMs: () -> Long,
    running: Boolean,
): State<Long> {
    val raw by rememberUpdatedState(rawMs)
    val playhead = remember { mutableLongStateOf(raw()) }
    LaunchedEffect(running) {
        if (!running) {
            snapshotFlow { raw() }.collect { playhead.longValue = it }
            return@LaunchedEffect
        }
        var reported = raw()
        var reportedAt = -1L
        var lastFrame = -1L
        var position = playhead.longValue.toDouble()
        while (true) {
            withFrameNanos { now ->
                val latest = raw()
                if (reportedAt < 0L || latest != reported) {
                    reported = latest
                    reportedAt = now
                }
                // Where the last report puts the song by now. Capped, so a player that has stalled
                // (no new report while it buffers) cannot drag the light on without it.
                val target = reported + ((now - reportedAt) / 1_000_000.0).coerceAtMost(SWEEP_MAX_EXTRAPOLATION_MS)
                val step = if (lastFrame < 0L) 0.0 else (now - lastFrame) / 1_000_000.0
                lastFrame = now
                position =
                    if (abs(target - position) > SWEEP_SNAP_MS) {
                        target
                    } else {
                        // Close a share of the gap each frame, in proportion to the frame's length so
                        // 60 Hz and 120 Hz settle alike; max() keeps it from ever running backwards.
                        val advanced = position + step
                        max(position, advanced + (target - advanced) * (step / SWEEP_CATCH_UP_MS).coerceAtMost(1.0))
                    }
                playhead.longValue = position.roundToLong()
            }
        }
    }
    return playhead
}

// How the sweep's own clock tracks the reported position: the time it takes to close most of a
// gap, the largest gap it closes gradually rather than jumping, and how far past the last report
// it will run on its own.
private const val SWEEP_CATCH_UP_MS = 150.0
private const val SWEEP_SNAP_MS = 500.0
private const val SWEEP_MAX_EXTRAPOLATION_MS = 250.0

/**
 * A word-synced line's words, wrapping like text. On the line being sung ([active]) each word is lit
 * up to the singer with a soft edge half an em wide and dim after it, and a held note lights,
 * crests and, with [halo] (dark themes only: on a light one a glow is a dark smudge), glows letter
 * by letter. Off it the words are plain [ink], dimmed by the line. [singleLine] keeps them on one
 * row for a marquee.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SungWords(
    line: ParsedRichSyncLine,
    active: Boolean,
    playhead: State<Long>,
    style: TextStyle,
    ink: Color,
    halo: Boolean,
    modifier: Modifier = Modifier,
    far: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    singleLine: Boolean = false,
) {
    val dim = ink.copy(alpha = ink.alpha * UNSUNG_ALPHA)
    val gap = with(LocalDensity.current) { (style.fontSize * WORD_GAP_EM).toDp() }
    val words: @Composable () -> Unit = {
        line.words.forEachIndexed { i, w ->
            SungWord(w.text, w.startTimeMs, line.endOf(i), active, playhead, style, ink, dim, halo)
        }
    }
    if (singleLine) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Bottom) { words() }
    } else {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap, if (far) Alignment.End else Alignment.Start),
            maxLines = maxLines,
        ) { words() }
    }
}

@Composable
private fun SungWord(
    text: String,
    startMs: Long,
    endMs: Long,
    active: Boolean,
    playhead: State<Long>,
    style: TextStyle,
    lit: Color,
    dim: Color,
    halo: Boolean,
) {
    val held = remember(text, startMs, endMs) { heldLetters(text, startMs, endMs) }
    if (held == null) {
        SweptText(text, startMs, endMs, active, playhead, style, lit, dim)
        return
    }
    // A held word is its letters whether its line is sung or not, so it never reflows when the line
    // begins. Left to right inside a right-to-left line too: only left-to-right scripts split.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row {
            for (l in held) HeldLetter(l, endMs, active, playhead, style, lit, dim, halo)
        }
    }
}

private enum class Phase { UNSUNG, SINGING, SUNG }

/** One run of text: dim before its time, swept during it, lit after; plain [lit] off the sung line. */
@Composable
private fun SweptText(
    text: String,
    startMs: Long,
    endMs: Long,
    active: Boolean,
    playhead: State<Long>,
    style: TextStyle,
    lit: Color,
    dim: Color,
    onLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    // Read off the playhead only when it changes, so a line recomposes as its words begin and end,
    // not every frame. Only the word being sung reads the playhead per frame, in its draw.
    val phase by remember(startMs, endMs, active, playhead) {
        derivedStateOf {
            if (!active) return@derivedStateOf Phase.SUNG
            val ms = playhead.value
            when {
                ms >= endMs -> Phase.SUNG
                ms < startMs -> Phase.UNSUNG
                else -> Phase.SINGING
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val featherPx = with(LocalDensity.current) { style.fontSize.toPx() } * FEATHER_EM
    Text(
        text = text,
        style = style.copy(textDirection = TextDirection.Content),
        color = if (phase == Phase.UNSUNG) dim else lit,
        maxLines = 1,
        softWrap = false,
        onTextLayout = {
            layout = it
            onLayout?.invoke(it)
        },
        modifier =
            if (phase != Phase.SINGING) {
                Modifier
            } else {
                Modifier.drawWithContent {
                    val measured = layout ?: return@drawWithContent drawContent()
                    val p = progress(startMs, endMs, playhead.value)
                    val w = size.width
                    // The lit stop runs from one feather before the word to its end, the dim stop
                    // from its start to one feather after, so the edge is always a feather wide and
                    // nothing shows lit at p = 0.
                    val litEnd = p * w - (1f - p) * featherPx
                    val dimStart = p * w + p * featherPx
                    val rtl = measured.getParagraphDirection(0) == ResolvedTextDirection.Rtl
                    val brush =
                        if (rtl) {
                            Brush.horizontalGradient(listOf(lit, dim), startX = w - litEnd, endX = w - dimStart)
                        } else {
                            Brush.horizontalGradient(listOf(lit, dim), startX = litEnd, endX = dimStart)
                        }
                    drawText(measured, brush = brush)
                }
            },
    )
}

// A held note's crest: up and a little larger by 40% of the way, then back onto the line.
private const val CREST_MS = 900
private const val CREST_RISE_EM = 0.12f
private const val CREST_GROW = 0.12f
private val CrestEase = CubicBezierEasing(0.3f, 0.7f, 0.4f, 1f)

private fun crest(t: Float): Float =
    if (t < 0.4f) CrestEase.transform(t / 0.4f) else 1f - CrestEase.transform((t - 0.4f) / 0.6f)

/**
 * A held note's letter: lit on its own share of the note, cresting as it is reached, and on dark
 * themes a halo behind it that builds as the letter lights and lets go slowly once the note ends.
 * The halo is the letter in clear ink under a text shadow, drawn under the letter, never a layer
 * alpha: a layer would cut the bloom off at the letter's box.
 */
@Composable
private fun HeldLetter(
    letter: Letter,
    noteEndMs: Long,
    active: Boolean,
    playhead: State<Long>,
    style: TextStyle,
    lit: Color,
    dim: Color,
    halo: Boolean,
) {
    val fontPx = with(LocalDensity.current) { style.fontSize.toPx() }
    val reached by remember(active, playhead, letter.startMs) {
        derivedStateOf { active && playhead.value >= letter.startMs }
    }
    val rise = remember { Animatable(0f) }
    LaunchedEffect(reached) {
        if (reached) rise.animateTo(1f, tween(CREST_MS, easing = LinearEasing)) else rise.snapTo(0f)
    }
    val lighting by remember(active, halo, playhead, letter.startMs, noteEndMs) {
        derivedStateOf { active && halo && glowing(letter.startMs, noteEndMs, playhead.value) }
    }
    val glow = animateFloatAsState(if (lighting) 1f else 0f, tween(if (lighting) 300 else 1200, easing = EaseOut), label = "heldGlow")
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Box(
        Modifier
            .graphicsLayer {
                val k = crest(rise.value)
                translationY = -CREST_RISE_EM * fontPx * k
                scaleX = 1f + CREST_GROW * k
                scaleY = scaleX
            }.drawBehind {
                val a = glow.value
                val measured = layout ?: return@drawBehind
                if (a <= 0f) return@drawBehind
                drawText(measured, color = Color.Transparent, shadow = Shadow(lit.copy(alpha = 0.55f * a), blurRadius = 0.18f * fontPx))
                drawText(measured, color = Color.Transparent, shadow = Shadow(lit.copy(alpha = 0.3f * a), blurRadius = 0.55f * fontPx))
            },
    ) {
        SweptText(letter.text, letter.startMs, letter.endMs, active, playhead, style, lit, dim) { layout = it }
    }
}

/** What a timed line with nothing sung shows: an instrumental break. */
internal const val BREAK_NOTE = "♪"

/**
 * An instrumental break's note. On the line being sung ([active]) it pulses, and holds still where
 * it is while the song is paused.
 */
@Composable
internal fun BreakNote(
    active: Boolean,
    playing: Boolean,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(active, playing) {
        if (!active) {
            pulse.snapTo(0f)
            return@LaunchedEffect
        }
        if (!playing) return@LaunchedEffect
        while (true) {
            pulse.animateTo(1f, tween(800, easing = EaseInOut))
            pulse.animateTo(0f, tween(800, easing = EaseInOut))
        }
    }
    Text(
        text = BREAK_NOTE,
        style = style,
        color = color,
        modifier =
            modifier.graphicsLayer {
                if (active) {
                    val v = pulse.value
                    scaleX = 1f + 0.18f * v
                    scaleY = scaleX
                    alpha = 0.55f + 0.45f * v
                }
            },
    )
}

private val QuintOut = Easing { 1f - (1f - it).let { r -> r * r * r * r * r } }
private val CubicOut = Easing { 1f - (1f - it).let { r -> r * r * r } }
private const val HANDOFF_MS = 420

/**
 * The handoff between two lines in a one-line slot (Better Lyrics' stage): the old line rises away
 * and is gone in 220 ms, out of the way, while the new one rises in from a third of the slot below
 * a beat later and sharpens out of a slight blur ([handoffBlur]) as it settles.
 */
internal fun AnimatedContentTransitionScope<*>.lyricHandoff(): ContentTransform {
    val enter =
        slideInVertically(tween(HANDOFF_MS, easing = QuintOut)) { (it * 0.35f).toInt() } +
            fadeIn(tween(300, delayMillis = 70, easing = CubicOut))
    val exit =
        slideOutVertically(tween(HANDOFF_MS, easing = QuintOut)) { -(it * 0.35f).toInt() } +
            fadeOut(tween(220, easing = LinearEasing))
    return (enter togetherWith exit).using(SizeTransform(clip = false))
}

/** The blur a line comes in out of and goes out into, with [lyricHandoff]. Android 12 and up. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun AnimatedVisibilityScope.handoffBlur(): Modifier {
    val blur by transition.animateDp(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) tween(300, delayMillis = 70, easing = CubicOut) else tween(220, easing = LinearEasing)
        },
        label = "handoffBlur",
    ) { if (it == EnterExitState.Visible) 0.dp else 3.dp }
    return if (blur > 0.dp) Modifier.blur(blur, BlurredEdgeTreatment.Unbounded) else Modifier
}
