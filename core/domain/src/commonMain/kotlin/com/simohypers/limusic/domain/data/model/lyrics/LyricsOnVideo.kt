package com.simohypers.limusic.domain.data.model.lyrics

import com.simohypers.limusic.domain.data.entities.LyricsEntity
import com.simohypers.limusic.domain.data.model.browse.album.TwinSegment
import com.simohypers.limusic.domain.mediaservice.queue.twinPositionMs

/**
 * What a music video's cache row may hold and still be shown: its own Limusic lyric, or a source
 * picked or nudged for it. Anything else was found for the video alone, before its song was known.
 */
val LyricsEntity.keptOnVideo: Boolean get() = pinned || limusicId != null

/**
 * The song's lyrics on its music video [videoId]: every line and word time moved through
 * [segments] (the song's moments to the video's), the map the Song/Video switch lands by. No
 * longer the service's lyric as filed, so votes, reports and edits on the video are the video's own.
 */
fun LyricsEntity.ontoVideo(
    videoId: String,
    segments: List<TwinSegment>,
): LyricsEntity {
    fun at(ms: Long) = segments.twinPositionMs(ms)

    fun stamps(text: String) =
        STAMP.replace(text) { m ->
            val (min, sec, frac) = m.destructured
            val ms = at(min.toLong() * 60_000 + sec.toLong() * 1000 + frac.toLong() * (if (frac.length == 2) 10 else 1))
            "<${(ms / 60_000).pad(2)}:${(ms / 1000 % 60).pad(2)}.${(ms % 1000).pad(3)}>"
        }
    val moved =
        lines?.takeIf { syncType != "UNSYNCED" }?.map { l ->
            l.copy(
                startTimeMs = l.startTimeMs.toLongOrNull()?.let { at(it).toString() } ?: l.startTimeMs,
                // "0" is an end nobody knows, not the song's first moment.
                endTimeMs = l.endTimeMs.toLongOrNull()?.takeIf { it > 0 }?.let { at(it).toString() } ?: l.endTimeMs,
                words = stamps(l.words),
                background = l.background?.let(::stamps),
            )
        } ?: lines
    return copy(videoId = videoId, lines = moved, limusicId = null, revision = null)
}

private val STAMP = Regex("""<(\d{1,2}):(\d{2})\.(\d{2,3})>""")

private fun Long.pad(n: Int) = toString().padStart(n, '0')
