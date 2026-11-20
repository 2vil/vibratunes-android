package com.simohypers.limusic.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
val LimusicIcons.PlaylistPlay: ImageVector
  get() {
    if (_PlaylistPlay != null) {
      return _PlaylistPlay!!
    }
    _PlaylistPlay =
      ImageVector.Builder(
          name = "PlaylistPlay",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
          autoMirror = true,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(4f, 16f)
            quadToRelative(-0.425f, 0f, -0.7125f, -0.2875f)
            reflectiveQuadTo(3f, 15f)
            quadToRelative(0f, -0.425f, 0.2875f, -0.7125f)
            reflectiveQuadTo(4f, 14f)
            horizontalLineToRelative(6f)
            quadToRelative(0.425f, 0f, 0.7125f, 0.2875f)
            reflectiveQuadTo(11f, 15f)
            quadToRelative(0f, 0.425f, -0.2875f, 0.7125f)
            reflectiveQuadTo(10f, 16f)
            horizontalLineTo(4f)
            close()
            moveToRelative(0f, -4f)
            quadToRelative(-0.425f, 0f, -0.7125f, -0.2875f)
            reflectiveQuadTo(3f, 11f)
            quadToRelative(0f, -0.425f, 0.2875f, -0.7125f)
            reflectiveQuadTo(4f, 10f)
            horizontalLineToRelative(10f)
            quadToRelative(0.425f, 0f, 0.7125f, 0.2875f)
            reflectiveQuadTo(15f, 11f)
            quadToRelative(0f, 0.425f, -0.2875f, 0.7125f)
            reflectiveQuadTo(14f, 12f)
            horizontalLineTo(4f)
            close()
            moveToRelative(0f, -4f)
            quadToRelative(-0.425f, 0f, -0.7125f, -0.2875f)
            reflectiveQuadTo(3f, 7f)
            quadToRelative(0f, -0.425f, 0.2875f, -0.7125f)
            reflectiveQuadTo(4f, 6f)
            horizontalLineToRelative(10f)
            quadToRelative(0.425f, 0f, 0.7125f, 0.2875f)
            reflectiveQuadTo(15f, 7f)
            quadToRelative(0f, 0.425f, -0.2875f, 0.7125f)
            reflectiveQuadTo(14f, 8f)
            horizontalLineTo(4f)
            close()
            moveToRelative(12.775f, 12.475f)
            quadToRelative(-0.125f, 0.075f, -0.25f, 0.075f)
            reflectiveQuadToRelative(-0.25f, -0.05f)
            quadToRelative(-0.125f, -0.05f, -0.2f, -0.1625f)
            reflectiveQuadToRelative(-0.075f, -0.2625f)
            verticalLineToRelative(-6.15f)
            quadToRelative(0f, -0.15f, 0.075f, -0.2625f)
            reflectiveQuadToRelative(0.2f, -0.1625f)
            quadToRelative(0.125f, -0.05f, 0.25f, -0.05f)
            reflectiveQuadToRelative(0.25f, 0.075f)
            lineToRelative(4.6f, 3.05f)
            quadToRelative(0.125f, 0.075f, 0.175f, 0.1875f)
            reflectiveQuadToRelative(0.05f, 0.2375f)
            quadToRelative(0f, 0.125f, -0.05f, 0.2375f)
            reflectiveQuadToRelative(-0.175f, 0.1875f)
            lineTo(16.775f, 20.475f)
            close()
          }
        }
        .build()
    return _PlaylistPlay!!
  }

private var _PlaylistPlay: ImageVector? = null
