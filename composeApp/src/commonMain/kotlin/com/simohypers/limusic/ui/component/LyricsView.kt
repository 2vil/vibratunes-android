package com.simohypers.limusic.ui.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simohypers.limusic.domain.data.model.lyrics.RomanizationDictionaryState
import com.simohypers.limusic.domain.data.model.lyrics.RomanizationLanguage
import com.simohypers.limusic.domain.data.model.metadata.Line
import com.simohypers.limusic.domain.data.model.streams.TimeLine
import com.simohypers.limusic.domain.manager.DataStoreManager
import com.simohypers.limusic.domain.repository.LyricsRomanizerRepository
import com.simohypers.limusic.extension.farLines
import com.simohypers.limusic.extension.parseRichSyncWords
import com.simohypers.limusic.ui.component.lyrics.BREAK_NOTE
import com.simohypers.limusic.ui.component.lyrics.BreakNote
import com.simohypers.limusic.ui.component.lyrics.LyricsGlide
import com.simohypers.limusic.ui.component.lyrics.SCROLL_LEAD_MS
import com.simohypers.limusic.ui.component.lyrics.SungWords
import com.simohypers.limusic.ui.component.lyrics.UNSUNG_ALPHA
import com.simohypers.limusic.ui.component.lyrics.rememberSweepPlayhead
import com.simohypers.limusic.ui.screen.player.content.ClassicLyricsPage
import com.simohypers.limusic.ui.screen.player.content.NowPlayingContentActions
import com.simohypers.limusic.ui.screen.player.content.NowPlayingContentState
import com.simohypers.limusic.ui.screen.player.content.stripRichSyncTimestamps
import com.simohypers.limusic.ui.theme.typo
import com.simohypers.limusic.viewModel.NowPlayingScreenData
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.max

/** How far into a tapped line the seek lands; under anything heard, over the seek's rounding. */
private const val SEEK_INTO_LINE_MS = 50L

/**
 * How lines not being sung read. One coming up is exactly as strong as the unsung words of the
 * line being sung ([UNSUNG_ALPHA]), so a line that begins doesn't blink; a line already sung is
 * dimmer than both.
 */
private const val PAST_LINE_ALPHA = 0.35f

/** Every line but the one being sung sits a step smaller, so that one grows into place. */
private const val RESTING_SCALE = 0.96f
private const val LINE_MS = 500
private val LineEase = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)

/**
 * The sheet's lines as they are rendered. A timed line with nothing sung (an instrumental break, as
 * Limusic Lyrics and many LRC files mark one) shows a note, as on desktop. A gap between two lines
 * shows nothing, also as on desktop: SimpMusic's dots in every 3 s gap flashed between held notes.
 */
private fun displayLinesOf(
    lines: List<Line>?,
    syncType: String?,
): List<Line> {
    val source = lines.orEmpty()
    if (syncType != "LINE_SYNCED" && syncType != "RICH_SYNCED") return source
    return source.map { l ->
        if (l.startTimeMs.toLongOrNull() == null || l.words.isNotBlank() || l.background != null) l else l.copy(words = BREAK_NOTE)
    }
}

internal data class TimedLineIndex(
    val index: Int,
    val startTimeMs: Long,
)

/**
 * Returns the original line index of the last [TimedLineIndex] whose [TimedLineIndex.startTimeMs]
 * is `<= nowMs`. Assumes the receiver is sorted ascending by [TimedLineIndex.startTimeMs].
 *
 * Rules:
 *  - empty list -> -1
 *  - nowMs strictly before the first start time -> -1
 *  - nowMs after the last start time -> the last entry's original index (sticky last line)
 */
internal fun List<TimedLineIndex>.activeIndexAt(nowMs: Long): Int {
    if (isEmpty()) return -1
    if (nowMs < first().startTimeMs) return -1
    // Binary search for the last item whose startTimeMs <= nowMs.
    var lo = 0
    var hi = size - 1
    var ans = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (this[mid].startTimeMs <= nowMs) {
            ans = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return if (ans >= 0) this[ans].index else -1
}

/**
 * Builds a [Map] from each ORIGINAL line index to its closest synced translated `words`
 * within [thresholdMs]. Two-pointer over both sorted lists; on ties an earlier translated
 * line (smaller startTimeMs in the sorted list) wins for determinism.
 *
 * Lines with invalid `startTimeMs` on either side are skipped.
 */
private fun buildSyncedTranslatedWordsByLineIndex(
    originalLines: List<com.simohypers.limusic.domain.data.model.metadata.Line>,
    translatedLines: List<com.simohypers.limusic.domain.data.model.metadata.Line>,
    thresholdMs: Long = 1000L,
): Map<Int, String> {
    if (originalLines.isEmpty() || translatedLines.isEmpty()) return emptyMap()

    // Sort translated entries by start time. We keep the original list order as a
    // tie-breaker via stable sort: the FIRST translated line in the SORTED list wins
    // when the time delta is equal.
    val sortedTranslated =
        translatedLines
            .mapNotNull { line ->
                val ts = line.startTimeMs.toLongOrNull() ?: return@mapNotNull null
                ts to line.words
            }.sortedBy { it.first }

    if (sortedTranslated.isEmpty()) return emptyMap()

    // Original lines paired with their parsed timestamp + original index, sorted by time.
    data class OriginalEntry(val index: Int, val ts: Long)

    val sortedOriginal =
        originalLines
            .mapIndexedNotNull { index, line ->
                val ts = line.startTimeMs.toLongOrNull() ?: return@mapIndexedNotNull null
                OriginalEntry(index, ts)
            }.sortedBy { it.ts }

    if (sortedOriginal.isEmpty()) return emptyMap()

    val result = HashMap<Int, String>(sortedOriginal.size)
    var j = 0
    for (orig in sortedOriginal) {
        // Advance j so that sortedTranslated[j] is the first translated entry with ts >= orig.ts,
        // or the last entry if everything is smaller.
        while (j + 1 < sortedTranslated.size && sortedTranslated[j + 1].first <= orig.ts) {
            j++
        }
        // Candidate window: j and j+1 (the next one), pick whichever is closer.
        val candA = sortedTranslated[j]
        val diffA = abs(candA.first - orig.ts)
        var bestTs = candA.first
        var bestWords = candA.second
        var bestDiff = diffA
        if (j + 1 < sortedTranslated.size) {
            val candB = sortedTranslated[j + 1]
            val diffB = abs(candB.first - orig.ts)
            // Tie-break: prefer earlier (smaller startTimeMs) translated line.
            if (diffB < bestDiff) {
                bestTs = candB.first
                bestWords = candB.second
                bestDiff = diffB
            }
        }
        if (bestDiff < thresholdMs) {
            result[orig.index] = bestWords
            // Suppress unused warning while keeping the chosen ts visible for future tweaks.
            @Suppress("UNUSED_VARIABLE")
            val _bt = bestTs
        }
    }
    return result
}

/**
 * Synced lyrics as desktop Limusic draws them (1.4.0, after Better Lyrics): left, bold, in [ink];
 * the line being sung full size and strength, the rest a step smaller and dimmed. The page sets off
 * [SCROLL_LEAD_MS] before a line, puts it a bit above the middle and lets every line glide there on
 * its own time ([LyricsGlide]). Word-synced lines sweep as they are sung, held notes crest and on a
 * dark page glow ([SungWords]). Plain lyrics (no usable timings) read as text, smaller.
 */
@Composable
fun LyricsView(
    lyricsData: NowPlayingScreenData.LyricsData,
    timeLine: StateFlow<TimeLine>,
    onLineClick: (Float) -> Unit,
    modifier: Modifier = Modifier,
    // Optional trailing slot rendered as the LAST list item, so a caller's caption scrolls with
    // the lyrics instead of sitting anchored below them.
    footerContent: (@Composable () -> Unit)? = null,
    textScale: Float = 1f,
    showRomanized: Boolean = true,
    // The theme's by default. The Immersive player, always dark, passes white.
    ink: Color = MaterialTheme.colorScheme.onSurface,
    // A break's note pulses while the song plays and holds still while it's paused.
    playing: Boolean = true,
    dataStoreManager: DataStoreManager = koinInject(),
    romanizer: LyricsRomanizerRepository = koinInject(),
) {
    val listState = rememberLazyListState()
    val current by timeLine.collectAsStateWithLifecycle()

    // The listener's audio-delay correction, applied HERE rather than to the lyric rows: the rows
    // are cached in Room and shared with the community lyrics database, so a local correction must
    // never be written into them. SUBTRACTED: Bluetooth buffers, so at player position P the ear is
    // hearing P - offset, and that earlier moment is the one the sheet must light up for.
    val globalLyricsOffsetMs by dataStoreManager.lyricsOffsetMs.collectAsStateWithLifecycle(0)
    // Plus this song's own nudge from the source picker.
    val songLyricsOffsetMs = lyricsData.offsetMs

    // A tap on a line plays from it. Aimed at where the lyrics clock (the player less both offsets)
    // reads the line's start, and a little inside it: the seek goes through a Float percentage, and
    // landing a few ms early lit the line before it first, a flash on every tap.
    fun seekToLine(line: Line) {
        val total = timeLine.value.total.takeIf { it > 0 } ?: return
        val at = (line.startTimeMs.toLongOrNull() ?: return) + globalLyricsOffsetMs + songLyricsOffsetMs + SEEK_INTO_LINE_MS
        onLineClick(at.coerceIn(0, total).toFloat() * 100 / total)
    }

    // The stored value is a comma-separated list of enum names; parsing it per recomposition would
    // rebuild the set on every line change, hence the remember on the raw string.
    val romanizationStored by dataStoreManager.romanizationLanguages.collectAsStateWithLifecycle("")
    val romanizationLanguages =
        remember(romanizationStored) { RomanizationLanguage.parse(romanizationStored) }
    // Lyrics on screen with Japanese selected is when the dictionary pack is needed, so a missing
    // one is fetched from here (after a reinstall that restored a backup the selection is back but
    // the pack is not). Keyed on the lyrics too, so a failed attempt is tried again on a later song.
    LaunchedEffect(romanizationLanguages, lyricsData.lyrics) {
        if (RomanizationLanguage.JAPANESE in romanizationLanguages) romanizer.ensureJapaneseDictionary()
    }
    // A Japanese line read before the pack is on disk comes back null; keyed on this, the visible
    // lines are read again the moment the pack lands.
    val japaneseDictionaryState by romanizer.japaneseDictionaryState.collectAsStateWithLifecycle()
    val japaneseDictionaryReady by remember {
        derivedStateOf { japaneseDictionaryState == RomanizationDictionaryState.READY }
    }

    val displayLines =
        remember(lyricsData.lyrics.lines, lyricsData.lyrics.syncType) {
            displayLinesOf(lyricsData.lyrics.lines, lyricsData.lyrics.syncType)
        }
    val syncType = lyricsData.lyrics.syncType
    val synced = syncType == "LINE_SYNCED" || syncType == "RICH_SYNCED"

    // Limusic Lyrics' duets: the lines that sit on the far side. Empty for every other lyric.
    val farLines = remember(displayLines) { farLines(displayLines) }
    val duet = farLines.isNotEmpty()

    val timedLineIndexes =
        remember(displayLines) {
            val timed =
                displayLines.mapIndexedNotNull { index, line ->
                    line.startTimeMs.toLongOrNull()?.let { TimedLineIndex(index, it) }
                }
            // An unsynced sheet still carries a startTimeMs on every line, the literal "0". One
            // distinct value cannot order anything, whatever the sheet calls itself, so the list is
            // empty and no line is ever the sung one.
            if (timed.distinctBy { it.startTimeMs }.size <= 1) emptyList() else timed.sortedBy { it.startTimeMs }
        }
    // No usable timings: every line reads as plain text.
    val plain = timedLineIndexes.isEmpty()

    // The lyrics clock: the player less both offsets.
    fun lyricsMs(): Long = current.current - globalLyricsOffsetMs - songLyricsOffsetMs

    val currentLineIndex by remember(timedLineIndexes, songLyricsOffsetMs) {
        derivedStateOf {
            val now = lyricsMs()
            if (now <= 0L) -1 else timedLineIndexes.activeIndexAt(now)
        }
    }
    // The page moves to a line before it is sung, so the line lands as the singer reaches it rather
    // than sliding in under the first word. Its highlight still waits for its cue.
    val scrollLineIndex by remember(timedLineIndexes, songLyricsOffsetMs) {
        derivedStateOf {
            val now = lyricsMs() + SCROLL_LEAD_MS
            if (now <= 0L) -1 else timedLineIndexes.activeIndexAt(now)
        }
    }
    val playhead = rememberSweepPlayhead({ lyricsMs() }, running = playing && syncType == "RICH_SYNCED")
    // A glow on a light page is a dark smudge, so held notes glow only on a dark one.
    val halo = ink.luminance() > 0.5f

    val syncedTranslatedWordsByLineIndex =
        remember(displayLines, lyricsData.translatedLyrics?.first?.lines) {
            buildSyncedTranslatedWordsByLineIndex(
                originalLines = displayLines,
                translatedLines = lyricsData.translatedLyrics?.first?.lines.orEmpty(),
                thresholdMs = 1000L,
            )
        }

    // Per sheet: a new song's first placement jumps, like opening the page mid-song.
    val glide = remember(displayLines) { LyricsGlide() }
    var placed by remember(displayLines) { mutableStateOf(false) }
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) { if (dragged) glide.clear() }
    LaunchedEffect(scrollLineIndex, synced, glide) {
        if (scrollLineIndex < 0 || !synced) return@LaunchedEffect
        val smooth = placed
        placed = true
        glide.goTo(listState, scrollLineIndex, smooth)
    }

    BoxWithConstraints(modifier = modifier) {
        // Room below the closing lines, so they can still rise to where the sung line sits.
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = maxHeight * 0.5f),
        ) {
            items(displayLines.size) { index ->
                val line = displayLines[index]
                val words = line.words
                val translatedWords =
                    if (synced) {
                        syncedTranslatedWordsByLineIndex[index]
                    } else {
                        lyricsData.translatedLyrics?.first?.lines?.getOrNull(index)?.words
                    }
                // Romanization is its own row under the original, so the reader follows the original
                // script and still knows how to say it. Stripped of word stamps first, or the
                // timestamps themselves would be transliterated.
                val romanizedWords =
                    if (romanizationLanguages.isEmpty() || !showRomanized) {
                        null
                    } else {
                        remember(words, romanizationLanguages, japaneseDictionaryReady) {
                            romanizer.romanize(if (syncType == "RICH_SYNCED") words.stripRichSyncTimestamps() else words, romanizationLanguages)
                        }
                    }
                LyricLine(
                    words = words,
                    line = line,
                    far = index in farLines,
                    duet = duet,
                    syncType = syncType,
                    romanized = romanizedWords,
                    translated = translatedWords,
                    isCurrent = index == currentLineIndex,
                    isPast = currentLineIndex >= 0 && index < currentLineIndex,
                    plain = plain,
                    playhead = playhead,
                    playing = playing,
                    textScale = textScale,
                    ink = ink,
                    halo = halo,
                    onClick = if (plain) null else ({ seekToLine(line) }),
                    modifier = Modifier.graphicsLayer { translationY = glide.offsetPx(index) },
                )
            }
            footerContent?.let { footer ->
                item {
                    // The row after the last line: it glides with the lines, or they'd run into it.
                    Box(Modifier.graphicsLayer { translationY = glide.offsetPx(displayLines.size) }) { footer() }
                }
            }
        }
    }
}

/**
 * One line, as desktop Limusic draws it: the heading face, bold, on its singer's side. The line
 * being sung is full strength the moment it begins, so its unsung words read exactly as they did a
 * frame earlier as the line coming up, and only what is sung brightens; it grows from a step smaller
 * into place. Dimming is the line's alpha, so a word-timed line's sweep and its romanization fade
 * with it, and only the way out fades.
 */
@Composable
private fun LyricLine(
    words: String,
    line: Line,
    // A duet's other singer, on the far side; [duet] keeps both singers off each other's edge.
    far: Boolean,
    duet: Boolean,
    syncType: String?,
    romanized: String?,
    translated: String?,
    isCurrent: Boolean,
    isPast: Boolean,
    plain: Boolean,
    playhead: State<Long>,
    playing: Boolean,
    textScale: Float,
    ink: Color,
    halo: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val faded =
        animateFloatAsState(
            targetValue =
                when {
                    plain -> 0.9f
                    isCurrent -> 1f
                    isPast -> PAST_LINE_ALPHA
                    else -> UNSUNG_ALPHA
                },
            animationSpec = tween(LINE_MS, easing = LineEase),
            label = "lyricLineAlpha",
        )
    val scale = animateFloatAsState(if (isCurrent || plain) 1f else RESTING_SCALE, tween(LINE_MS, easing = LineEase), label = "lyricLineScale")
    val size = (if (plain) 18.sp else 26.sp) * textScale
    val style =
        typo().headlineLarge.copy(
            fontSize = size,
            lineHeight = size * 1.3f,
            fontWeight = if (plain) FontWeight.Medium else FontWeight.Bold,
        )
    val rich = syncType == "RICH_SYNCED" && !plain
    val lead = if (rich) remember(words, line.startTimeMs, line.endTimeMs) { parseRichSyncWords(words, line.startTimeMs, line.endTimeMs) } else null
    val background =
        if (rich) remember(line.background, line.startTimeMs, line.endTimeMs) { line.background?.let { parseRichSyncWords(it, line.startTimeMs, line.endTimeMs) } } else null
    val text = remember(words, syncType) { if (syncType == "RICH_SYNCED") words.stripRichSyncTimestamps() else words }
    val lineDirection = rememberLyricLayoutDirection(text)
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = if (isCurrent) 1f else faded.value
                scaleX = scale.value
                scaleY = scale.value
                transformOrigin = TransformOrigin(if (far) 1f else 0f, 0.5f)
            }.then(if (onClick != null) Modifier.clickable(interactionSource = null, indication = null, onClick = onClick) else Modifier)
            .padding(vertical = if (plain) 4.dp else 9.dp)
            .padding(start = if (duet && far) DUET_INSET else 0.dp, end = if (duet && !far) DUET_INSET else 0.dp),
    ) {
        when {
            text == BREAK_NOTE -> BreakNote(active = isCurrent, playing = playing, style = style, color = ink)
            lead != null || background != null ->
                // Word order follows the original; subtitles share its edge but keep their own direction.
                CompositionLocalProvider(LocalLayoutDirection provides lineDirection) {
                    if (lead != null) SungWords(lead, isCurrent, playhead, style, ink, halo, far = far)
                    if (background != null) {
                        // Dimmed as a group (graphicsLayer alpha), never by a translucent colour, which
                        // composites glyph by glyph and brightens wherever two glyphs overlap.
                        val sub = size * BACKGROUND_VOCALS_SCALE
                        Box(Modifier.padding(top = 2.dp).graphicsLayer { alpha = BACKGROUND_VOCALS_ALPHA }) {
                            SungWords(background, isCurrent, playhead, style.copy(fontSize = sub, lineHeight = sub * 1.3f), ink, halo = false, far = far)
                        }
                    }
                }
            else -> LyricText(text = text, modifier = Modifier.fillMaxWidth(), style = style, color = ink, far = far)
        }
        if (romanized != null) {
            val sub = max(size.value * 0.62f, 11f).sp
            LyricText(
                text = romanized,
                alignmentText = text,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                style = style.copy(fontSize = sub, lineHeight = sub * 1.3f, fontWeight = FontWeight.SemiBold),
                color = ink,
                far = far,
            )
        }
        if (translated != null) {
            LyricText(
                text = translated,
                alignmentText = text,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                style = typo().bodyMedium.copy(fontSize = 14.sp * textScale, fontStyle = FontStyle.Italic),
                color = ink.copy(alpha = 0.8f),
                far = far,
            )
        }
    }
}

/** In a duet, how far each singer's lines stay off the other singer's edge. */
private val DUET_INSET = 40.dp

/** How much smaller and quieter background vocals are than the lead, after desktop (0.72em, 65%). */
private const val BACKGROUND_VOCALS_SCALE = 0.72f
private const val BACKGROUND_VOCALS_ALPHA = 0.65f

/**
 * M3 Expressive's fullscreen lyrics: Classic's Lyrics view ([ClassicLyricsPage]) in a full-height
 * bottom sheet.
 */
@ExperimentalMaterial3Api
@Composable
fun FullscreenLyricsSheet(
    state: NowPlayingContentState,
    actions: NowPlayingContentActions,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        contentColor = Color.Transparent,
        dragHandle = {},
        scrimColor = Color.Black.copy(alpha = .5f),
        // The default 640dp cap would leave a centred strip on a phone turned sideways.
        sheetMaxWidth = Dp.Unspecified,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        shape = RectangleShape,
    ) {
        // The page's own close slides the sheet away before it leaves composition.
        ClassicLyricsPage(state, actions) {
            coroutineScope.launch {
                sheetState.hide()
                onDismiss()
            }
        }
    }
}
