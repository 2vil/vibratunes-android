package com.simohypers.limusic.expect

import kotlinx.coroutines.flow.StateFlow

/** What the Settings tunnel card shows. [error] carries a raw backend message, not a resource. */
data class TunnelStatus(
    val hasConf: Boolean = false,
    val confName: String = "",
    val on: Boolean = false,
    val needsConsent: Boolean = false,
    val error: String = "",
)

/**
 * The embedded WireGuard tunnel — the phone-side twin of the desktop's TunnelManager. Import a
 * .conf, flip the switch, and everything the app asks routes through it at the IP layer (a
 * VpnService, not app-level proxies like the desktop needs for mpv). Android-only: on other
 * platforms `available` is false and nothing else gets called.
 */
expect object Tunnel {
    val available: Boolean
    val status: StateFlow<TunnelStatus>

    /** Reads and stores the picked .conf; returns an error message, or "" on success. */
    suspend fun importConf(uri: String): String

    /** Brings the tunnel up (asking for VPN consent if needed) or takes it down. */
    suspend fun setEnabled(on: Boolean)

    /** Stops the tunnel and forgets the imported .conf. */
    suspend fun removeConf()
}
