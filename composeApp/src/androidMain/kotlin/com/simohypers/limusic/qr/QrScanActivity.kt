package com.simohypers.limusic.qr

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import limusic.composeapp.generated.resources.Res
import limusic.composeapp.generated.resources.devices_scan_hint
import org.jetbrains.compose.resources.stringResource
import java.util.concurrent.Executors

/**
 * The phone side of "link a phone": a camera screen that reads the QR the desktop draws next to
 * its pairing code. Decodes QR only (ZXing over the camera's luminance plane) and finishes with
 * the text in [EXTRA_TEXT]; no result means the user backed out or denied the camera.
 */
class QrScanActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_TEXT = "com.simohypers.limusic.qr.TEXT"
    }

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else finish()
        }

    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private val reader =
        MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }

    /** Set by the composition's AndroidView factory; the camera binds to it once it exists. */
    private var previewView: PreviewView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { QrScanContent() }
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        analyzerExecutor.shutdown()
        super.onDestroy()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                val view = previewView
                if (view == null) {
                    // The composition hasn't laid out yet; a beat later it will have.
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                        { startCamera() },
                        100,
                    )
                    return@addListener
                }
                val provider = future.get()
                val preview =
                    Preview.Builder().build().apply { setSurfaceProvider(view.surfaceProvider) }
                val analysis =
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .apply { setAnalyzer(analyzerExecutor, ::decode) }
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun decode(proxy: ImageProxy) {
        val result =
            try {
                val plane = proxy.planes[0]
                val width = proxy.width
                val height = proxy.height
                // The Y plane may be padded (rowStride > width): compact it row by row.
                val bytes = ByteArray(width * height)
                plane.buffer.rewind()
                if (plane.rowStride == width) {
                    plane.buffer.get(bytes, 0, bytes.size)
                } else {
                    for (row in 0 until height) {
                        plane.buffer.position(row * plane.rowStride)
                        plane.buffer.get(bytes, row * width, width)
                    }
                }
                val source =
                    PlanarYUVLuminanceSource(
                        bytes,
                        width,
                        height,
                        0,
                        0,
                        width,
                        height,
                        false,
                    )
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            } catch (_: NotFoundException) {
                null
            } catch (_: Exception) {
                null
            } finally {
                reader.reset()
            }
        if (result != null) {
            val text = result.text.trim()
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_TEXT, text))
            finish()
        }
        proxy.close()
    }

    @Composable
    private fun QrScanContent() {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).also { previewView = it }
                },
            )
            Text(
                stringResource(Res.string.devices_scan_hint),
                color = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = 48.dp, start = 24.dp, end = 24.dp),
            )
        }
    }
}
