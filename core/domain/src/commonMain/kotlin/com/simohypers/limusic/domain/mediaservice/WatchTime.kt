package com.simohypers.limusic.domain.mediaservice

import kotlin.math.roundToLong

/**
 * Watch time for a registered play, after desktop's `watchtime.rs` (#764): which stretches of the
 * track were heard, reported to the play's `videostatsWatchtimeUrl` once its playback ping is out.
 * Without it YouTube only knows a play started. Desktop reports every 40 s while playing, as
 * music.youtube.com does; the phone reports on a pause and when the play ends (`final=1`) only, so
 * playback wakes the radio no more often than before (#7). One per play: the handler feeds it
 * position ticks and sends what it returns. [startedMs] is on the clock the reports are timed by.
 */
class WatchTime(
    private val startedMs: Long,
) {
    /** Stretches ended and not yet reported, in track seconds. */
    private val done = mutableListOf<Pair<Double, Double>>()

    /** The stretch being heard now. */
    private var open: Pair<Double, Double>? = null
    private var position = 0.0

    /** The watch-time URL and the play's cpn, once the playback ping is out. */
    private var target: Pair<String, String>? = null

    /** The playback ping went out. What was heard before it is reported too. */
    fun arm(
        url: String,
        cpn: String,
    ) {
        target = url to cpn
    }

    /**
     * A position tick while playing. Ticks come at most a second apart, so any other move (a jump
     * back, a jump of seconds forward) ends the stretch.
     */
    fun tick(pos: Double) {
        val o = open
        if (o != null && pos >= o.second && pos - o.second < 2.0) {
            open = o.first to pos
        } else {
            shut()
            open = pos to pos
        }
        position = pos
    }

    /** Paused: report what was heard up to here. */
    fun pause(nowMs: Long): Report? {
        shut()
        return report(nowMs, last = false)
    }

    /** The play is over (another started, or this one looped): report the rest and close it. */
    fun close(nowMs: Long): Report? {
        shut()
        return report(nowMs, last = true)
    }

    private fun shut() {
        open?.takeIf { it.second > it.first }?.let(done::add)
        open = null
    }

    private fun report(
        nowMs: Long,
        last: Boolean,
    ): Report? {
        val (url, cpn) = target ?: return null
        if (done.isEmpty() && !last) return null
        val stretches = done.toList()
        done.clear()
        return Report(url, cpn, stretches, position, (nowMs - startedMs) / 1000.0, last)
    }

    data class Report(
        val url: String,
        val cpn: String,
        /** `(start, end)` of each stretch heard since the last report. Empty on a close with nothing new. */
        val stretches: List<Pair<Double, Double>>,
        /** The playhead. */
        val position: Double,
        /** Seconds since the play started. */
        val elapsed: Double,
        /** Closes the play. */
        val last: Boolean,
    ) {
        /** The query music.youtube.com sends with a paused or closing report; [volume] is 0-100. */
        fun params(volume: Int): Map<String, String> {
            // Nothing new still names a stretch: the official client closes with st=et=cmt.
            val heard = stretches.ifEmpty { listOf(position to position) }

            // One value per stretch, as the official client lists them.
            fun each(v: String) = List(heard.size) { v }.joinToString(",")
            return buildMap {
                put("cmt", secs(position))
                put("st", heard.joinToString(",") { secs(it.first) })
                put("et", heard.joinToString(",") { secs(it.second) })
                put("state", "paused")
                put("rt", secs(elapsed))
                put("volume", each(volume.toString()))
                put("muted", each("0"))
                if (last) put("final", "1")
            }
        }
    }
}

/** Milliseconds at most, trailing zeros dropped: `10.468`, `0`. */
private fun secs(v: Double): String {
    val ms = (v * 1000).roundToLong()
    val frac = (ms % 1000).toString().padStart(3, '0').trimEnd('0')
    return if (frac.isEmpty()) "${ms / 1000}" else "${ms / 1000}.$frac"
}
