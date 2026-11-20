package com.simohypers.limusic.expect

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResultLauncher
import com.simohypers.limusic.logger.Logger
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel as WgTunnel
import com.wireguard.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin
import java.io.BufferedReader
import java.io.File
import java.io.StringReader

actual object Tunnel {
    actual val available: Boolean = true

    private const val TAG = "VibratunesTunnel"

    private val _status = MutableStateFlow(TunnelStatus())
    actual val status: StateFlow<TunnelStatus> = _status

    private val turn = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var backend: GoBackend? = null
    private var config: Config? = null
    /** What the user asked for; like the desktop, the tunnel comes back up when the app does. */
    private var wantedUp = false
    /** An enable is waiting on the system VPN consent dialog; retried from [onAppResumed]. */
    private var pendingUp = false

    private val confFile get() = File(context().filesDir, "tunnel.conf")
    private val prefs get() = context().getSharedPreferences("tunnel", Context.MODE_PRIVATE)

    /**
     * The consent dialog MUST be started with startActivityForResult from a real Activity —
     * ConfirmDialog reads getCallingPackage() and finishes instantly when there is none (which
     * is exactly what a plain context.startActivity produces). MainActivity registers a
     * StartActivityForResult launcher and hands it here.
     */
    private var consentLauncher: ActivityResultLauncher<Intent>? = null

    fun setConsentLauncher(launcher: ActivityResultLauncher<Intent>?) {
        consentLauncher = launcher
    }

    /** The consent dialog answered: bring the tunnel up if granted, else surface the hint. */
    fun onConsentResult() {
        if (!pendingUp) return
        scope.launch { setEnabled(true, fromUi = false) }
    }

    private val wgTunnel =
        object : WgTunnel {
            override fun getName() = "vibratunes"

            override fun onStateChange(newState: WgTunnel.State) {
                // The switch follows what the backend actually runs (revoke, always-on, …).
                _status.update { it.copy(on = newState == WgTunnel.State.UP) }
            }
        }

    private fun context(): Context = getKoin().get()

    /**
     * The heartbeat, called from Application onCreate and MainActivity onResume: restores the
     * stored .conf, retries an enable that was waiting on the consent dialog, and re-syncs the
     * switch with the backend in case something else revoked the VPN.
     */
    fun onAppResumed() {
        wantedUp = prefs.getBoolean("enabled", false)
        val name = prefs.getString("name", "").orEmpty()
        Logger.i(TAG, "onAppResumed: wantedUp=$wantedUp confFile=${confFile.exists()} configSet=${config != null} backendSet=${backend != null}")
        if (!confFile.exists()) {
            config = null
            _status.value = TunnelStatus()
            return
        }
        if (config == null) {
            config =
                try {
                    Config.parse(confFile.bufferedReader()).also { Logger.i(TAG, "stored conf parsed ok") }
                } catch (e: Exception) {
                    Logger.e(TAG, "stored conf parse failed: $e")
                    _status.update { it.copy(hasConf = true, confName = name, error = e.message ?: "parse") }
                    return
                }
        }
        _status.update { it.copy(hasConf = true, confName = name) }
        // The VPN dies with the process: bring it back if the user had it up, and finish an
        // enable that had gone out to the consent dialog. One retry: if the user declined the
        // dialog, the next manual toggle asks again.
        val retry = pendingUp
        pendingUp = false
        if ((wantedUp || retry) && backend?.getState(wgTunnel) != WgTunnel.State.UP) {
            Logger.i(TAG, "auto-restore launch (wantedUp=$wantedUp retry=$retry)")
            scope.launch { setEnabled(true, fromUi = false) }
        } else {
            syncOn()
        }
    }

    actual suspend fun importConf(uri: String): String =
        withContext(Dispatchers.IO) {
            turn.withLock {
                try {
                    val text =
                        context().contentResolver.openInputStream(Uri.parse(uri))
                            ?.bufferedReader()?.use { it.readText() }
                            ?: return@withContext "unreadable"
                    val parsed = Config.parse(BufferedReader(StringReader(text))) // throws on anything but a .conf
                    confFile.writeText(text)
                    val name = displayName(uri) ?: "tunnel.conf"
                    prefs.edit().putString("name", name).apply()
                    config = parsed
                    // Like the desktop: a running tunnel restarts onto the new conf.
                    val be = backend
                    if (be != null && be.getState(wgTunnel) == WgTunnel.State.UP) {
                        be.setState(wgTunnel, WgTunnel.State.DOWN, null)
                        be.setState(wgTunnel, WgTunnel.State.UP, parsed)
                    }
                    wantedUp = prefs.getBoolean("enabled", false)
                    _status.update { it.copy(hasConf = true, confName = name, needsConsent = false, error = "") }
                    ""
                } catch (e: Exception) {
                    e.message ?: e.toString()
                }
            }
        }

    actual suspend fun setEnabled(on: Boolean) = setEnabled(on, fromUi = true)

    actual suspend fun removeConf() =
        withContext(Dispatchers.IO) {
            turn.withLock {
                try {
                    backend?.setState(wgTunnel, WgTunnel.State.DOWN, config)
                } catch (_: Exception) {
                }
                confFile.delete()
                prefs.edit().clear().apply()
                config = null
                wantedUp = false
                pendingUp = false
                _status.value = TunnelStatus()
            }
        }

    private suspend fun setEnabled(on: Boolean, fromUi: Boolean) =
        withContext(Dispatchers.IO) {
            Logger.i(TAG, "setEnabled(on=$on fromUi=$fromUi) enter")
            turn.withLock {
                val cfg = config ?: run {
                    Logger.e(TAG, "setEnabled: no in-memory conf")
                    _status.update { it.copy(error = "no conf") }
                    return@withContext
                }
                val be =
                    try {
                        ensureBackend()
                    } catch (t: Throwable) {
                        Logger.e(TAG, "backend init failed: $t")
                        _status.update { it.copy(error = "$t") }
                        return@withContext
                    }
                wantedUp = on
                prefs.edit().putBoolean("enabled", on).apply()
                if (!on) {
                    try {
                        be.setState(wgTunnel, WgTunnel.State.DOWN, cfg)
                        _status.update { it.copy(on = false, needsConsent = false, error = "") }
                    } catch (e: Exception) {
                        Logger.e(TAG, "down failed: $e")
                        _status.update { it.copy(error = e.message ?: "$e") }
                    }
                    return@withContext
                }
                val consent = VpnService.prepare(context())
                Logger.i(TAG, "prepare -> ${if (consent == null) "granted" else "dialog needed"}")
                if (consent != null) {
                    // Only a user tap may raise the system dialog; a silent restart waits for
                    // the next resume instead.
                    if (fromUi) {
                        pendingUp = true
                        _status.update { it.copy(needsConsent = true, error = "") }
                        val launcher = consentLauncher
                        if (launcher != null) {
                            try {
                                launcher.launch(consent)
                                Logger.i(TAG, "consent dialog launched via result launcher")
                            } catch (t: Throwable) {
                                Logger.e(TAG, "dialog launch failed: $t")
                            }
                        } else {
                            Logger.w(TAG, "no consent launcher registered — cannot ask")
                        }
                    }
                    return@withContext
                }
                try {
                    be.setState(wgTunnel, WgTunnel.State.UP, cfg)
                    Logger.i(TAG, "tunnel UP")
                    _status.update { it.copy(on = true, needsConsent = false, error = "") }
                } catch (e: Exception) {
                    Logger.e(TAG, "up failed: $e")
                    _status.update { it.copy(error = e.message ?: "$e") }
                }
            }
        }

    private fun ensureBackend(): GoBackend = backend ?: GoBackend(context()).also { backend = it }

    private fun syncOn() {
        val on = backend?.getState(wgTunnel) == WgTunnel.State.UP
        _status.update { it.copy(on = on) }
    }

    private fun displayName(uri: String): String? =
        runCatching {
            context().contentResolver
                .query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
}
