package com.simohypers.limusic.ui.component.lyrics

import com.simohypers.limusic.extension.parseRichSyncWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricMotionTest {
    @Test
    fun aStampWithNothingAfterItEndsTheWordBeforeIt() {
        val line = parseRichSyncWords("<00:01.000>Hello<00:01.400> <00:02.000>world", "1000", "3000")!!
        assertEquals(listOf("Hello", "world"), line.words.map { it.text })
        assertEquals(1_400L, line.endOf(0), "its own end, not the next word's start")
        assertEquals(3_000L, line.endOf(1), "the last word runs to the line's end")
        val old = parseRichSyncWords("<00:01.000>Hello <00:02.000>world", "1000", "0")!!
        assertEquals(2_000L, old.endOf(0), "a cached lyric without ends runs to the next word")
        assertEquals(2_000L + LAST_WORD_FALLBACK_MS, old.endOf(1))
    }

    @Test
    fun onlyALongWordInALatinScriptIsHeld() {
        val held = assertNotNull(heldLetters("love", 1_000, 2_200))
        assertEquals(listOf("l", "o", "v", "e"), held.map { it.text })
        assertEquals(listOf(1_000L, 1_300L, 1_600L, 1_900L), held.map { it.startMs })
        assertEquals(2_200L, held.last().endMs)
        assertNull(heldLetters("love", 1_000, 1_900), "under a second")
        assertNull(heldLetters("a", 1_000, 3_000), "one letter")
        assertNull(heldLetters("supercalifragilistic", 1_000, 5_000), "more than twelve")
        assertNull(heldLetters("حب", 1_000, 3_000), "a joined script lights whole")
        assertNotNull(heldLetters("любовь", 1_000, 3_000))
        assertEquals(listOf("e\u0301", "t", "e\u0301"), heldLetters("été", 0, 1_500)!!.map { it.text }, "a mark stays with its letter")
    }

    @Test
    fun linesAheadOfTheMotionArriveFirst() {
        assertTrue(scrollMs(-1, ahead = true) < scrollMs(0, ahead = false))
        assertTrue(scrollMs(0, ahead = false) < scrollMs(1, ahead = false))
        assertTrue(scrollMs(1, ahead = false) < scrollMs(4, ahead = false))
        assertEquals(scrollMs(6, ahead = true), scrollMs(9, ahead = true), "ahead lines stop spreading after six")
        assertEquals(0f, progress(1_000, 2_000, 900))
        assertEquals(0.5f, progress(1_000, 2_000, 1_500))
        assertEquals(1f, progress(1_000, 1_000, 1_000))
    }
}
