package com.simohypers.limusic.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.simohypers.limusic.common.LibraryChipType
import com.simohypers.limusic.ui.navigation.destination.home.SettingsDestination
import com.simohypers.limusic.ui.navigation.destination.library.LibraryDynamicPlaylistDestination
import com.simohypers.limusic.ui.theme.typo
import org.jetbrains.compose.resources.stringResource
import kotlin.reflect.KClass

fun NavBackStackEntry.matchesTab(
    tab: BottomNavScreen,
): Boolean {
    val dest = destination
    return when {
        dest.hasRoute(LibraryDynamicPlaylistDestination::class) ->
            tab.destination is LibraryDynamicPlaylistDestination
        else ->
            dest.hasRoute(tab.destination::class)
    }
}

/** Position of [entry]'s tab in [tabs], or -1 when it is not a tab. */
fun limusicTabIndex(
    entry: NavBackStackEntry,
    tabs: List<BottomNavScreen> = BottomNavScreen.allTabs,
): Int =
    tabs.indexOfFirst { tab -> entry.matchesTab(tab) }

/** Position of [destination] in the tab bar, or -1 when it is not a tab. */
fun limusicTabIndex(destination: NavDestination, tabs: List<BottomNavScreen> = BottomNavScreen.allTabs): Int =
    tabs.indexOfFirst {
        if (it.destination is LibraryDynamicPlaylistDestination) {
            destination.hasRoute(LibraryDynamicPlaylistDestination::class)
        } else {
            destination.hasRoute(it.destination::class)
        }
    }

/**
 * Opens a tab the way tapping it does. A tab opened with a plain navigate (an app link) sits on
 * Home's stack, and the next Home tap then saves and restores it straight back.
 */
fun NavController.navigateToTab(
    destination: Any,
) {
    navigate(destination) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * The bottom tabs. The tab you are in is the last tab on the back stack, so it stays lit on the
 * album or artist pages opened from it. Tapping it there goes back to the tab's root; tapping it on
 * the root calls [onReselect] (scroll to top or refresh).
 */
@Composable
fun LimusicNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier,
    tabs: List<BottomNavScreen> = listOf(BottomNavScreen.Home, BottomNavScreen.Search, BottomNavScreen.Library),
    currentLibraryChip: LibraryChipType = LibraryChipType.YOUR_LIBRARY,
    onLibraryTabRequest: (LibraryChipType?, Boolean) -> Unit = { _, _ -> },
    onReselect: (KClass<*>) -> Unit = {},
) {
    val backStack by navController.currentBackStack.collectAsState()
    val tabEntry = backStack.indexOfLast { entry ->
        tabs.any { tab -> entry.matchesTab(tab) }
    }
    val stackTab = backStack.getOrNull(tabEntry)?.let { entry ->
        limusicTabIndex(entry, tabs)
    } ?: -1
    // Settings and the pages opened from it belong to no tab, so none is lit there (unless Settings is a bottom tab)
    val inSettings = backStack.indexOfLast { it.destination.hasRoute(SettingsDestination::class) } > tabEntry
    val isSettingsTab = tabs.any { it.destination is SettingsDestination }
    val currentTab = if (inSettings && !isSettingsTab) -1 else stackTab
    val onTabRoot = backStack.lastOrNull()?.let { entry ->
        tabs.getOrNull(currentTab)?.let { tab -> entry.matchesTab(tab) }
    } == true
    Column(modifier) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == currentTab
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        val libraryChip = tab.libraryChip
                        if (libraryChip != null) {
                            onLibraryTabRequest(libraryChip, true)
                        } else {
                            onLibraryTabRequest(null, false)
                        }

                        val targetOnStack = backStack.any { entry ->
                            if (tab.destination is LibraryDynamicPlaylistDestination) {
                                entry.destination.hasRoute(LibraryDynamicPlaylistDestination::class)
                            } else {
                                entry.destination.hasRoute(tab.destination::class)
                            }
                        }

                        if (selected && onTabRoot) {
                            onReselect(tab.destination::class)
                        } else if (index == stackTab) {
                            // Restoring this tab's saved stack would bring the same page back, so pop to its root.
                            navController.popBackStack(tab.destination, inclusive = false)
                        } else if (targetOnStack) {
                            // Target tab is already in the back stack under a sub-screen, pop back to it.
                            navController.popBackStack(tab.destination, inclusive = false)
                        } else {
                            navController.navigateToTab(tab.destination)
                        }
                    },
                    icon = { tab.icon() },
                    label = {
                        Text(
                            text = stringResource(tab.title),
                            style = typo().labelSmall.copy(
                                fontSize = 10.5.sp,
                                textAlign = TextAlign.Center,
                                letterSpacing = (-0.2).sp,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                        )
                    },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                )
            }
        }
    }
}

/**
 * The top bar Home and Library share: the title on the left, the tab's own [actions] on the right. Draws below the status bar and nothing behind itself, so the screen decides the ground.
 */
@Composable
fun LimusicTabTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = typo().titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(text = subtitle, style = typo().bodySmall, maxLines = 1)
            }
        }
        actions()
    }
}
