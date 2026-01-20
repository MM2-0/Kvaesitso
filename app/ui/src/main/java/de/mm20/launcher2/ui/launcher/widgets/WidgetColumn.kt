package de.mm20.launcher2.ui.launcher.widgets

import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.LocalAppWidgetHost
import de.mm20.launcher2.ui.ktx.animateTo
import de.mm20.launcher2.ui.launcher.sheets.WidgetPickerSheet
import de.mm20.launcher2.ui.locals.LocalSnackbarHostState
import de.mm20.launcher2.widgets.AppWidget
import kotlinx.coroutines.launch

@Composable
fun WidgetColumn(
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    onEditModeChange: (Boolean) -> Unit,
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: WidgetsVM = viewModel()
    val snackbarHostState = LocalSnackbarHostState.current

    var addNewWidget by rememberSaveable { mutableStateOf(false) }


    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        val scope = rememberCoroutineScope()
        Column {
            val widgets by viewModel.widgets.collectAsState()
            val swapThresholds = remember(widgets) {
                Array(widgets.size) { floatArrayOf(0f, 0f) }
            }
            val widgetsWithIndex = remember(widgets) { widgets.withIndex() }
            for ((i, widget) in widgetsWithIndex) {
                key(widget.id) {
                    var dragOffsetAfterSwap = remember<Float?> { null }
                    val offsetY = remember(widgets) { mutableStateOf(dragOffsetAfterSwap ?: 0f) }

                    LaunchedEffect(widgets) {
                        dragOffsetAfterSwap = null
                    }

                    val widgetHost = LocalAppWidgetHost.current

                    WidgetItem(
                        widget = widget,
                        editMode = editMode,
                        onWidgetAdd = { widget, offset ->
                            viewModel.addWidget(widget, i + offset)
                        },
                        onWidgetRemove = {
                            lifecycleOwner.lifecycleScope.launch {
                                viewModel.removeWidget(widget)
                                val result = snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.widget_removed),
                                    actionLabel = context.getString(R.string.action_undo),
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.addWidget(widget, i)
                                } else {
                                    if (widget is AppWidget) {
                                        widgetHost.deleteAppWidgetId(widget.config.widgetId)
                                    }
                                }
                            }
                        },
                        onWidgetUpdate = {
                            viewModel.updateWidget(it)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPlaced {
                                swapThresholds[i][0] = it.positionInParent().y
                                swapThresholds[i][1] = it.positionInParent().y + it.size.height
                            }
                            .padding(top = if (i > 0) 8.dp else 0.dp)
                            .offset {
                                IntOffset(0, offsetY.value.toInt())
                            },
                        draggableState = rememberDraggableState {
                            scope.launch {
                                val newOffset = offsetY.value + it
                                offsetY.value = newOffset
                                if (i > 0 && newOffset < (swapThresholds[i - 1][0] - swapThresholds[i - 1][1])) {
                                    if (dragOffsetAfterSwap == null) {
                                        dragOffsetAfterSwap =
                                            swapThresholds[i - 1][1] - swapThresholds[i - 1][0] + newOffset
                                        viewModel.moveUp(i)
                                    }
                                }
                                if (i < widgets.lastIndex && newOffset > (swapThresholds[i + 1][1] - swapThresholds[i + 1][0])) {
                                    if (dragOffsetAfterSwap == null) {
                                        dragOffsetAfterSwap =
                                            swapThresholds[i + 1][0] - swapThresholds[i + 1][1] + newOffset
                                        viewModel.moveDown(i)
                                    }
                                }
                            }
                        },
                        onDragStopped = {
                            scope.launch {
                                offsetY.animateTo(0f)
                            }
                        }
                    )
                }
            }
        }

        val editButton by viewModel.editButton.collectAsState()
        if (editMode || editButton == true) {
            val title = stringResource(
                if (editMode) R.string.widget_add_widget
                else R.string.menu_edit_widgets
            )

            Button(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                onClick = {
                    if (!editMode) {
                        onEditModeChange(true)
                    } else {
                        addNewWidget = true
                    }
                }
            ) {
                Icon(
                    modifier = Modifier
                        .padding(end = ButtonDefaults.IconSpacing)
                        .size(ButtonDefaults.IconSize),
                    painter = painterResource(
                        if (editMode) R.drawable.add_20px else R.drawable.edit_20px
                    ), contentDescription = null
                )
                Text(title)
            }

        }
    }

    WidgetPickerSheet(
        expanded = addNewWidget,
        onDismiss = { addNewWidget = false },
        onWidgetSelected = {
            viewModel.addWidget(it)
            addNewWidget = false
        }
    )
}