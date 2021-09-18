package de.mm20.launcher2.ui.component

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.favorites.FavoritesViewModel
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.theme.divider
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterialApi::class, ExperimentalAnimationApi::class)
@Composable
fun DefaultSwipeActions(
    item: Searchable,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val viewModel: FavoritesViewModel = viewModel()

    val isPinned by viewModel.isPinned(item).observeAsState()
    val isHidden by viewModel.isHidden(item).observeAsState()

    val state = rememberSwipeableState(
        SwipeAction.Default,
        confirmStateChange = {
            if (it == SwipeAction.Favorites) {
                if (isPinned == true) {
                    viewModel.unpinItem(item)
                } else {
                    viewModel.pinItem(item)
                }
            }
            false
        }
    )

    val bgColor =
        if (state.offset.value > 0f) colorResource(id = R.color.amber)
        else colorResource(id = R.color.blue)

    val isDismissing =
        state.targetValue == SwipeAction.Favorites || state.targetValue == SwipeAction.Hide

    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat()
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

        val anchors = mapOf(
            0f to SwipeAction.Default,
            width to SwipeAction.Favorites,
            -width to SwipeAction.Hide
        )

        val thresholds = { _: SwipeAction, _: SwipeAction ->
            FractionalThreshold(0.5f)
        }

        Box(
            Modifier.swipeable(
                state = state,
                anchors = anchors,
                thresholds = thresholds,
                orientation = Orientation.Horizontal,
                enabled = enabled && state.currentValue == SwipeAction.Default,
                reverseDirection = isRtl,
                velocityThreshold = 10000.dp
            )
        ) {
            if (enabled) {
                Row(
                    modifier = Modifier.matchParentSize()
                ) {
                    Card(
                        backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.divider),
                        modifier = Modifier.fillMaxSize(),
                        elevation = 0.dp
                    ) {
                        Box(
                            contentAlignment = if (state.offset.value > 0f) {
                                Alignment.CenterStart
                            } else {
                                Alignment.CenterEnd
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(bgColor)
                                    .fillMaxWidth(
                                        animateFloatAsState(
                                            if (isDismissing) 1f else 0f,
                                            tween(200)
                                        ).value
                                    )
                                    .fillMaxHeight()
                            )
                            Icon(
                                imageVector = if (state.offset.value > 0f) {
                                    if (isPinned == true) {
                                        Icons.Rounded.StarBorder
                                    } else {
                                        Icons.Rounded.Star
                                    }
                                } else {
                                    if (isHidden == true) {
                                        Icons.Rounded.Visibility
                                    } else {
                                        Icons.Rounded.VisibilityOff
                                    }
                                },
                                tint = animateColorAsState(if (isDismissing) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface).value,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .scale(animateFloatAsState(if (isDismissing) 1.2f else 1f).value),
                                contentDescription = null
                            )
                        }
                    }
                }

            }
            Row(
                content = content,
                modifier = Modifier.offset { IntOffset(state.offset.value.roundToInt(), 0) }
            )
        }
    }
}

enum class SwipeAction {
    Default,
    Favorites,
    Hide
}