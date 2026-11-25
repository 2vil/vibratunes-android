package com.simohypers.limusic.kotlinytmusicscraper.pages

import com.simohypers.limusic.kotlinytmusicscraper.models.SongItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Album rows that link the music video take the album audio's id (issue #52, desktop #442). */
class AlbumAudioTest {
    private fun row(
        id: String,
        type: String?,
        duration: Int = 200,
    ) = SongItem(id = id, title = id, artists = emptyList(), duration = duration, thumbnail = "art", musicVideoType = type)

    private val album =
        listOf(
            row("song1", "MUSIC_VIDEO_TYPE_ATV", 26),
            row("video2", "MUSIC_VIDEO_TYPE_OMV", 302),
            row("untyped3", null),
        )

    @Test
    fun videoRowTakesTheAudioIdAndKeepsTheVideoAsItsTwin() {
        val audio = listOf(row("song1", "MUSIC_VIDEO_TYPE_ATV"), row("song2", "MUSIC_VIDEO_TYPE_ATV", 294), row("song3", "MUSIC_VIDEO_TYPE_ATV"))
        val swapped = album.withAlbumAudio(audio)

        assertEquals(listOf("song1", "song2", "untyped3"), swapped.map { it.id })
        assertEquals(294, swapped[1].duration)
        assertEquals("MUSIC_VIDEO_TYPE_ATV", swapped[1].musicVideoType)
        assertEquals("video2", swapped[1].counterpart?.id)
        // Wide art, or the switch would play the video's sound under the album cover.
        assertEquals(720, swapped[1].counterpart?.thumbnails?.thumbnails?.single()?.height)
        // Only the video row trades; a row YouTube never typed is not called a video.
        assertNull(swapped[0].counterpart)
        assertNull(swapped[2].counterpart)
    }

    @Test
    fun countMismatchKeepsTheVideoIds() {
        assertSame(album, album.withAlbumAudio(listOf(row("song1", "MUSIC_VIDEO_TYPE_ATV"))))
    }

    @Test
    fun anAudioPlaylistRowThatIsAVideoIsNotTaken() {
        val audio = listOf(row("song1", "MUSIC_VIDEO_TYPE_ATV"), row("other", "MUSIC_VIDEO_TYPE_OMV"), row("song3", "MUSIC_VIDEO_TYPE_ATV"))
        assertEquals("video2", album.withAlbumAudio(audio)[1].id)
    }
}
