package com.simohypers.limusic.ui.navigation.graph

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.simohypers.limusic.common.LibraryChipType
import com.simohypers.limusic.ui.component.BottomNavScreen
import com.simohypers.limusic.ui.component.limusicTabIndex
import com.simohypers.limusic.ui.navigation.destination.home.HomeDestination
import com.simohypers.limusic.ui.theme.ForceDarkContent
import com.simohypers.limusic.ui.navigation.destination.library.AlbumsDestination
import com.simohypers.limusic.ui.navigation.destination.library.ArtistsDestination
import com.simohypers.limusic.ui.navigation.destination.library.DownloadsDestination
import com.simohypers.limusic.ui.navigation.destination.library.LibraryDestination
import com.simohypers.limusic.ui.navigation.destination.library.MixForYouDestination
import com.simohypers.limusic.ui.navigation.destination.library.PlaylistsDestination
import com.simohypers.limusic.ui.navigation.destination.library.PodcastsDestination
import com.simohypers.limusic.ui.navigation.destination.library.SongsDestination
import com.simohypers.limusic.ui.navigation.destination.player.FullscreenDestination
import com.simohypers.limusic.ui.navigation.destination.search.SearchDestination
import com.simohypers.limusic.ui.screen.home.HomeScreen
import com.simohypers.limusic.ui.screen.library.LibraryScreen
import com.simohypers.limusic.ui.screen.library.MixForYouScreen
import com.simohypers.limusic.ui.screen.other.SearchScreen
import com.simohypers.limusic.ui.screen.player.FullscreenPlayer

@Composable
@ExperimentalMaterial3Api
@ExperimentalFoundationApi
fun AppNavigationGraph(
    innerPadding: PaddingValues,
    navController: NavHostController,
    tabs: List<BottomNavScreen> = BottomNavScreen.allTabs,
    currentLibraryChip: LibraryChipType = LibraryChipType.YOUR_LIBRARY,
    startDestination: Any = HomeDestination,
    hideNavBar: () -> Unit = { },
    showNavBar: (shouldShowNowPlayingSheet: Boolean) -> Unit = { },
    showNowPlayingSheet: () -> Unit = {},
    onScrolling: (onTop: Boolean) -> Unit = {},
) {
    NavHost(
        navController,
        startDestination = startDestination,
        enterTransition = {
            val fromIndex = getBottomTabIndex(initialState, tabs)
            val toIndex = getBottomTabIndex(targetState, tabs)
            when {
                fromIndex != null && toIndex != null -> {
                    when {
                        toIndex > fromIndex -> fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) + slideInHorizontally(
                            animationSpec = tween(350, easing = FastOutSlowInEasing),
                            initialOffsetX = { it },
                        )
                        toIndex < fromIndex -> fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) + slideInHorizontally(
                            animationSpec = tween(350, easing = FastOutSlowInEasing),
                            initialOffsetX = { -it },
                        )
                        else -> fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing))
                    }
                }
                else -> fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) + slideInHorizontally(
                    animationSpec = tween(350, easing = FastOutSlowInEasing),
                    initialOffsetX = { it },
                )
            }
        },
        exitTransition = {
            val fromIndex = getBottomTabIndex(initialState, tabs)
            val toIndex = getBottomTabIndex(targetState, tabs)
            when {
                fromIndex != null && toIndex != null -> {
                    when {
                        toIndex > fromIndex -> fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing)) + slideOutHorizontally(
                            animationSpec = tween(350, easing = FastOutSlowInEasing),
                            targetOffsetX = { -it },
                        )
                        toIndex < fromIndex -> fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing)) + slideOutHorizontally(
                            animationSpec = tween(350, easing = FastOutSlowInEasing),
                            targetOffsetX = { it },
                        )
                        else -> fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing))
                    }
                }
                else -> fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing)) + slideOutHorizontally(
                    animationSpec = tween(350, easing = FastOutSlowInEasing),
                    targetOffsetX = { -it },
                )
            }
        },
        popEnterTransition = {
            val fromIndex = getBottomTabIndex(initialState, tabs)
            val toIndex = getBottomTabIndex(targetState, tabs)
            when {
                fromIndex != null && toIndex != null -> {
                    when {
                        toIndex > fromIndex -> fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) + slideInHorizontally(
                            animationSpec = tween(350, easing = FastOutSlowInEasing),
                            initialOffsetX = { it },
                        )
                        toIndex < fromIndex -> fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) + slideInHorizontally(
                            animationSpec = tween(350, easing = FastOutSlowInEasing),
                            initialOffsetX = { -it },
                        )
                        else -> fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing))
                    }
                }
                else -> fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) + slideInHorizontally(
                    animationSpec = tween(350, easing = FastOutSlowInEasing),
                    initialOffsetX = { -it },
                )
            }
        },
        popExitTransition = {
            val fromIndex = getBottomTabIndex(initialState, tabs)
            val toIndex = getBottomTabIndex(targetState, tabs)
            when {
                fromIndex != null && toIndex != null -> {
                    when {
                        toIndex > fromIndex -> fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing)) + slideOutHorizontally(
                            animationSpec = tween(350, easing = FastOutSlowInEasing),
                            targetOffsetX = { -it },
                        )
                        toIndex < fromIndex -> fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing)) + slideOutHorizontally(
                            animationSpec = tween(350, easing = FastOutSlowInEasing),
                            targetOffsetX = { it },
                        )
                        else -> fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing))
                    }
                }
                else -> fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing)) + slideOutHorizontally(
                    animationSpec = tween(350, easing = FastOutSlowInEasing),
                    targetOffsetX = { it },
                )
            }
        },
    ) {
        // Bottom bar destinations
        composable<HomeDestination> {
            HomeScreen(
                onScrolling = onScrolling,
                navController = navController,
            )
        }
        composable<SearchDestination> {
            SearchScreen(
                navController = navController,
                innerPadding = innerPadding,
            )
        }
        composable<LibraryDestination> {
            LibraryScreen(
                innerPadding = innerPadding,
                navController = navController,
                onScrolling = onScrolling,
                initialChip = LibraryChipType.YOUR_LIBRARY,
            )
        }
        composable<PlaylistsDestination> {
            LibraryScreen(
                innerPadding = innerPadding,
                navController = navController,
                onScrolling = onScrolling,
                initialChip = LibraryChipType.LOCAL_PLAYLIST,
            )
        }
        composable<AlbumsDestination> {
            LibraryScreen(
                innerPadding = innerPadding,
                navController = navController,
                onScrolling = onScrolling,
                initialChip = LibraryChipType.ALBUMS,
            )
        }
        composable<ArtistsDestination> {
            LibraryScreen(
                innerPadding = innerPadding,
                navController = navController,
                onScrolling = onScrolling,
                initialChip = LibraryChipType.ARTISTS,
            )
        }
        composable<SongsDestination> {
            LibraryScreen(
                innerPadding = innerPadding,
                navController = navController,
                onScrolling = onScrolling,
                initialChip = LibraryChipType.SONGS,
            )
        }
        composable<DownloadsDestination> {
            LibraryScreen(
                innerPadding = innerPadding,
                navController = navController,
                onScrolling = onScrolling,
                initialChip = LibraryChipType.DOWNLOADED_PLAYLIST,
            )
        }
        composable<PodcastsDestination> {
            LibraryScreen(
                innerPadding = innerPadding,
                navController = navController,
                onScrolling = onScrolling,
                initialChip = LibraryChipType.FAVORITE_PODCAST,
            )
        }
        // Only reachable as a tab while signed in to YouTube
        composable<MixForYouDestination> {
            MixForYouScreen(
                innerPadding = innerPadding,
                navController = navController,
                onScrolling = onScrolling,
            )
        }
        composable<FullscreenDestination> {
            ForceDarkContent {
                FullscreenPlayer(
                    navController,
                    hideNavBar = hideNavBar,
                    showNavBar = {
                        showNavBar.invoke(true)
                        showNowPlayingSheet.invoke()
                    },
                )
            }
        }
        // Home screen graph
        homeScreenGraph(
            innerPadding = innerPadding,
            navController = navController,
        )
        // Library screen graph
        libraryScreenGraph(
            innerPadding = innerPadding,
            navController = navController,
        )
        // List screen graph
        listScreenGraph(
            innerPadding = innerPadding,
            navController = navController,
        )
        // Login screen graph
        loginScreenGraph(
            innerPadding = innerPadding,
            navController = navController,
            hideBottomBar = hideNavBar,
            showBottomBar = {
                showNavBar(false)
            },
        )
    }
}

/**
 * Returns a tab's visual index within [tabs], or null if [entry] is not one of the active bottom-bar tabs.
 */
private fun getBottomTabIndex(
    entry: NavBackStackEntry,
    tabs: List<BottomNavScreen>,
): Int? {
    val idx = limusicTabIndex(entry, tabs)
    return if (idx >= 0) idx else null
}
