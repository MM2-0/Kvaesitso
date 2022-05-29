package de.mm20.launcher2.ui.launcher.modals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideIn
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.grid.SearchResultGrid
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterialApi::class)
@Composable
fun HiddenItemsSheet(
    onDismiss: () -> Unit
) {
    val viewModel: HiddenItemsSheetVM = viewModel()

    Dialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = { onDismiss() }) {
        val animationState = remember {
            MutableTransitionState(false).apply {
                targetState = true
            }
        }

        val swipeState =
            rememberSwipeableState(initialValue = SwipeState.Default) {
                if (it == SwipeState.Dismiss) onDismiss()
                return@rememberSwipeableState true
            }

        val nestedScrollConnection = remember {
            object : NestedScrollConnection {

                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y > 0) {
                        return super.onPreScroll(available, source)
                    }
                    val c = swipeState.performDrag(available.y)
                    return Offset(available.x, c)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (available.y < 0) {
                        return super.onPreScroll(available, source)
                    }
                    val c = swipeState.performDrag(available.y)
                    return Offset(available.x, c)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if(available.y > 0) {
                        return  super.onPreFling(available)
                    }
                    swipeState.performFling(available.y)
                    return available
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity
                ): Velocity {
                    if (available.y < 0) {
                        return  super.onPreFling(available)
                    }
                    swipeState.performFling(available.y)
                    return available
                }
            }
        }

        AnimatedVisibility(
            animationState,
            enter = slideIn { IntOffset(0, it.height) }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
                    .swipeable(
                        swipeState,
                        mapOf(
                            0f to SwipeState.Default,
                            600.dp.toPixels() to SwipeState.Dismiss
                        ),
                        orientation = Orientation.Vertical,
                        thresholds = { _, _ -> FractionalThreshold(0.5f) },
                    )
                    .offset { IntOffset(0, swipeState.offset.value.roundToInt()) },
                shape = MaterialTheme.shapes.large.copy(
                    bottomEnd = CornerSize(0f),
                    bottomStart = CornerSize(0f),
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {

                    SmallTopAppBar(
                        title = {
                            Text(
                                stringResource(R.string.preference_hidden_items),
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                maxLines = 1
                            )
                        },
                        actions = {
                            IconButton(onClick = { /*TODO*/ }) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = stringResource(
                                        R.string.settings
                                    )
                                )
                            }
                        }
                    )

                    val items by viewModel.hiddenItems.collectAsState(emptyList())
                    SearchResultGrid(
                        items,
                        modifier = Modifier
                            .weight(1f)
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }

    }

}

private enum class SwipeState {
    Default, Dismiss
}