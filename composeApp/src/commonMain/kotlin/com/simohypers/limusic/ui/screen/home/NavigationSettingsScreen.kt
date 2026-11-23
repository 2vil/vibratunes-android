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
import com.simohypers.limusic.domain.manager.DataStoreManager
import com.simohypers.limusic.ui.component.BottomNavScreen
import com.simohypers.limusic.ui.component.SettingGroup
import com.simohypers.limusic.ui.icon.DragHandle
import com.simohypers.limusic.ui.icon.LimusicIcons
import com.simohypers.limusic.ui.icon.RestartAlt
import com.simohypers.limusic.ui.theme.typo
import com.simohypers.limusic.viewModel.SharedViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private const val MAX_BOTTOM_BAR_TABS = 6

@Composable
fun NavigationSettingsScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    sharedViewModel: SharedViewModel = koinInject(),
) {
    val bottomBarTabsString by sharedViewModel.bottomBarTabs.collectAsStateWithLifecycle(initialValue = DataStoreManager.DEFAULT_BOTTOM_BAR_TABS)
    val allTabs = BottomNavScreen.allTabs

    val saved = remember(bottomBarTabsString) {
        val activeList = BottomNavScreen.parseTabs(bottomBarTabsString).ifEmpty {
            BottomNavScreen.parseTabs(DataStoreManager.DEFAULT_BOTTOM_BAR_TABS)
        }
        activeList.map { it to true } + allTabs.filter { it !in activeList }.map { it to false }
    }

    var rows by remember { mutableStateOf(saved) }
    var dragging by remember { mutableStateOf<BottomNavScreen?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<BottomNavScreen, Int>() }

    LaunchedEffect(saved) {
        if (dragging == null) {
            rows = saved
        }
    }

    fun commit(next: List<Pair<BottomNavScreen, Boolean>>) {
        rows = next
        val activeOnly = next.filter { it.second }.map { it.first }
        sharedViewModel.setBottomBarTabs(BottomNavScreen.serializeTabs(activeOnly))
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

    val defaultActive = BottomNavScreen.parseTabs(DataStoreManager.DEFAULT_BOTTOM_BAR_TABS)
    val defaultRows = defaultActive.map { it to true } + allTabs.filter { it !in defaultActive }.map { it to false }
    val isDefault = rows == defaultRows
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = innerPadding.calculateTopPadding())
            .verticalScroll(rememberScrollState()),
    ) {
        SettingTopBar(
            title = "Customize Navigation Bar",
            onBack = { navController.navigateUp() },
            windowInsets = WindowInsets(0),
        )
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp), verticalAlignment = Alignment.Top) {
                Text(
                    "Drag a tab to reorder, or switch it on/off (max 6 tabs).",
                    style = typo().bodySmall,
                    modifier = Modifier.weight(1f).padding(top = 10.dp),
                )
                TextButton(
                    onClick = { commit(defaultRows) },
                    enabled = !isDefault,
                ) {
                    Icon(LimusicIcons.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset")
                }
            }

            val activeCount = rows.count { it.second }

            SettingGroup(title = null) {
                var rank = 0
                rows.forEachIndexed { index, (tab, on) ->
                    val position = if (on) ++rank else null
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
                                .clickable {
                                    if (on) {
                                        commit(rows.map { if (it.first == tab) tab to false else it })
                                    } else if (activeCount < MAX_BOTTOM_BAR_TABS) {
                                        commit(rows.map { if (it.first == tab) tab to true else it })
                                    }
                                }
                                .drawBehind {
                                    if (index > 0 && !isDragged) {
                                        drawLine(divider, Offset(56.dp.toPx(), 0f), Offset(size.width, 0f), 0.5.dp.toPx())
                                    }
                                }
                                .padding(end = 16.dp, top = 10.dp, bottom = 10.dp),
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
                                Icon(
                                    LimusicIcons.DragHandle,
                                    contentDescription = null,
                                    tint = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }

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

                            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                tab.icon()
                            }

                            Spacer(Modifier.width(12.dp))

                            Text(
                                stringResource(tab.title),
                                style = typo().labelMedium,
                                color = if (on) scheme.onSurface else scheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )

                            Spacer(Modifier.width(16.dp))

                            Switch(
                                checked = on,
                                enabled = on || activeCount < MAX_BOTTOM_BAR_TABS,
                                onCheckedChange = { checked ->
                                    if (checked && activeCount >= MAX_BOTTOM_BAR_TABS) return@Switch
                                    commit(rows.map { if (it.first == tab) tab to checked else it })
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(innerPadding.calculateBottomPadding() + 24.dp))
        }
    }
}
