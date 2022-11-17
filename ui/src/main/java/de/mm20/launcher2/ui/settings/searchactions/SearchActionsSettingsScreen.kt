package de.mm20.launcher2.ui.settings.searchactions

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.searchactions.builders.CustomizableSearchActionBuilder
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.SearchActionIcon
import de.mm20.launcher2.ui.component.getSearchActionIconVector
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.launcher.helper.DraggableItem
import de.mm20.launcher2.ui.launcher.helper.LazyDragAndDropColumn
import de.mm20.launcher2.ui.launcher.helper.rememberLazyDragAndDropListState
import de.mm20.launcher2.ui.locals.LocalNavController

@Composable
fun SearchActionsSettingsScreen() {
    val viewModel: SearchActionsSettingsScreenVM = viewModel()
    val navController = LocalNavController.current
    val systemUiController = rememberSystemUiController()
    systemUiController.setStatusBarColor(MaterialTheme.colorScheme.surface)
    systemUiController.setNavigationBarColor(Color.Black)

    val context = LocalContext.current

    val colorScheme = MaterialTheme.colorScheme

    val activity = LocalContext.current as? AppCompatActivity

    val listState = rememberLazyDragAndDropListState(
        onDragStart = {
            it.key != "divider" && !(it.key as String).startsWith("disabled-")
        },
        onItemMove = { from, to -> viewModel.moveItem(from.index, to.index) }
    )

    val searchActions by viewModel.searchActions.observeAsState(emptyList())
    val disabledActions by viewModel.disabledActions.observeAsState(emptyList())

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.createAction() }) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.preference_screen_search_actions),
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (navController?.navigateUp() != true) {
                            activity?.onBackPressed()
                        }
                    }) {
                        Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }) {

        LazyDragAndDropColumn(
            state = listState,
            bidirectionalDrag = false,
            contentPadding = it,
            modifier = Modifier
                .fillMaxSize()
        ) {
            items(
                items = searchActions,
                key = { it.key }
            ) { item ->
                DraggableItem(
                    state = listState,
                    key = item.key
                ) {
                    val elevation by animateDpAsState(if (it) 4.dp else 0.dp)
                    Surface(
                        shadowElevation = elevation,
                        tonalElevation = elevation,
                        modifier = Modifier.zIndex(if (it) 1f else 0f)
                    ) {
                        if (item is CustomizableSearchActionBuilder) {
                            Preference(
                                icon = {
                                    SearchActionIcon(item)
                                },
                                title = item.label,
                                onClick = {
                                    viewModel.editAction(item)
                                }
                            )
                        } else {
                            SwitchPreference(
                                icon = getSearchActionIconVector(item.icon),
                                title = item.label,
                                value = true,
                                onValueChanged = {
                                    viewModel.removeAction(item)
                                }
                            )
                        }
                    }
                }
            }
            item(key = "divider") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                )
            }
            items(
                items = disabledActions,
                key = { "disabled-${it.key}" }
            ) { item ->
                SwitchPreference(
                    icon = getSearchActionIconVector(item.icon),
                    title = item.label,
                    value = false,
                    onValueChanged = {
                        viewModel.addAction(item)
                    }
                )
            }
        }
    }

    val editAction by viewModel.showEditDialogFor.observeAsState(null)
    val createAction by viewModel.showCreateDialog.observeAsState(false)

    if (createAction) {
        EditSearchActionSheet(
            initialSearchAction = null,
            onSave = {
                viewModel.addAction(it)
            },
            onDismiss = {
                viewModel.dismissDialogs()
            }
        )
    }
    if (editAction != null) {
        EditSearchActionSheet(
            initialSearchAction = editAction,
            onSave = {
                viewModel.updateAction(editAction!!, it)
            },
            onDismiss = {
                viewModel.dismissDialogs()
            },
            onDelete = {
                viewModel.removeAction(editAction!!)
            }
        )
    }
}