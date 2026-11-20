package com.simohypers.limusic.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
val LimusicIcons.QrCodeScanner: ImageVector
  get() {
    if (_QrCodeScanner != null) {
      return _QrCodeScanner!!
    }
    _QrCodeScanner =
      ImageVector.Builder(
          name = "QrCodeScanner",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          addPath(
            pathData =
              addPathNodes(
                // Material Symbols `qr_code_scanner` (24dp).
                "M9.5,2H5C3.34,2,2,3.34,2,5v4.5h2V5c0-0.55,0.45-1,1-1h4.5V2z " +
                  "M14.5,2v2H19c0.55,0,1,0.45,1,1v4.5h2V5c0-1.66-1.34-3-3-3H14.5z " +
                  "M22,14.5h-2V19c0,0.55-0.45,1-1,1h-4.5v2H19c1.66,0,3-1.34,3-3V14.5z " +
                  "M2,14.5V19c0,1.66,1.34,3,3,3h4.5v-2H5c-0.55,0-1-0.45-1-1v-4.5H2z " +
                  "M12,10.5c-0.83,0-1.5,0.67-1.5,1.5s0.67,1.5,1.5,1.5s1.5-0.67,1.5-1.5S12.83,10.5,12,10.5z " +
                  "M7.5,10.5c-0.83,0-1.5,0.67-1.5,1.5s0.67,1.5,1.5,1.5S9,12.83,9,12S8.33,10.5,7.5,10.5z " +
                  "M16.5,10.5c-0.83,0-1.5,0.67-1.5,1.5s0.67,1.5,1.5,1.5s1.5-0.67,1.5-1.5S17.33,10.5,16.5,10.5z " +
                  "M7.5,6C6.67,6,6,6.67,6,7.5S6.67,9,7.5,9S9,8.33,9,7.5S8.33,6,7.5,6z " +
                  "M7.5,15c-0.83,0-1.5,0.67-1.5,1.5S6.67,18,7.5,18S9,17.33,9,16.5S8.33,15,7.5,15z " +
                  "M16.5,15c-0.83,0-1.5,0.67-1.5,1.5s0.67,1.5,1.5,1.5s1.5-0.67,1.5-1.5S17.33,15,16.5,15z " +
                  "M16.5,6C15.67,6,15,6.67,15,7.5S15.67,9,16.5,9S18,8.33,18,7.5S17.33,6,16.5,6z " +
                  "M12,15c-0.83,0-1.5,0.67-1.5,1.5s0.67,1.5,1.5,1.5s1.5-0.67,1.5-1.5S12.83,15,12,15z",
              ),
            fill = SolidColor(Color.Black),
          )
        }
        .build()
    return _QrCodeScanner!!
  }

private var _QrCodeScanner: ImageVector? = null
