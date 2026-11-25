package com.simohypers.limusic.domain.data.model.lyrics

import com.simohypers.limusic.domain.data.entities.LyricsEntity
import com.simohypers.limusic.domain.data.model.browse.album.TwinSegment
import com.simohypers.limusic.domain.data.model.metadata.Line
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricsOnVideoTest {
    @Test
    fun ontoVideoMovesLinesAndWordsPastTheIntro() {
        val song =
            LyricsEntity(
                videoId = "song",
                error = false,
                syncType = "RICH_SYNCED",
                lines =
                    listOf(
                        Line(startTimeMs = "1000", endTimeMs = "1500", words = "<00:01.000>a<00:01.500>", background = "<00:01.00>ooh"),
                        Line(startTimeMs = "15000", endTimeMs = "0", words = "cut"),
                    ),
                limusicId = "song",
                revision = 7,
                offsetMs = 120,
            )
        // A 5.477 s intro, then the song plays on, except 10..20 s of it the video drops.
        val v = song.ontoVideo("video", listOf(TwinSegment(0, 5477, 10_000), TwinSegment(20_000, 15_477, 100_000)))
        val l = v.lines!![0]
        assertEquals("6477" to "6977", l.startTimeMs to l.endTimeMs)
        assertEquals("<00:06.477>a<00:06.977>", l.words)
        assertEquals("<00:06.477>ooh", l.background)
        // A line the video cut waits for where the song picks up again; an unknown end stays unknown.
        assertEquals("15477" to "0", v.lines!![1].startTimeMs to v.lines!![1].endTimeMs)
        // Shown and pinned under the video; votes and edits there are the video's, the nudge the song's.
        assertEquals("video", v.videoId)
        assertNull(v.limusicId)
        assertNull(v.revision)
        assertEquals(120, v.offsetMs)
    }
}
