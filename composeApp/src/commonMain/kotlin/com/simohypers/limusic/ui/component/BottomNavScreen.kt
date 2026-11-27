package com.simohypers.limusic.ui.component

import androidx.compose.ui.graphics.vector.ImageVector
import com.simohypers.limusic.ui.icon.Album
import com.simohypers.limusic.ui.icon.DownloadForOffline
import com.simohypers.limusic.ui.icon.Favorite
import com.simohypers.limusic.ui.icon.Home
import com.simohypers.limusic.ui.icon.LibraryMusic
import com.simohypers.limusic.ui.icon.LimusicIcons
import com.simohypers.limusic.ui.icon.PeopleAlt
import com.simohypers.limusic.ui.icon.Podcasts
import com.simohypers.limusic.ui.icon.QueueMusic
import com.simohypers.limusic.ui.icon.Search
import com.simohypers.limusic.ui.icon.Sensors
import com.simohypers.limusic.ui.navigation.destination.home.HomeDestination
import com.simohypers.limusic.ui.navigation.destination.library.AlbumsDestination
import com.simohypers.limusic.ui.navigation.destination.library.ArtistsDestination
import com.simohypers.limusic.ui.navigation.destination.library.DownloadsDestination
import com.simohypers.limusic.ui.navigation.destination.library.LibraryDestination
import com.simohypers.limusic.ui.navigation.destination.library.LikedSongsDestination
import com.simohypers.limusic.ui.navigation.destination.library.MixForYouDestination
import com.simohypers.limusic.ui.navigation.destination.library.PlaylistsDestination
import com.simohypers.limusic.ui.navigation.destination.library.PodcastsDestination
import com.simohypers.limusic.ui.navigation.destination.library.SongsDestination
import com.simohypers.limusic.ui.navigation.destination.search.SearchDestination
import limusic.composeapp.generated.resources.Res
import limusic.composeapp.generated.resources.albums
import limusic.composeapp.generated.resources.artists
import limusic.composeapp.generated.resources.home
import limusic.composeapp.generated.resources.library
import limusic.composeapp.generated.resources.library_downloads
import limusic.composeapp.generated.resources.liked_songs
import limusic.composeapp.generated.resources.mix
import limusic.composeapp.generated.resources.playlists
import limusic.composeapp.generated.resources.podcasts
import limusic.composeapp.generated.resources.search
import limusic.composeapp.generated.resources.songs
import org.jetbrains.compose.resources.StringResource

/**
 * A tab the bar can show. Every tab has a destination of its own, which nothing else navigates to,
 * so the bar can tell which tab a back stack belongs to from the route alone. [key] is how the
 * user's pick is saved.
 */
sealed class BottomNavScreen(
    val key: String,
    val ordinal: Int,
    val destination: Any,
    val title: StringResource,
    val icon: ImageVector,
) {
    data object Home : BottomNavScreen("home", 0, HomeDestination, Res.string.home, LimusicIcons.Home)

    data object Search : BottomNavScreen("search", 1, SearchDestination, Res.string.search, LimusicIcons.Search)

    data object Library : BottomNavScreen("library", 2, LibraryDestination, Res.string.library, LimusicIcons.LibraryMusic)

    data object LikedSongs : BottomNavScreen("liked_songs", 3, LikedSongsDestination, Res.string.liked_songs, LimusicIcons.Favorite)

    // Only shown while signed in to YouTube: an anonymous session gets no mixes.
    // Labelled "Mix", not "Mix for you": the full title is the widest label in the bar and forces
    // every tab to be that wide. The screen itself still uses the full title.
    data object MixForYou : BottomNavScreen("mix", 4, MixForYouDestination, Res.string.mix, LimusicIcons.Sensors)

    // Library with one chip and no chip row.
    data object Playlists : BottomNavScreen("playlists", 5, PlaylistsDestination, Res.string.playlists, LimusicIcons.QueueMusic)

    data object Albums : BottomNavScreen("albums", 6, AlbumsDestination, Res.string.albums, LimusicIcons.Album)

    data object Artists : BottomNavScreen("artists", 7, ArtistsDestination, Res.string.artists, LimusicIcons.PeopleAlt)

    data object Songs : BottomNavScreen("songs", 8, SongsDestination, Res.string.songs, LimusicIcons.LibraryMusic)

    data object Downloads : BottomNavScreen("downloads", 9, DownloadsDestination, Res.string.library_downloads, LimusicIcons.DownloadForOffline)

    data object Podcasts : BottomNavScreen("podcasts", 10, PodcastsDestination, Res.string.podcasts, LimusicIcons.Podcasts)

    companion object {
        /** More than five and the labels no longer fit a phone's width. */
        const val MAX_TABS = 5

        // Lazy: touching Home first starts this class's init, and an eager list would catch Home
        // before it exists and hold a null.
        val allTabs by lazy { listOf(Home, Search, Library, LikedSongs, MixForYou, Playlists, Albums, Artists, Songs, Downloads, Podcasts) }

        private val defaultTabs by lazy { listOf(Home, Search, Library) }

        /**
         * The saved tabs in their order; nothing saved means the default three. Home is always
         * there, since the app opens on it.
         */
        fun parseTabs(saved: String): List<BottomNavScreen> {
            if (saved.isBlank()) return defaultTabs
            val tabs = saved.split(",").mapNotNull { key -> allTabs.find { it.key == key.trim() } }.distinct()
            return (if (Home in tabs) tabs else listOf(Home) + tabs).take(MAX_TABS)
        }

        fun serializeTabs(tabs: List<BottomNavScreen>): String = tabs.joinToString(",") { it.key }
    }
}
