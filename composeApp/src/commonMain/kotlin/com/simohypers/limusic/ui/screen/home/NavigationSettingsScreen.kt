package com.simohypers.limusic.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.simohypers.limusic.ui.component.BottomNavScreen
import com.simohypers.limusic.ui.component.SettingGroup
import com.simohypers.limusic.ui.icon.DragHandle
import com.simohypers.limusic.ui.icon.LimusicIcons
import com.simohypers.limusic.ui.icon.RestartAlt
import com.simohypers.limusic.ui.theme.typo
import com.simohypers.limusic.viewModel.SharedViewModel
import limusic.composeapp.generated.resources.Res
import limusic.composeapp.generated.resources.nav_bar_tabs
import limusic.composeapp.generated.resources.nav_bar_tabs_hint
import limusic.composeapp.generated.resources.reset
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Every tab with whether it is in the bar: the bar's tabs first, in their order, then the rest. */
private fun tabRows(saved: String): List<Pair<BottomNavScreen, Boolean>> {
    val on = BottomNavScreen.parseTabs(saved)
    return on.map { it to true } + BottomNavScreen.allTabs.filter { it !in on }.map { it to false }
}

/** Which tabs the bottom bar shows and in what order. The same drag list as Lyrics sources. */
@Composable
fun NavigationSettingsScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    sharedViewModel: SharedViewModel = koinInject(),
) {
    val savedTabs by sharedViewModel.bottomBarTabs.collectAsStateWithLifecycle()
    val saved = remember(savedTabs) { tabRows(savedTabs) }
    // The rows a drag moves. They follow [saved] except mid-drag; the order is written on release.
    var rows by remember { mutableStateOf(saved) }
    var dragging by remember { mutableStateOf<BottomNavScreen?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<BottomNavScreen, Int>() }
    LaunchedEffect(saved) { if (dragging == null) rows = saved }

    fun commit(next: List<Pair<BottomNavScreen, Boolean>>) {
        rows = next
        sharedViewModel.setBottomBarTabs(BottomNavScreen.serializeTabs(next.filter { it.second }.map { it.first }))
    }

    fun dragBy(dy: Float) {
        val tab = dragging ?: return
        dragOffset += dy
        while (true) {
            val i = rows.indexOfFirst { it.first == tab }
            val by =
                when {
                    i < rows.lastIndex && dragOffset > (heights[rows[i + 1].first] ?: 0) / 2f -> 1
                    i > 0 && dragOffset < -(heights[rows[i - 1].first] ?: 0) / 2f -> -1
                    else -> break
                }
            val passed = heights[rows[i + by].first] ?: 0
            rows = rows.toMutableList().apply { add(i + by, removeAt(i)) }
            dragOffset -= by * passed
        }
    }

    fun endDrag() {
        if (dragging == null) return
        dragging = null
        dragOffset = 0f
        commit(rows)
    }

    val defaultRows = remember { tabRows("") }
    val activeCount = rows.count { it.second }
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = innerPadding.calculateTopPadding())
            .verticalScroll(rememberScrollState()),
    ) {
        SettingTopBar(title = stringResource(Res.string.nav_bar_tabs), onBack = { navController.navigateUp() }, windowInsets = WindowInsets(0))
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp), verticalAlignment = Alignment.Top) {
                Text(
                    stringResource(Res.string.nav_bar_tabs_hint, BottomNavScreen.MAX_TABS),
                    style = typo().bodySmall,
                    modifier = Modifier.weight(1f).padding(top = 10.dp),
                )
                TextButton(onClick = { commit(defaultRows) }, enabled = rows != defaultRows) {
                    Icon(LimusicIcons.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.reset))
                }
            }
            SettingGroup(title = null) {
                var rank = 0
                rows.forEachIndexed { index, (tab, on) ->
                    val position = if (on) ++rank else null
                    // Home stays in the bar, since the app opens on it; it can still move.
                    val canToggle = tab != BottomNavScreen.Home && (on || activeCount < BottomNavScreen.MAX_TABS)
                    val toggle = { commit(rows.map { if (it.first == tab) tab to !on else it }) }
                    key(tab.key) {
                        val isDragged = dragging == tab
                        val divider = scheme.outlineVariant
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .onSizeChanged { heights[tab] = it.height }
                                .zIndex(if (isDragged) 1f else 0f)
                                .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                                .background(if (isDragged) scheme.surfaceContainerHighest else Color.Transparent)
                                .clickable(enabled = canToggle, onClick = toggle)
                                .drawBehind {
                                    if (index > 0 && !isDragged) drawLine(divider, Offset(56.dp.toPx(), 0f), Offset(size.width, 0f), 0.5.dp.toPx())
                                }.padding(end = 16.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .pointerInput(tab) {
                                        detectDragGestures(
                                            onDragStart = {
                                                dragging = tab
                                                dragOffset = 0f
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragBy(amount.y)
                                            },
                                            onDragEnd = { endDrag() },
                                            onDragCancel = { endDrag() },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(LimusicIcons.DragHandle, contentDescription = null, tint = scheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                            }
                            // Where it sits in the bar, counting only the tabs that are on.
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (on) scheme.primary.copy(alpha = 0.14f) else scheme.onSurface.copy(alpha = 0.06f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    position?.toString() ?: "-",
                                    style = typo().labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                    color = if (on) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Icon(tab.icon, contentDescription = null, tint = if (on) scheme.onSurface else scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(tab.title),
                                style = typo().labelMedium,
                                color = if (on) scheme.onSurface else scheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(16.dp))
                            Switch(checked = on, enabled = canToggle, onCheckedChange = { toggle() })
                        }
                    }
                }
            }
            Spacer(Modifier.height(innerPadding.calculateBottomPadding() + 24.dp))
        }
    }
}
