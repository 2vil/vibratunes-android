package com.simohypers.limusic.ui.component

import com.simohypers.limusic.ui.component.BottomNavScreen.Albums
import com.simohypers.limusic.ui.component.BottomNavScreen.Home
import com.simohypers.limusic.ui.component.BottomNavScreen.Library
import com.simohypers.limusic.ui.component.BottomNavScreen.LikedSongs
import com.simohypers.limusic.ui.component.BottomNavScreen.Podcasts
import com.simohypers.limusic.ui.component.BottomNavScreen.Search
import com.simohypers.limusic.ui.component.BottomNavScreen.Songs
import kotlin.test.Test
import kotlin.test.assertEquals

class BottomNavScreenTest {
    @Test
    fun nothingSavedIsTheDefaultBar() {
        assertEquals(listOf(Home, Search, Library), BottomNavScreen.parseTabs(""))
    }

    @Test
    fun savedOrderRoundTrips() {
        val tabs = listOf(Library, Home, LikedSongs)
        assertEquals(tabs, BottomNavScreen.parseTabs(BottomNavScreen.serializeTabs(tabs)))
    }

    @Test
    fun homeIsAlwaysThereAndTheBarStopsAtFive() {
        // Unknown keys (a tab a later build dropped) and repeats are skipped, Home comes back first.
        assertEquals(listOf(Home, Albums, Songs), BottomNavScreen.parseTabs("albums,settings,albums,songs"))
        assertEquals(listOf(Home, Search, Library, Albums, Songs), BottomNavScreen.parseTabs("search,library,albums,songs,podcasts"))
        assertEquals(BottomNavScreen.MAX_TABS, BottomNavScreen.parseTabs("home,search,library,albums,songs,podcasts").size)
        assertEquals(false, Podcasts in BottomNavScreen.parseTabs("home,search,library,albums,songs,podcasts"))
    }
}
