package de.mm20.launcher2.ui.launcher.scaffold

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.widgets.WidgetColumn
import kotlinx.coroutines.launch

internal object WidgetsComponent : ScaffoldComponent {
    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        var editMode by rememberSaveable { mutableStateOf(false) }
        val topPadding by animateDpAsState(if (editMode) 8.dp else 0.dp)

        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
                .padding(top = topPadding)
                .padding(insets),
        ) {
            WidgetColumn(
                modifier = Modifier,
                editMode = editMode,
                onEditModeChange = {
                    if (it) state.lock() else state.unlock()
                    editMode = it
                },
            )
        }
        if (editMode) {
            BackHandler {
                editMode = false
                state.unlock()
            }
        }
        AnimatedVisibility(
            editMode,
            modifier = Modifier.zIndex(10f),
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.menu_edit_widgets)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            editMode = false
                            state.unlock()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_done))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    }
}

fun Modifier.betterVerticalScroll(
    state: ScrollState,
): Modifier = composed {
    val flingBehavior = ScrollableDefaults.flingBehavior()
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {

        }
    }
    val nestedScrollDispatcher = remember { NestedScrollDispatcher() }

    val draggableState = rememberDraggable2DState {
        var available = it
        var consumed = Offset.Zero
        nestedScrollDispatcher.dispatchPreScroll(available, NestedScrollSource.UserInput).also {
            consumed += it
            available -= it
        }
        state.dispatchRawDelta(-available.y).also {
            available -= Offset(0f, -it)
            consumed += Offset(0f, -it)
        }
        nestedScrollDispatcher.dispatchPostScroll(consumed, available, NestedScrollSource.UserInput)
    }

    val scope = rememberCoroutineScope()



    return@composed Modifier
        .nestedScroll(nestedScrollConnection, nestedScrollDispatcher)
        .draggable2D(
            draggableState,
            onDragStopped = {
                scope.launch {
                    state.scroll {
                        var available = it
                        var consumed = Velocity.Zero
                        nestedScrollDispatcher.dispatchPreFling(available).also {
                            consumed += it
                            available -= it
                        }
                        with(flingBehavior) {
                            performFling(-available.y).also {
                                consumed += Velocity(0f, -it)
                                available -= Velocity(0f, -it)
                            }
                        }
                        nestedScrollDispatcher.dispatchPostFling(consumed, available)
                    }
                }
            }
        )
        .verticalScroll(state, enabled = false)
}