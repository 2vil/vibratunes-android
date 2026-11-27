package com.simohypers.limusic.ui.screen.player.content.immersive

import com.simohypers.limusic.domain.data.model.metadata.Line
import kotlin.test.Test
import kotlin.test.assertEquals

class SlotTimingTest {
    private fun line(start: Long, end: Long, words: String) = Line(endTimeMs = end.toString(), startTimeMs = start.toString(), words = words)

    @Test
    fun theNextLineComesEarlyOnlyOnceTheLastWordIsSung() {
        val t =
            SlotTiming(
                listOf(
                    line(1_000, 2_000, "<00:01.000>one<00:01.500>"),
                    line(2_000, 3_000, "<00:02.000>two <00:02.500>words"),
                    line(3_000, 4_000, "<00:03.000>three"),
                ),
            )
        assertEquals(-1, t.lineAt(700))
        assertEquals(0, t.lineAt(800), "the first line comes early: nothing is being sung")
        assertEquals(1, t.lineAt(1_800), "line one ended at 1.5 s, so line two comes early")
        assertEquals(1, t.lineAt(2_800), "line two's last word runs to 3 s, so line three waits")
        assertEquals(2, t.lineAt(3_000))
    }

    @Test
    fun linesWithoutWordTimingComeEarly() {
        val t = SlotTiming(listOf(line(1_000, 0, "one"), line(2_000, 0, "two")))
        assertEquals(1, t.lineAt(1_800))
    }
}
