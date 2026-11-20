package com.simohypers.limusic.expect.ui

import androidx.compose.runtime.Composable

interface QrScannerLauncher {
    fun launch()
}

/**
 * Opens the camera to scan one QR code. [onResult] gets the decoded text — a pairing invite like
 * the desktop's, `CODE` or `CODE@host` — or null if the scanner was closed without a read.
 */
@Composable
expect fun rememberQrScannerLauncher(onResult: (String?) -> Unit): QrScannerLauncher
