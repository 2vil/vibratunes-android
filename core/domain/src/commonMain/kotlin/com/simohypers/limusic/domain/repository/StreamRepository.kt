package com.simohypers.limusic.domain.repository

import com.simohypers.limusic.domain.data.entities.NewFormatEntity
import com.simohypers.limusic.domain.data.model.browse.album.Track
import com.simohypers.limusic.domain.manager.DataStoreManager
import com.simohypers.limusic.domain.utils.Resource
import kotlinx.coroutines.flow.Flow

interface StreamRepository {
    suspend fun insertNewFormat(newFormat: NewFormatEntity)

    fun getNewFormat(videoId: String): Flow<NewFormatEntity?>

    suspend fun getFormatFlow(videoId: String): Flow<NewFormatEntity?>

    suspend fun updateFormat(videoId: String)

    fun getStream(
        dataStoreManager: DataStoreManager,
        videoId: String,
        isDownloading: Boolean,
        isVideo: Boolean,
        muxed: Boolean = false, // m3u8 or mp4 (both contain audio and video)
    ): Flow<String?>

    /** The watch-history ping for a play; emits the HTTP status, 0 on failure. */
    fun initPlayback(
        playback: String,
        cpn: String,
        playlistId: String?,
    ): Flow<Int>


    fun getFullMetadata(videoId: String): Flow<Resource<Track>>

    fun is403Url(url: String): Flow<Boolean>

    suspend fun invalidateFormat(videoId: String)

    /**
     * Which client produced this video's stream, for the info sheet: this run's resolve, else what
     * [streamUrl] (the saved URL it plays from) names. Null when neither says.
     */
    fun getExtractSource(
        videoId: String,
        streamUrl: String?,
    ): String?
}