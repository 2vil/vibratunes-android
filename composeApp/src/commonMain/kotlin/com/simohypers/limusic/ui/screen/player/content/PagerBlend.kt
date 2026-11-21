package com.simohypers.limusic.ui.screen.player.content

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.materialkolor.ktx.themeColorOrNull
import com.simohypers.limusic.expect.ui.toImageBitmap
import com.simohypers.limusic.expect.ui.toReadableBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor

/*
 * What a player style needs to change its page WITH the covers on a swipe or a skip (the skip
 * buttons slide the pager too), instead of after the new song's cover has loaded: where the pager
 * sits between two covers, and the colours of the covers around it, read before they are needed.
 */

/** The page the pager is on, or the one it has just slid past; the page after it is coming in. */
internal fun PagerState.pageBelow(): Int = floor(currentPage + currentPageOffsetFraction + PAGE_SETTLE_SLACK).toInt()

/** How far the pager has slid from [below] on to the page after it, 0 when it is on [below]. */
internal fun PagerState.slidPast(below: Int): Float = (currentPage + currentPageOffsetFraction - below).coerceIn(0f, 1f)

/** The cover the artwork pager shows for [page]. */
internal fun NowPlayingContentState.pageCover(page: Int): String? =
    artworkQueue.getOrNull(page)?.thumbnails?.maxByOrNull { it.width * it.height }?.url

/**
 * The cover colour of the page the pager is on, blended into the next page's as the pager slides,
 * for "Adapt colors to artwork". The covers either side are read ahead. Null for a cover without
 * a colour (the theme's own accent then); while a cover is still being read the last colour holds.
 */
@Composable
internal fun rememberPagerCoverColor(state: NowPlayingContentState): Color? {
    val pager = state.artworkPagerState
    val below by remember(pager) { derivedStateOf { pager.pageBelow() } }
    val from = state.pageCover(below)
    val to = state.pageCover(below + 1)
    val colors = rememberCoverColors(listOfNotNull(state.pageCover(below - 1), from, to, state.pageCover(below + 2)))
    val read = from != null && colors.containsKey(from)
    val fromColor = from?.let { colors[it] }
    val toColor = to?.let { colors[it] }
    val held = remember { arrayOfNulls<Color>(1) }
    val color by remember(pager, read, fromColor, toColor) {
        derivedStateOf {
            when {
                !read -> held[0]
                fromColor != null && toColor != null -> lerp(fromColor, toColor, pager.slidPast(below))
                else -> fromColor
            }
        }
    }
    held[0] = color
    return color
}

/** [coverColor] of each of [urls], filled in as they are read; a url read with no colour maps to null. */
@Composable
private fun rememberCoverColors(urls: List<String>): Map<String, Color?> {
    val context = LocalPlatformContext.current
    val colors = remember { mutableStateMapOf<String, Color?>() }
    LaunchedEffect(urls) {
        for (url in urls) if (!colors.containsKey(url)) colors[url] = coverColor(context, url)
    }
    return colors
}

/** The colour "Adapt colors to artwork" takes from the cover at [url]; null for a colourless or unreadable one. */
internal suspend fun coverColor(
    context: PlatformContext,
    url: String,
): Color? {
    val image =
        SingletonImageLoader
            .get(context)
            .execute(ImageRequest.Builder(context).data(url).size(COVER_COLOR_PX).build())
            .image ?: return null
    return withContext(Dispatchers.Default) {
        runCatching { image.toImageBitmap().toReadableBitmap(COVER_COLOR_PX, COVER_COLOR_PX).themeColorOrNull() }.getOrNull()
    }
}

// The cover is scored at this size: the colour depends on which colours cover it and how strongly,
// which a full-resolution read would only make slower to find.
private const val COVER_COLOR_PX = 112

// How close to a page the pager has to be to count as on it, so a settled pager's float error never
// reads as having just left the page before.
private const val PAGE_SETTLE_SLACK = 0.001f
