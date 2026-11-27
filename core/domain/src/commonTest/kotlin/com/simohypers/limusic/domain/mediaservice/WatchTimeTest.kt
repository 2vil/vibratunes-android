package com.simohypers.limusic.domain.mediaservice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchTimeTest {
    /** Plays [from]..[to] in 0.25 s ticks. */
    private fun WatchTime.play(
        from: Double,
        to: Double,
    ) {
        var pos = from
        while (pos <= to) {
            tick(pos)
            pos += 0.25
        }
    }

    @Test
    fun reportsEveryStretchHeardOnceAndClosesThePlay() {
        val w = WatchTime(startedMs = 1_000)
        // Nothing before the playback ping, but the time heard before it is kept.
        w.play(0.0, 30.0)
        assertNull(w.pause(31_000))
        w.play(30.0, 70.0)
        w.arm("u", "cpn")

        // A seek forward ends the stretch, a pause reports both.
        w.play(120.0, 130.0)
        val paused = w.pause(132_000)!!
        assertEquals(listOf(0.0 to 30.0, 30.0 to 70.0, 120.0 to 130.0), paused.stretches)
        assertEquals("u" to "cpn", paused.url to paused.cpn)
        assertFalse(paused.last)
        assertEquals(131.0, paused.elapsed)
        assertNull(w.pause(132_000), "nothing new")

        // Resumed and skipped: the closing report has the rest.
        w.play(130.0, 140.0)
        val last = w.close(143_000)!!
        assertEquals(listOf(130.0 to 140.0), last.stretches)
        assertTrue(last.last)
    }

    @Test
    fun aPlayThatNeverRegisteredReportsNothing() {
        val w = WatchTime(0)
        w.play(0.0, 100.0)
        assertNull(w.pause(0))
        assertNull(w.close(0))
    }

    /** The params music.youtube.com sends, one volume/muted value per stretch (desktop's test). */
    @Test
    fun paramsListTheStretches() {
        val r = WatchTime.Report("u", "cpn", listOf(61.395 to 93.262, 95.56 to 101.4), 101.4, 102.015, last = false)
        assertEquals(
            mapOf(
                "cmt" to "101.4",
                "st" to "61.395,95.56",
                "et" to "93.262,101.4",
                "state" to "paused",
                "rt" to "102.015",
                "volume" to "15,15",
                "muted" to "0,0",
            ),
            r.params(15),
        )
        val closing = r.copy(stretches = emptyList(), position = 259.5, last = true).params(15)
        assertEquals(listOf("259.5", "259.5", "259.5", "15", "0", "1"), listOf("cmt", "st", "et", "volume", "muted", "final").map { closing[it] })
    }
}
