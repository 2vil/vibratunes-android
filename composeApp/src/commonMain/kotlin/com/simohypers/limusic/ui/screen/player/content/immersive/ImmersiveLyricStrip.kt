package com.simohypers.limusic.ui.screen.player.content.immersive

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simohypers.limusic.domain.data.model.lyrics.RomanizationDictionaryState
import com.simohypers.limusic.domain.data.model.lyrics.RomanizationLanguage
import com.simohypers.limusic.domain.data.model.metadata.Line
import com.simohypers.limusic.domain.manager.DataStoreManager
import com.simohypers.limusic.domain.repository.LyricsRomanizerRepository
import com.simohypers.limusic.extension.ParsedRichSyncLine
import com.simohypers.limusic.extension.WordTiming
import com.simohypers.limusic.extension.parseRichSyncWords
import com.simohypers.limusic.ui.component.lyrics.BreakNote
import com.simohypers.limusic.ui.component.lyrics.LyricLevels
import com.simohypers.limusic.ui.component.lyrics.SLOT_LEAD_MS
import com.simohypers.limusic.ui.component.lyrics.endOf
import com.simohypers.limusic.ui.component.lyrics.SungWords
import com.simohypers.limusic.ui.component.lyrics.handoffBlur
import com.simohypers.limusic.ui.component.lyrics.lyricHandoff
import com.simohypers.limusic.ui.component.lyrics.rememberSweepPlayhead
import com.simohypers.limusic.ui.component.rememberLyricLayoutDirection
import com.simohypers.limusic.ui.screen.player.content.NowPlayingContentState
import com.simohypers.limusic.ui.screen.player.content.stripRichSyncTimestamps
import kotlin.math.roundToLong
import org.koin.compose.koinInject

/**
 * The line being sung, directly over the progress bar. Tapping it opens the Lyrics tab. One line
 * tall, scrolling sideways as a marquee when it is longer, and one line tall empty or not, so the
 * controls below it never move. Before the first line and in an instrumental break it holds a
 * pulsing note, so an intro doesn't read as a song without lyrics. Follows the Romanized lyric line
 * setting, like [CoverLyricLine].
 */
@Composable
internal fun ImmersiveLyricStrip(
    state: NowPlayingContentState,
    typography: ImmersiveTypography,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val synced = state.lyricsSynced()
    LyricSlot(
        state = state,
        style = typography.lyricStrip,
        color = typography.lyricStrip.color,
        rows = 1,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    enabled = synced,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 20.dp),
    )
}

/**
 * The line being sung under the cover, in the Classic and M3 Expressive players: two lines tall
 * whatever it holds, a one-line lyric in the middle of it, in the theme's colours. [hidden] empties
 * it (M3 Expressive under a fullscreen canvas). With Romanized lyric line on (Settings > Appearance >
 * Player) a line with a reading shows that instead: two lines hold one script, not both.
 */
@Composable
internal fun CoverLyricLine(
    state: NowPlayingContentState,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
) {
    LyricSlot(state, style, color, rows = 2, hidden = hidden, modifier = modifier.fillMaxWidth())
}

private fun NowPlayingContentState.lyricsSynced(): Boolean {
    val lyrics = screenData.lyricsData?.lyrics
    return lyrics?.lines != null && lyrics.syncType != null && lyrics.syncType != "UNSYNCED"
}

// What a slot shows: a line being sung, or the note before the first line and in a break.
private sealed interface Slot {
    data class Sung(
        val line: Line,
    ) : Slot

    data object Waiting : Slot
}

/**
 * Desktop's CoverLyric: word-synced lines light as they are sung, the way the lyrics page lights
 * them (a held note crests, and on a dark player glows); line-synced ones show whole. A new line
 * comes in with [lyricHandoff]. Background vocals stay on the lyrics page, where they have a line of
 * their own; a line that is nothing but them sweeps them, or it would show empty.
 */
@Composable
private fun LyricSlot(
    state: NowPlayingContentState,
    style: TextStyle,
    color: Color,
    rows: Int,
    modifier: Modifier,
    hidden: Boolean = false,
) {
    val lyrics = state.screenData.lyricsData?.lyrics
    val synced = state.lyricsSynced() && !hidden
    val richSynced = synced && lyrics?.syncType == "RICH_SYNCED"
    val playing = state.controllerState.isPlaying
    val playhead =
        rememberSweepPlayhead(
            rawMs = { state.timelineState.current - state.lyricsOffsetMs },
            running = richSynced && playing,
        )
    val lines = if (synced) lyrics?.lines.orEmpty() else emptyList()
    val timing = remember(lines) { SlotTiming(lines) }
    // The index is -1 only until the first line starts: once a line has been reached it is held
    // until the next one, and the last is held to the end of the track.
    val index by remember(timing, playhead) { derivedStateOf { timing.lineAt(playhead.value) } }
    val line = lines.getOrNull(index)?.takeIf { it.hasWords() }
    val reading = rememberReading()
    val levels = remember(color) { LyricLevels(color) }
    val single = rows == 1
    AnimatedContent(
        // Keyed on the line itself: the index alone would miss a lyrics swap that lands on the same
        // index, and the text alone would merge two identical lines sung back to back.
        targetState =
            when {
                line != null -> Slot.Sung(line)
                synced -> Slot.Waiting
                else -> null
            },
        transitionSpec = { lyricHandoff() },
        label = "lyricSlot",
        modifier = modifier,
    ) { shown ->
        val sung = (shown as? Slot.Sung)?.line
        val roman = remember(sung, reading) { sung?.let { reading(it.displayText()) } }
        val lineText = roman ?: sung?.displayText().orEmpty()
        val parsed = remember(sung, richSynced, roman) { if (richSynced) sung?.parsed(roman) else null }
        val lineDirection = rememberLyricLayoutDirection(lineText)
        // The empty text is what holds the slot's height, whatever is in it.
        Box(Modifier.fillMaxWidth().then(handoffBlur()), contentAlignment = Alignment.CenterStart) {
            Text("", style = style, minLines = rows, maxLines = rows)
            CompositionLocalProvider(LocalLayoutDirection provides lineDirection) {
                when {
                    shown == Slot.Waiting -> BreakNote(active = true, playing = playing, style = style, color = color)
                    parsed != null && single ->
                        // The marquee is on the Box, so the words scroll as one row.
                        Box(Modifier.basicMarquee(iterations = Int.MAX_VALUE, animationMode = MarqueeAnimationMode.Immediately)) {
                            SungWords(parsed, active = true, playhead, style, color, levels.dark, { levels.unsung }, singleLine = true)
                        }
                    parsed != null -> SungWords(parsed, active = true, playhead, style, color, levels.dark, { levels.unsung }, maxLines = rows)
                    sung != null ->
                        Text(
                            text = lineText,
                            style = style.copy(color = color, textDirection = TextDirection.ContentOrLtr),
                            maxLines = rows,
                            softWrap = !single,
                            overflow = if (single) TextOverflow.Clip else TextOverflow.Ellipsis,
                            modifier =
                                if (single) {
                                    Modifier.basicMarquee(iterations = Int.MAX_VALUE, animationMode = MarqueeAnimationMode.Immediately)
                                } else {
                                    Modifier.fillMaxWidth()
                                },
                        )
                }
            }
        }
    }
}

/**
 * Which line a slot shows at a moment of the song: the next one [SLOT_LEAD_MS] before it starts, so
 * the handoff is over by its first word, unless the line before is still being sung then, in which
 * case the change waits for that line's last word. Lines without word timing change on their cue.
 */
internal class SlotTiming(
    lines: List<Line>,
) {
    private val starts = lines.map { it.startTimeMs.toLongOrNull() ?: Long.MAX_VALUE }

    // When each line's last word ends, or its start for a line without word timing.
    private val ends =
        lines.mapIndexed { i, l ->
            l.parsed(null)?.let { it.endOf(it.words.lastIndex) } ?: starts[i]
        }

    fun lineAt(ms: Long): Int {
        val now = starts.indexOfLast { it <= ms }
        val early = starts.indexOfLast { it <= ms + SLOT_LEAD_MS }
        return if (early > now && now >= 0 && ends[now] > ms) now else early
    }
}

// A line with something to sing: lead words, or only background vocals. An empty timed line is a break.
private fun Line.hasWords(): Boolean = words.stripRichSyncTimestamps().isNotEmpty() || !background.isNullOrBlank()

private fun Line.displayText(): String = words.stripRichSyncTimestamps().ifEmpty { background?.stripRichSyncTimestamps().orEmpty() }

/**
 * The reading a one-line lyric slot shows instead of a line, or null: Romanized lyric line is off,
 * none of the languages picked under Lyrics romanization apply, or the line is already Latin.
 * A new function when any of those change, so the slots' remembers read the line again.
 */
@Composable
private fun rememberReading(): (String) -> String? {
    val dataStoreManager = koinInject<DataStoreManager>()
    val romanizer = koinInject<LyricsRomanizerRepository>()
    val on by dataStoreManager.coverLyricRomanized.collectAsStateWithLifecycle(false)
    val stored by dataStoreManager.romanizationLanguages.collectAsStateWithLifecycle("")
    val languages = remember(stored) { RomanizationLanguage.parse(stored) }
    val dictionary by romanizer.japaneseDictionaryState.collectAsStateWithLifecycle()
    val ready = dictionary == RomanizationDictionaryState.READY
    // The lyrics page fetches a missing pack too, but this line can be on screen without it ever opening.
    LaunchedEffect(on, languages) {
        if (on && RomanizationLanguage.JAPANESE in languages) romanizer.ensureJapaneseDictionary()
    }
    return remember(on, languages, ready) {
        { text -> if (on && languages.isNotEmpty()) romanizer.romanize(text, languages)?.trim()?.ifEmpty { null } else null }
    }
}

// The line's word timing, carried over to [roman] when it is shown instead.
private fun Line.parsed(roman: String?): ParsedRichSyncLine? =
    (parseRichSyncWords(words, startTimeMs, endTimeMs) ?: background?.let { parseRichSyncWords(it, startTimeMs, endTimeMs) })
        ?.let { if (roman != null) it.spreadOver(roman) else it }

/**
 * [reading] timed by this line: the romanizer's output is timed to nothing, so the words' sung span
 * (first word's start to the line's end) is shared out by length and the reading still sweeps as the
 * line is sung. Desktop's CoverLyric does the same.
 */
internal fun ParsedRichSyncLine.spreadOver(reading: String): ParsedRichSyncLine {
    val parts = reading.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val from = words.firstOrNull()?.startTimeMs ?: return this
    val to = endOf(words.lastIndex)
    val per = (to - from).toDouble() / parts.sumOf { it.length }.coerceAtLeast(1)
    var chars = 0
    val timed =
        parts.map { part ->
            WordTiming(part, from + (chars * per).roundToLong()).also { chars += part.length }
        }
    return ParsedRichSyncLine(timed, lineStartTimeMs, to)
}

