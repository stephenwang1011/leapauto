package com.leapauto.app.ui

import android.view.KeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import com.leapauto.app.QuickCommandOrderPolicy
import com.leapauto.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Item contract used by the reusable quick-control reorder surface. */
data class QuickButton(
    val id: String,
    val label: String,
    val iconRes: Int
)

private const val DRAG_LONG_PRESS_TIMEOUT_MS = 300L
private const val DRAG_SCALE = 1.05f
private const val DRAG_ALPHA = 0.9f
private const val DRAG_ROW_HEIGHT_DP = 48
private const val DRAG_ROW_SPACING_DP = 6
private const val DRAG_DASH_LENGTH_DP = 8f
private const val DRAG_DASH_GAP_DP = 6f
private const val DRAG_STROKE_WIDTH_DP = 1f
private const val DRAG_HANDLE_DESCRIPTION = "拖动以重新排序"
private val DRAG_ELEVATION = 8.dp
private val DRAG_ROW_HEIGHT = DRAG_ROW_HEIGHT_DP.dp
private val DRAG_ROW_SPACING = DRAG_ROW_SPACING_DP.dp
private val DRAG_HANDLE_SIZE = 48.dp
private val DRAG_HANDLE_ICON_SIZE = 24.dp

/**
 * A bounded, single-group reorder list for quick actions.
 *
 * The callback is invoked synchronously from the gesture/key event after the
 * list has been reordered. A ViewModel can use it directly, for example:
 *
 *     class QuickButtonOrderViewModel : ViewModel() {
 *         val quickButtons = mutableStateListOf<QuickButton>()
 *
 *         fun onQuickButtonsReordered(order: List<QuickButton>) {
 *             quickButtons.clear()
 *             quickButtons.addAll(order)
 *             repository.save(order.map(QuickButton::id))
 *         }
 *     }
 *
 *     @Composable
 *     fun QuickButtonEditor(viewModel: QuickButtonOrderViewModel) {
 *         DraggableButtonList(
 *             items = viewModel.quickButtons,
 *             onReorder = viewModel::onQuickButtonsReordered
 *         )
 *     }
 *
 * The host screen in [QuickVehicleActions] adapts this callback to the existing
 * VIN-scoped SessionStore order without changing command or security behavior.
 */
@Composable
fun DraggableButtonList(
    items: List<QuickButton>,
    onReorder: (List<QuickButton>) -> Unit,
    modifier: Modifier = Modifier
) {
    val workingItems = remember {
        mutableStateListOf<QuickButton>().apply { addAll(items) }
    }
    val currentOnReorder by rememberUpdatedState(onReorder)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val animationScope = androidx.compose.runtime.rememberCoroutineScope()
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<String?>(null) }
    var targetIndex by remember { mutableIntStateOf(-1) }
    val dragOffset = remember { Animatable(0f) }

    LaunchedEffect(items) {
        if (workingItems != items) {
            workingItems.clear()
            workingItems.addAll(items)
        }
    }

    fun reorder(from: Int, to: Int) {
        if (from !in workingItems.indices || to !in workingItems.indices || from == to) return
        val reordered = workingItems.toList().toMutableList().apply {
            add(to, removeAt(from))
        }
        workingItems.clear()
        workingItems.addAll(reordered)
        currentOnReorder(reordered)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DRAG_ROW_SPACING)
    ) {
        itemsIndexed(
            items = workingItems,
            key = { _, item -> item.id }
        ) { index, item ->
            val isDragging = draggingId == item.id
            val isPlaceholder = draggingId != null && targetIndex == index && !isDragging
            if (isPlaceholder) {
                DragPlaceholder()
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DRAG_ROW_HEIGHT)
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffset.value else 0f
                            scaleX = if (isDragging) DRAG_SCALE else 1f
                            scaleY = if (isDragging) DRAG_SCALE else 1f
                            alpha = if (isDragging) DRAG_ALPHA else 1f
                        }
                        .shadow(
                            elevation = if (isDragging) DRAG_ELEVATION else 0.dp,
                            shape = RoundedCornerShape(12.dp),
                            clip = false
                        ),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDragging) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = item.label,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .size(DRAG_HANDLE_SIZE)
                                .focusable()
                                .semantics {
                                    role = Role.Button
                                    contentDescription = DRAG_HANDLE_DESCRIPTION
                                }
                                .onPreviewKeyEvent { event ->
                                    val nativeEvent = event.nativeKeyEvent
                                    if (nativeEvent.action != KeyEvent.ACTION_DOWN || !nativeEvent.isAltPressed) {
                                        false
                                    } else {
                                        when (nativeEvent.keyCode) {
                                            KeyEvent.KEYCODE_DPAD_UP -> {
                                                reorder(index, index - 1)
                                                true
                                            }

                                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                reorder(index, index + 1)
                                                true
                                            }

                                            else -> false
                                        }
                                    }
                                }
                                .pointerInput(item.id, workingItems.size) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val longPress = withTimeoutOrNull(DRAG_LONG_PRESS_TIMEOUT_MS) {
                                            awaitLongPressOrCancellation(down.id)
                                        } ?: return@awaitEachGesture
                                        val originIndex = workingItems.indexOfFirst { it.id == item.id }
                                        if (originIndex !in workingItems.indices) {
                                            return@awaitEachGesture
                                        }

                                        draggingId = item.id
                                        targetIndex = originIndex
                                        var accumulatedOffset = 0f
                                        var offsetJob: Job? = null
                                        fun snapOffset(value: Float) {
                                            offsetJob?.cancel()
                                            offsetJob = animationScope.launch {
                                                dragOffset.snapTo(value)
                                            }
                                        }
                                        snapOffset(0f)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val rowExtentPx = with(density) {
                                            (DRAG_ROW_HEIGHT + DRAG_ROW_SPACING).toPx()
                                        }
                                        var completed = false
                                        try {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                    ?: break
                                                if (!change.pressed) break
                                                val delta = change.positionChange().y
                                                if (delta != 0f) {
                                                    change.consume()
                                                    accumulatedOffset += delta
                                                    snapOffset(accumulatedOffset)
                                                    targetIndex = QuickCommandOrderPolicy.targetIndex(
                                                        startIndex = originIndex,
                                                        dragOffsetPx = accumulatedOffset,
                                                        rowExtentPx = rowExtentPx,
                                                        itemCount = workingItems.size
                                                    )
                                                }
                                            }
                                            val destination = targetIndex.coerceIn(0, workingItems.lastIndex)
                                            reorder(originIndex, destination)
                                            completed = true
                                        } finally {
                                            offsetJob?.cancel()
                                            animationScope.launch {
                                                if (completed) {
                                                    dragOffset.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy
                                                        )
                                                    )
                                                } else {
                                                    dragOffset.snapTo(0f)
                                                }
                                                draggingId = null
                                                targetIndex = -1
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_drag_handle),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(DRAG_HANDLE_ICON_SIZE)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DragPlaceholder() {
    val outline = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(DRAG_ROW_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
    ) {
        val strokeWidth = DRAG_STROKE_WIDTH_DP.dp.toPx()
        drawRoundRect(
            color = outline,
            cornerRadius = CornerRadius(12.dp.toPx()),
            style = Stroke(
                width = strokeWidth,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(DRAG_DASH_LENGTH_DP.dp.toPx(), DRAG_DASH_GAP_DP.dp.toPx())
                )
            )
        )
    }
}
