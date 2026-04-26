package de.mm20.launcher2.ui.launcher.search.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.theme.transparency.transparency
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class QuickAccessItem(
    val tag: String?,
    val label: String,
)

@Composable
fun AppAlphabetScroller(
    letters: List<String>,
    activeLetter: String?,
    modifier: Modifier = Modifier,
    maxVisibleLetters: Int = 10,
    quickAccessItems: List<QuickAccessItem> = emptyList(),
    selectedQuickAccessTag: String? = null,
    onQuickAccessSelected: (String?) -> Unit = {},
    onQuickAccessHoldChanged: (Boolean) -> Unit = {},
    onLetterTapped: (String) -> Unit,
    onLetterDragged: (String) -> Unit,
) {
    if (letters.isEmpty()) return

    var dragIndex by remember { mutableIntStateOf(-1) }
    var quickAccessPressing by remember { mutableStateOf(false) }
    var quickAccessPopupVisible by remember { mutableStateOf(false) }
    var hoveredQuickAccessIndex by remember { mutableStateOf<Int?>(null) }
    var starBounds by remember { mutableStateOf<Rect?>(null) }
    var scrollerBounds by remember { mutableStateOf<Rect?>(null) }
    var railBounds by remember { mutableStateOf<Rect?>(null) }
    var quickAccessBaseIndex by remember { mutableIntStateOf(0) }
    var lockedPopupTopWindowPx by remember { mutableStateOf<Float?>(null) }

    val view = LocalView.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val popupWidthDp = 220.dp
    val popupItemHeightDp = 40.dp
    val popupGapDp = (-1).dp
    val popupVerticalPaddingDp = 8.dp
    val popupItemSpacingDp = 2.dp

    val hasQuickAccess = letters.firstOrNull() == "*"
    val minWindowSize = if (hasQuickAccess && letters.size > 1) 2 else 1
    val windowSize = maxVisibleLetters.coerceIn(minWindowSize, letters.size)

    var lastKnownActiveIndex by remember(letters) { mutableIntStateOf(0) }
    val currentActiveIndex = letters.indexOf(activeLetter)
    if (currentActiveIndex >= 0) lastKnownActiveIndex = currentActiveIndex

    val focusIndex = if (dragIndex >= 0) dragIndex else lastKnownActiveIndex
    val visibleIndices: List<Int> = if (hasQuickAccess) {
        if (letters.size <= windowSize) {
            letters.indices.toList()
        } else {
            val movableSize = windowSize - 1
            val movableFocus = (focusIndex - 1).coerceIn(0, letters.lastIndex - 1)
            val movableStart = (movableFocus - movableSize / 2).coerceIn(0, (letters.size - 1) - movableSize)
            buildList {
                add(0)
                repeat(movableSize) {
                    add(1 + movableStart + it)
                }
            }
        }
    } else {
        val windowStart = if (letters.size <= windowSize) 0
        else (focusIndex - windowSize / 2).coerceIn(0, letters.size - windowSize)
        List(windowSize) { windowStart + it }
    }

    val visibleLetters = visibleIndices.map { letters[it] }
    val latestVisibleIndices by rememberUpdatedState(visibleIndices)

    fun computePopupTopWindowPx(): Float {
        val star = starBounds ?: return scrollerBounds?.top ?: 0f
        val scroller = scrollerBounds ?: return 0f
        val itemHeightPx = with(density) { popupItemHeightDp.toPx() }
        val spacingPx = with(density) { popupItemSpacingDp.toPx() }
        val verticalPaddingPx = with(density) { popupVerticalPaddingDp.toPx() } * 2f
        val popupHeightPx = if (quickAccessItems.isEmpty()) 0f else {
            (itemHeightPx * quickAccessItems.size) +
                (spacingPx * (quickAccessItems.size - 1)) +
                verticalPaddingPx
        }
        val rawTopPx = star.center.y - popupHeightPx / 2f
        return min(max(rawTopPx, scroller.top), scroller.bottom - popupHeightPx)
    }
    fun computePopupLeftWindowPx(): Float {
        val railLeftWindow = railBounds?.left ?: (scrollerBounds?.right ?: 0f)
        return railLeftWindow - with(density) { popupGapDp.toPx() + popupWidthDp.toPx() }
    }

    Box(
        modifier = modifier.onGloballyPositioned {
            val b = it.boundsInWindow()
            scrollerBounds = b
        }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .zIndex(2f)
                .onGloballyPositioned { railBounds = it.boundsInWindow() }
                .sizeIn(minWidth = 36.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = MaterialTheme.transparency.surface * 0.65f),
                    MaterialTheme.shapes.medium,
                )
                .padding(vertical = 6.dp, horizontal = 4.dp)
                .pointerInput(letters.size, quickAccessPressing, quickAccessPopupVisible) {
                    fun selectByY(y: Float, height: Float) {
                        if (quickAccessPressing || quickAccessPopupVisible) return
                        if (height <= 0f) return
                        val localIndex = floor((y / height) * latestVisibleIndices.size).toInt()
                            .coerceIn(0, latestVisibleIndices.lastIndex)
                        val index = latestVisibleIndices[localIndex]
                        if (index != dragIndex) {
                            dragIndex = index
                            onLetterDragged(letters[index])
                        }
                    }
                    detectVerticalDragGestures(
                        onDragStart = { selectByY(it.y, size.height.toFloat()) },
                        onVerticalDrag = { change, _ ->
                            selectByY(change.position.y, size.height.toFloat())
                            change.consume()
                        },
                        onDragEnd = { dragIndex = -1 },
                        onDragCancel = { dragIndex = -1 },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            visibleLetters.forEachIndexed { visibleIndex, letter ->
                val index = visibleIndices[visibleIndex]
                val emphasized = (letter == activeLetter || index == dragIndex) &&
                    !(letter == "*" && quickAccessPressing)
                val scale by animateFloatAsState(
                    targetValue = if (emphasized) 1.52f else 1.14f,
                    label = "alphabetScale",
                )
                val displayScale = if (letter == "*" && quickAccessItems.isNotEmpty()) 1f else scale

                val letterModifier = if (letter == "*" && quickAccessItems.isNotEmpty()) {
                    Modifier
                        .onGloballyPositioned { coordinates ->
                            starBounds = coordinates.boundsInWindow()
                        }
                        .pointerInput(quickAccessItems, selectedQuickAccessTag) {
                            fun indexFromLocalPosition(localX: Float, localY: Float): Int? {
                                val stepPx = with(density) { popupItemHeightDp.toPx() }
                                val stridePx = stepPx + with(density) { popupItemSpacingDp.toPx() }
                                val fingerWindowX = (starBounds?.left ?: 0f) + localX
                                val fingerWindowY = (starBounds?.top ?: 0f) + localY
                                val popupLeftWindow = computePopupLeftWindowPx()
                                val popupRightWindow = popupLeftWindow + with(density) { popupWidthDp.toPx() }
                                val popupTopWindow = lockedPopupTopWindowPx ?: computePopupTopWindowPx()
                                val contentTop = popupTopWindow + with(density) { popupVerticalPaddingDp.toPx() }
                                val contentBottom = contentTop + (quickAccessItems.size * stepPx) +
                                    ((quickAccessItems.size - 1).coerceAtLeast(0) * with(density) { popupItemSpacingDp.toPx() })

                                if (fingerWindowX < popupLeftWindow || fingerWindowX > popupRightWindow) return null
                                if (fingerWindowY < contentTop || fingerWindowY > contentBottom) return null

                                val centeredY = fingerWindowY - contentTop - (stepPx / 2f)
                                val slot = (centeredY / stridePx).roundToInt()
                                    .coerceIn(0, quickAccessItems.lastIndex)
                                val slotTop = slot * stridePx
                                val slotY = (fingerWindowY - contentTop) - slotTop
                                if (slotY < 0f || slotY > stepPx) return null
                                return slot
                            }

                            fun finishSelection() {
                                hoveredQuickAccessIndex?.let { onQuickAccessSelected(quickAccessItems[it].tag) }
                                quickAccessPressing = false
                                quickAccessPopupVisible = false
                                hoveredQuickAccessIndex = null
                                lockedPopupTopWindowPx = null
                                onQuickAccessHoldChanged(false)
                                view.parent?.requestDisallowInterceptTouchEvent(false)
                            }

                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                quickAccessPressing = true
                                quickAccessBaseIndex = quickAccessItems.indexOfFirst { it.tag == selectedQuickAccessTag }
                                    .takeIf { it >= 0 } ?: 0
                                lockedPopupTopWindowPx = computePopupTopWindowPx()
                                quickAccessPopupVisible = true
                                hoveredQuickAccessIndex = if (quickAccessItems.isNotEmpty()) {
                                    indexFromLocalPosition(offset.x, offset.y)
                                } else null
                                onQuickAccessHoldChanged(true)
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, _ ->
                                    if (quickAccessItems.isNotEmpty()) {
                                        hoveredQuickAccessIndex = indexFromLocalPosition(change.position.x, change.position.y)
                                    }
                                    change.consume()
                                },
                                onDragCancel = { finishSelection() },
                                onDragEnd = { finishSelection() },
                            )
                        }
                } else {
                    Modifier.combinedClickable(
                        onClick = { onLetterTapped(letter) },
                        onLongClick = {},
                    )
                }

                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
                    ),
                    textAlign = TextAlign.Center,
                    color = if (emphasized) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .sizeIn(minWidth = 28.dp)
                        .heightIn(min = 28.dp)
                        .then(letterModifier)
                        .scale(displayScale)
                        .padding(vertical = 2.dp),
                )
            }
        }

        val popupTransitionState = remember { MutableTransitionState(false) }
        popupTransitionState.targetState = quickAccessPopupVisible && quickAccessItems.isNotEmpty()
        if (popupTransitionState.currentState || popupTransitionState.targetState) {
            val popupTopWindow = lockedPopupTopWindowPx ?: computePopupTopWindowPx()
            val scrollerLeftWindow = scrollerBounds?.left ?: 0f
            val scrollerTopWindow = scrollerBounds?.top ?: 0f
            val popupLeftWindow = computePopupLeftWindowPx()
            val popupTopLocal = (popupTopWindow - scrollerTopWindow).roundToInt()
            val popupLeftLocal = (popupLeftWindow - scrollerLeftWindow).roundToInt()
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(popupLeftLocal, popupTopLocal),
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    clippingEnabled = false,
                ),
            ) {
                AnimatedVisibility(
                    visibleState = popupTransitionState,
                    enter = fadeIn() + scaleIn(initialScale = 0.97f) + slideInHorizontally(initialOffsetX = { it / 2 }),
                    exit = fadeOut() + scaleOut(targetScale = 0.98f) + slideOutHorizontally(targetOffsetX = { it / 2 }),
                ) {
                    Column(
                        modifier = Modifier
                            .width(popupWidthDp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = MaterialTheme.transparency.surface * 0.86f),
                                MaterialTheme.shapes.medium,
                            )
                            .padding(vertical = popupVerticalPaddingDp, horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(popupItemSpacingDp),
                    ) {
                        quickAccessItems.forEachIndexed { itemIndex, item ->
                            val selected = selectedQuickAccessTag == item.tag
                            val highlighted = hoveredQuickAccessIndex == itemIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(popupItemHeightDp)
                                    .background(
                                        if (selected || highlighted) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f)
                                        else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.48f),
                                        MaterialTheme.shapes.small,
                                    )
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (item.tag == null) R.drawable.star_20px_filled else R.drawable.tag_20px,
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
