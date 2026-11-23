package com.simohypers.limusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.simohypers.limusic.domain.manager.DataStoreManager
import com.simohypers.limusic.ui.icon.DragHandle
import com.simohypers.limusic.ui.icon.LimusicIcons
import com.simohypers.limusic.ui.icon.RestartAlt
import com.simohypers.limusic.ui.theme.AppThemeContent
import com.simohypers.limusic.ui.theme.rounded
import com.simohypers.limusic.ui.theme.typo
import org.jetbrains.compose.resources.stringResource

private const val MAX_BOTTOM_BAR_TABS = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationBarSheet(
    activeTabsString: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelectTab: (BottomNavScreen) -> Unit = {},
) {
    val allTabs = BottomNavScreen.allTabs
    val initialActive = BottomNavScreen.parseTabs(activeTabsString).ifEmpty {
        BottomNavScreen.parseTabs(DataStoreManager.DEFAULT_BOTTOM_BAR_TABS)
    }

    var rows by remember(activeTabsString) {
        mutableStateOf(
            initialActive + allTabs.filter { it !in initialActive }
        )
    }
    var dragging by remember { mutableStateOf<BottomNavScreen?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<BottomNavScreen, Int>() }

    fun commit(next: List<BottomNavScreen>) {
        rows = next
        onSave(BottomNavScreen.serializeTabs(next))
    }

    fun heightOf(tab: BottomNavScreen?): Int = tab?.let { heights[it] } ?: 56

    fun dragBy(dy: Float) {
        val tab = dragging ?: return
        dragOffset += dy
        while (true) {
            val i = rows.indexOf(tab)
            val by =
                when {
                    dragOffset > heightOf(rows.getOrNull(i + 1)) / 2f && i < rows.lastIndex -> 1
                    dragOffset < -heightOf(rows.getOrNull(i - 1)) / 2f && i > 0 -> -1
                    else -> break
                }
            val passed = heightOf(rows[i + by])
            val mutable = rows.toMutableList()
            mutable.add(i + by, mutable.removeAt(i))
            rows = mutable
            dragOffset -= by * passed
        }
    }

    fun endDrag() {
        if (dragging == null) return
        dragging = null
        dragOffset = 0f
        commit(rows)
    }

    val isDefault = rows == BottomNavScreen.parseTabs(DataStoreManager.DEFAULT_BOTTOM_BAR_TABS)
    val scheme = MaterialTheme.colorScheme

    AppThemeContent {
        val colors = rememberSurfaceDarkColors()
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.Transparent,
            contentColor = Color.Transparent,
            dragHandle = null,
            scrimColor = Color.Black.copy(alpha = .5f),
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = rounded(topStart = 20.dp, topEnd = 20.dp),
                colors = CardDefaults.cardColors().copy(containerColor = colors.container),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.align(Alignment.CenterHorizontally).width(60.dp).height(4.dp),
                        colors = CardDefaults.cardColors().copy(containerColor = colors.handle),
                        shape = RoundedCornerShape(50),
                    ) {}
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Customize Navigation Bar", style = typo().titleMedium, color = colors.content)
                            Text(
                                "Reorder or choose tabs for bottom bar (max 6 tabs)",
                                style = typo().bodySmall,
                                color = colors.subtitle,
                            )
                        }
                        TextButton(
                            onClick = {
                                val defaultTabs = BottomNavScreen.parseTabs(DataStoreManager.DEFAULT_BOTTOM_BAR_TABS)
                                commit(defaultTabs + allTabs.filter { it !in defaultTabs })
                            },
                            enabled = !isDefault,
                        ) {
                            Icon(LimusicIcons.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reset")
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    val activeTabs = parseActiveTabs(activeTabsString)
                    val activeCount = rows.count { it in activeTabs }

                    SettingGroup(title = null) {
                        rows.forEachIndexed { index, tab ->
                            val isActive = tab in activeTabs
                            val position = if (isActive) activeTabs.indexOf(tab) + 1 else null
                            key(tab.key) {
                                val isDragged = dragging == tab
                                val divider = scheme.outlineVariant
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onSizeChanged { heights[tab] = it.height }
                                        .zIndex(if (isDragged) 1f else 0f)
                                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                                        .background(if (isDragged) scheme.surfaceContainerHighest else Color.Transparent)
                                        .clickable {
                                            if (!isActive && activeCount < MAX_BOTTOM_BAR_TABS) {
                                                val nextActive = activeTabs + tab
                                                commit(nextActive + allTabs.filter { it !in nextActive })
                                            }
                                            onSelectTab(tab)
                                        }
                                        .drawBehind {
                                            if (index > 0 && !isDragged) {
                                                drawLine(divider, Offset(56.dp.toPx(), 0f), Offset(size.width, 0f), 0.5.dp.toPx())
                                            }
                                        }
                                        .padding(end = 16.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
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
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(if (isActive) scheme.primary.copy(alpha = 0.14f) else scheme.onSurface.copy(alpha = 0.06f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            position?.toString() ?: "-",
                                            style = typo().labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                            color = if (isActive) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.5f),
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
                                        color = if (isActive) scheme.onSurface else scheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )

                                    Spacer(Modifier.width(16.dp))

                                    Switch(
                                        checked = isActive,
                                        enabled = isActive || activeCount < MAX_BOTTOM_BAR_TABS,
                                        onCheckedChange = { checked ->
                                            if (checked && activeCount >= MAX_BOTTOM_BAR_TABS) return@Switch
                                            val nextActive = if (checked) activeTabs + tab else activeTabs - tab
                                            commit(nextActive + allTabs.filter { it !in nextActive })
                                            if (checked) {
                                                onSelectTab(tab)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    EndOfModalBottomSheet()
                }
            }
        }
    }
}

private fun parseActiveTabs(savedString: String): List<BottomNavScreen> {
    val parsed = BottomNavScreen.parseTabs(savedString)
    return parsed.ifEmpty { BottomNavScreen.parseTabs(DataStoreManager.DEFAULT_BOTTOM_BAR_TABS) }
}
