package com.simohypers.limusic.ui.navigation.destination.library

import kotlinx.serialization.Serializable

/** Liked songs as a tab: its own route, so the bar never takes Library's Liked songs page for it. */
@Serializable
data object LikedSongsDestination
