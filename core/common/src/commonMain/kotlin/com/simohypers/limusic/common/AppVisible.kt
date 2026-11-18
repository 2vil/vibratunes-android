package com.simohypers.limusic.common

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * True while a Limusic screen is on view (MainActivity between onStart and onStop). Loops that
 * mostly feed the UI tick slower without one, since a phone in a pocket pays for every wakeup.
 */
object AppVisible {
    val state = MutableStateFlow(false)
}
