package com.simohypers.limusic.ui.component

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import com.simohypers.limusic.common.LibraryChipType
import com.simohypers.limusic.ui.icon.Album
import com.simohypers.limusic.ui.icon.DownloadForOffline
import com.simohypers.limusic.ui.icon.Favorite
import com.simohypers.limusic.ui.icon.Headphones
import com.simohypers.limusic.ui.icon.History
import com.simohypers.limusic.ui.icon.Home
import com.simohypers.limusic.ui.icon.LibraryMusic
import com.simohypers.limusic.ui.icon.PeopleAlt
import com.simohypers.limusic.ui.icon.QueueMusic
import com.simohypers.limusic.ui.icon.Search
import com.simohypers.limusic.ui.icon.Sensors
import com.simohypers.limusic.ui.icon.Settings
import com.simohypers.limusic.ui.icon.LimusicIcons
import com.simohypers.limusic.ui.navigation.destination.home.HomeDestination
import com.simohypers.limusic.ui.navigation.destination.home.RecentlySongsDestination
import com.simohypers.limusic.ui.navigation.destination.home.SettingsDestination
import com.simohypers.limusic.ui.navigation.destination.library.AlbumsDestination
import com.simohypers.limusic.ui.navigation.destination.library.ArtistsDestination
import com.simohypers.limusic.ui.navigation.destination.library.DownloadsDestination
import com.simohypers.limusic.ui.navigation.destination.library.LibraryDestination
import com.simohypers.limusic.ui.navigation.destination.library.LibraryDynamicPlaylistDestination
import com.simohypers.limusic.ui.navigation.destination.library.MixForYouDestination
import com.simohypers.limusic.ui.navigation.destination.library.PlaylistsDestination
import com.simohypers.limusic.ui.navigation.destination.library.PodcastsDestination
import com.simohypers.limusic.ui.navigation.destination.library.SongsDestination
import com.simohypers.limusic.ui.navigation.destination.search.SearchDestination
import com.simohypers.limusic.ui.screen.library.LibraryDynamicPlaylistType
import org.jetbrains.compose.resources.StringResource
import limusic.composeapp.generated.resources.Res
import limusic.composeapp.generated.resources.albums
import limusic.composeapp.generated.resources.artists
import limusic.composeapp.generated.resources.history
import limusic.composeapp.generated.resources.home
import limusic.composeapp.generated.resources.library
import limusic.composeapp.generated.resources.library_downloads
import limusic.composeapp.generated.resources.liked_songs
import limusic.composeapp.generated.resources.mix
import limusic.composeapp.generated.resources.podcasts
import limusic.composeapp.generated.resources.playlists
import limusic.composeapp.generated.resources.search
import limusic.composeapp.generated.resources.settings
import limusic.composeapp.generated.resources.songs

sealed class BottomNavScreen(
    val key: String,
    val ordinal: Int,
    val destination: Any,
    val title: StringResource,
    val icon: @Composable () -> Unit,
    val libraryChip: LibraryChipType? = null,
) {
    data object Home : BottomNavScreen(
        key = "home",
        ordinal = 0,
        destination = HomeDestination,
        title = Res.string.home,
        icon = { Icon(LimusicIcons.Home, contentDescription = null) },
    )

    data object Search : BottomNavScreen(
        key = "search",
        ordinal = 1,
        destination = SearchDestination,
        title = Res.string.search,
        icon = { Icon(LimusicIcons.Search, contentDescription = null) },
    )

    data object Library : BottomNavScreen(
        key = "library",
        ordinal = 2,
        destination = LibraryDestination,
        title = Res.string.library,
        icon = { Icon(imageVector = LimusicIcons.LibraryMusic, contentDescription = null) },
    )

    data object Playlists : BottomNavScreen(
        key = "playlists",
        ordinal = 3,
        destination = PlaylistsDestination,
        title = Res.string.playlists,
        icon = { Icon(imageVector = LimusicIcons.QueueMusic, contentDescription = null) },
        libraryChip = LibraryChipType.LOCAL_PLAYLIST,
    )

    data object Albums : BottomNavScreen(
        key = "albums",
        ordinal = 4,
        destination = AlbumsDestination,
        title = Res.string.albums,
        icon = { Icon(imageVector = LimusicIcons.Album, contentDescription = null) },
        libraryChip = LibraryChipType.ALBUMS,
    )

    data object Artists : BottomNavScreen(
        key = "artists",
        ordinal = 5,
        destination = ArtistsDestination,
        title = Res.string.artists,
        icon = { Icon(imageVector = LimusicIcons.PeopleAlt, contentDescription = null) },
        libraryChip = LibraryChipType.ARTISTS,
    )

    data object Songs : BottomNavScreen(
        key = "songs",
        ordinal = 6,
        destination = SongsDestination,
        title = Res.string.songs,
        icon = { Icon(imageVector = LimusicIcons.LibraryMusic, contentDescription = null) },
        libraryChip = LibraryChipType.SONGS,
    )

    data object Downloads : BottomNavScreen(
        key = "downloads",
        ordinal = 7,
        destination = DownloadsDestination,
        title = Res.string.library_downloads,
        icon = { Icon(imageVector = LimusicIcons.DownloadForOffline, contentDescription = null) },
        libraryChip = LibraryChipType.DOWNLOADED_PLAYLIST,
    )

    data object Podcasts : BottomNavScreen(
        key = "podcasts",
        ordinal = 8,
        destination = PodcastsDestination,
        title = Res.string.podcasts,
        icon = { Icon(imageVector = LimusicIcons.Headphones, contentDescription = null) },
        libraryChip = LibraryChipType.FAVORITE_PODCAST,
    )

    data object History : BottomNavScreen(
        key = "history",
        ordinal = 9,
        destination = RecentlySongsDestination,
        title = Res.string.history,
        icon = { Icon(imageVector = LimusicIcons.History, contentDescription = null) },
    )

    data object MixForYou : BottomNavScreen(
        key = "mix",
        ordinal = 10,
        destination = MixForYouDestination,
        title = Res.string.mix,
        icon = { Icon(imageVector = LimusicIcons.Sensors, contentDescription = null) },
    )

    data object LikedMusic : BottomNavScreen(
        key = "liked_music",
        ordinal = 11,
        destination = LibraryDynamicPlaylistDestination(LibraryDynamicPlaylistType.Favorite.toStringParams()),
        title = Res.string.liked_songs,
        icon = { Icon(imageVector = LimusicIcons.Favorite, contentDescription = null) },
    )

    data object Settings : BottomNavScreen(
        key = "settings",
        ordinal = 12,
        destination = SettingsDestination,
        title = Res.string.settings,
        icon = { Icon(imageVector = LimusicIcons.Settings, contentDescription = null) },
    )

    companion object {
        val allTabs = listOf(Home, Search, Library, LikedMusic, Playlists, Albums, Artists, Songs, Downloads, Podcasts, History, MixForYou, Settings)

        fun parseTabs(savedString: String): List<BottomNavScreen> {
            val keys = savedString.split(",").map { it.trim() }
            val tabMap = allTabs.associateBy { it.key }
            return keys.mapNotNull { tabMap[it] }
        }

        fun serializeTabs(tabs: List<BottomNavScreen>): String {
            return tabs.joinToString(",") { it.key }
        }
    }
}
