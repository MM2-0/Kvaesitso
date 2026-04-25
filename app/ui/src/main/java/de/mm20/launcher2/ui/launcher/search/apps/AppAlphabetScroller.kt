package de.mm20.launcher2.ui.launcher.search.apps

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor

@Composable
fun AppAlphabetScroller(
    letters: List<String>,
    activeLetter: String?,
    modifier: Modifier = Modifier,
    maxVisibleLetters: Int = 10,
    onLetterTapped: (String) -> Unit,
    onLetterDragged: (String) -> Unit,
) {
    if (letters.isEmpty()) return

    var dragIndex by remember { mutableIntStateOf(-1) }
    val windowSize = maxVisibleLetters.coerceIn(1, letters.size)

    var lastKnownActiveIndex by remember(letters) { mutableIntStateOf(0) }
    val currentActiveIndex = letters.indexOf(activeLetter)
    if (currentActiveIndex >= 0) {
        lastKnownActiveIndex = currentActiveIndex
    }

    val focusIndex = if (dragIndex >= 0) dragIndex else lastKnownActiveIndex
    val windowStart = if (letters.size <= windowSize) {
        0
    } else {
        (focusIndex - windowSize / 2).coerceIn(0, letters.size - windowSize)
    }
    val visibleLetters = letters.subList(windowStart, windowStart + windowSize)
    val latestWindowStart by rememberUpdatedState(windowStart)
    val latestVisibleSize by rememberUpdatedState(visibleLetters.size)

    Column(
        modifier = modifier
            .sizeIn(minWidth = 42.dp)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                RoundedCornerShape(20.dp),
            )
            .padding(vertical = 10.dp, horizontal = 6.dp)
            .pointerInput(letters.size) {
                fun selectByY(y: Float, height: Float) {
                    if (height <= 0f) return
                    val localIndex = floor((y / height) * latestVisibleSize).toInt()
                        .coerceIn(0, latestVisibleSize - 1)
                    val index = (latestWindowStart + localIndex)
                        .coerceIn(0, letters.lastIndex)
                    if (index != dragIndex) {
                        dragIndex = index
                        onLetterDragged(letters[index])
                    }
                }
                detectVerticalDragGestures(
                    onDragStart = {
                        selectByY(it.y, size.height.toFloat())
                    },
                    onVerticalDrag = { change, _ ->
                        selectByY(change.position.y, size.height.toFloat())
                        change.consume()
                    },
                    onDragEnd = {
                        dragIndex = -1
                    },
                    onDragCancel = {
                        dragIndex = -1
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        visibleLetters.forEachIndexed { visibleIndex, letter ->
            val index = windowStart + visibleIndex
            val emphasized = letter == activeLetter || index == dragIndex
            val scale by animateFloatAsState(
                targetValue = if (emphasized) 1.62f else 1.2f,
                label = "alphabetScale",
            )
            Text(
                text = letter,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal
                ),
                textAlign = TextAlign.Center,
                color = if (emphasized) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .sizeIn(minWidth = 28.dp)
                    .heightIn(min = 26.dp)
                    .scale(scale)
                    .clickable { onLetterTapped(letter) }
                    .padding(vertical = 2.dp),
            )
        }
    }
}
