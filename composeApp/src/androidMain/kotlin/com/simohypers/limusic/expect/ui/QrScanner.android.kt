package com.simohypers.limusic.expect.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.simohypers.limusic.qr.QrScanActivity

@Composable
actual fun rememberQrScannerLauncher(onResult: (String?) -> Unit): QrScannerLauncher {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onResult(result.data?.getStringExtra(QrScanActivity.EXTRA_TEXT))
        }
    return object : QrScannerLauncher {
        override fun launch() {
            launcher.launch(Intent(context, QrScanActivity::class.java))
        }
    }
}
