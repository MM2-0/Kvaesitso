package de.mm20.launcher2.ui.launcher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.ClockWidget
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.widgets.picker.PickAppWidgetActivity
import de.mm20.launcher2.widgets.ExternalWidget
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun WidgetColumn(
    modifier: Modifier = Modifier,
    clockHeight: Dp = 0.dp,
    editMode: Boolean = false,
    onEditModeChange: (Boolean) -> Unit,
) {

    val viewModel: WidgetsVM = viewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val widgetHost = remember { AppWidgetHost(context.applicationContext, 44203) }

    LaunchedEffect(null) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            widgetHost.startListening()
            try {
                awaitCancellation()
            } finally {
                widgetHost.stopListening()
            }
        }
    }

    val pickWidgetLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {
        val data = it.data ?: return@rememberLauncherForActivityResult
        val widgetId = data.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return@rememberLauncherForActivityResult
        if (it.resultCode == Activity.RESULT_OK) {
            viewModel.addAppWidget(context, widgetId)
        }
    }

    Column(
        modifier = modifier
    ) {
        val scope = rememberCoroutineScope()
        var showAddDialog by remember { mutableStateOf(false) }

        AnimatedVisibility(!editMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(clockHeight),
                contentAlignment = Alignment.BottomCenter
            ) {
                ClockWidget(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        val widgets by viewModel.widgets.observeAsState(emptyList())
        Column {
            val swapThresholds = remember(widgets) {
                Array(widgets.size) { floatArrayOf(0f, 0f) }
            }
            for ((i, widget) in widgets.withIndex()) {
                key(if (widget is ExternalWidget) widget.widgetId else widget) {
                    var dragOffsetAfterSwap = remember<Float?> { null }
                    val offsetY = remember(widgets) { Animatable(dragOffsetAfterSwap ?: 0f) }

                    LaunchedEffect(widgets) {
                        dragOffsetAfterSwap = null
                    }

                    WidgetItem(
                        widget = widget,
                        appWidgetHost = widgetHost,
                        editMode = editMode,
                        onWidgetRemove = {
                            if (widget is ExternalWidget) {
                                widgetHost.deleteAppWidgetId(widget.widgetId)
                            }
                            viewModel.removeWidget(widget)
                        },
                        onWidgetResize = {
                            viewModel.setWidgetHeight(widget, it)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPlaced {
                                swapThresholds[i][0] = it.positionInParent().y
                                swapThresholds[i][1] = it.positionInParent().y + it.size.height
                            }
                            .padding(top = 8.dp)
                            .offset {
                                IntOffset(0, offsetY.value.toInt())
                            },
                        draggableState = rememberDraggableState {
                            scope.launch {
                                val newOffset = offsetY.value + it
                                offsetY.snapTo(newOffset)
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

        val icon =
            AnimatedImageVector.animatedVectorResource(R.drawable.anim_ic_edit_add)
        ExtendedFloatingActionButton(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.CenterHorizontally),
            icon = {
                Icon(
                    painter = rememberAnimatedVectorPainter(
                        animatedImageVector = icon,
                        atEnd = !editMode
                    ), contentDescription = null
                )
            },
            text = {
                Text(
                    stringResource(
                        if (editMode) R.string.widget_add_widget
                        else R.string.menu_edit_widgets
                    )
                )
            }, onClick = {
                if (!editMode) {
                    onEditModeChange(true)
                } else {
                    if (viewModel.getAvailableBuiltInWidgets().isEmpty()) {
                        pickWidgetLauncher.launch(
                            Intent(
                                context,
                                PickAppWidgetActivity::class.java
                            )
                        )
                    } else {
                        showAddDialog = true
                    }
                }
            })

        if (showAddDialog) {
            val availableBuiltInWidgets =
                remember { viewModel.getAvailableBuiltInWidgets() }
            Dialog(onDismissRequest = { showAddDialog = false }) {
                Surface(
                    tonalElevation = 16.dp,
                    shadowElevation = 16.dp,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.widget_add_widget),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(
                                start = 24.dp,
                                end = 24.dp,
                                top = 24.dp,
                                bottom = 8.dp
                            )
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = 16.dp
                                )
                        ) {
                            items(availableBuiltInWidgets) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addWidget(it)
                                            showAddDialog = false
                                        }
                                        .padding(
                                            horizontal = 24.dp,
                                            vertical = 16.dp
                                        )
                                ) {
                                    Text(
                                        text = it.loadLabel(LocalContext.current),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp)
                                        .clickable {
                                            pickWidgetLauncher.launch(
                                                Intent(
                                                    context,
                                                    PickAppWidgetActivity::class.java
                                                )
                                            )
                                            showAddDialog = false
                                        }
                                        .padding(
                                            horizontal = 24.dp,
                                            vertical = 16.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.widget_add_external),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }

                        TextButton(
                            onClick = { showAddDialog = false },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(bottom = 16.dp, end = 24.dp)
                        ) {
                            Text(
                                stringResource(android.R.string.cancel)
                            )
                        }
                    }
                }
            }
        }
    }
}