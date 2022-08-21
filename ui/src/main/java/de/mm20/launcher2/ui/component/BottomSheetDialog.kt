package de.mm20.launcher2.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.*
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.mm20.launcher2.ui.ktx.toDp
import de.mm20.launcher2.ui.ktx.toPixels
import kotlin.math.roundToInt

@Composable
fun BottomSheetDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    swipeToDismiss: () -> Boolean = { true },
    dismissOnBackPress: () -> Boolean = { true },
    content: @Composable () -> Unit,
) {
    val swipeState = remember {
        SwipeableState(
            initialValue = SwipeState.Dismiss,
            confirmStateChange = {
                if (it == SwipeState.Dismiss) {
                    if (swipeToDismiss()) onDismissRequest()
                    else return@SwipeableState false
                }
                return@SwipeableState true
            }
        )
    }


    val nestedScrollConnection = remember {
        object : NestedScrollConnection {

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.toFloat()
                return if (delta < 0 && source == NestedScrollSource.Drag) {
                    swipeState.performDrag(delta).toOffset()
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return if (source == NestedScrollSource.Drag) {
                    swipeState.performDrag(available.toFloat()).toOffset()
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val toFling = Offset(available.x, available.y).toFloat()
                return if (toFling < 0 && swipeState.offset.value > 0) {
                    swipeState.performFling(velocity = toFling)
                    // since we go to the anchor with tween settling, consume all for the best UX
                    available
                } else {
                    Velocity.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                swipeState.performFling(velocity = Offset(available.x, available.y).toFloat())
                return available
            }

            private fun Float.toOffset(): Offset = Offset(0f, this)

            private fun Offset.toFloat(): Float = this.y
        }
    }

    Dialog(
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = dismissOnBackPress()
        ),
        onDismissRequest = onDismissRequest,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            propagateMinConstraints = true,
            contentAlignment = Alignment.BottomCenter
        ) {
            val maxHeightPx = maxHeight.toPixels()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Bottom)
                    .clipToBounds(),
                verticalArrangement = Arrangement.Bottom
            ) {
                var height by remember {
                    mutableStateOf(maxHeightPx)
                }

                LaunchedEffect(null) {
                    swipeState.animateTo(SwipeState.Peek)
                }

                val heightDp = height.toDp()
                val peekHeight = (height - maxHeightPx / 2).coerceAtLeast(0f)
                val anchors = mutableMapOf(
                    peekHeight to SwipeState.Peek,
                    height to SwipeState.Dismiss,
                ).also {
                    if (peekHeight > 0f) {
                        it[0f] = SwipeState.Full
                    }
                }
                Surface(
                    modifier = Modifier
                        .nestedScroll(nestedScrollConnection)
                        .swipeable(
                            swipeState,
                            anchors = anchors,
                            orientation = Orientation.Vertical,
                            thresholds = { _, to ->
                                if (to == SwipeState.Dismiss) {
                                    FixedThreshold(heightDp - 48.dp)
                                } else {
                                    FractionalThreshold(0.5f)
                                }
                            },
                            resistance = null
                        )
                        .animateContentSize()
                        .onSizeChanged {
                            height = it.height.toFloat()
                        }
                        .offset { IntOffset(0, swipeState.offset.value.roundToInt()) }
                        .fillMaxWidth()
                        .weight(1f, false),
                    shape = MaterialTheme.shapes.extraLarge.copy(
                        bottomStart = CornerSize(0),
                        bottomEnd = CornerSize(0),
                    ),
                    shadowElevation = 16.dp,
                ) {
                    Column {
                        CenterAlignedTopAppBar(
                            title = title,
                            actions = actions,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            propagateMinConstraints = true,
                            contentAlignment = Alignment.Center
                        ) {
                            content()
                        }

                    }
                }
                val elevation by animateDpAsState(if (swipeState.offset.value == 0f) 0.dp else 1.dp)
                Surface(
                    modifier = Modifier
                        .wrapContentHeight()
                        .fillMaxWidth(),
                    tonalElevation = elevation,
                ) {

                    if (confirmButton != null || dismissButton != null) {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (dismissButton != null) {
                                dismissButton()
                            }
                            if (confirmButton != null && dismissButton != null) {
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            if (confirmButton != null) {
                                confirmButton()
                            }
                        }

                    }

                }
            }
        }
    }
}

private enum class SwipeState {
    Full, Peek, Dismiss
}