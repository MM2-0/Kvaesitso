package de.mm20.launcher2.ui.settings.searchactions

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.searchactions.builders.CustomizableSearchActionBuilder
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.SearchActionIcon
import de.mm20.launcher2.ui.component.dragndrop.DraggableItem
import de.mm20.launcher2.ui.component.dragndrop.LazyDragAndDropColumn
import de.mm20.launcher2.ui.component.dragndrop.rememberLazyDragAndDropListState
import de.mm20.launcher2.ui.component.getSearchActionIconVector
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.ktx.animateShapeAsState
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
data object SearchActionsSettingsRoute : NavKey

@Composable
fun SearchActionsSettingsScreen() {
    val viewModel: SearchActionsSettingsScreenVM = viewModel()
    val backStack = LocalBackStack.current
    val systemUiController = rememberSystemUiController()
    systemUiController.setStatusBarColor(MaterialTheme.colorScheme.surface)
    systemUiController.setNavigationBarColor(Color.Black)

    val context = LocalContext.current

    val colorScheme = MaterialTheme.colorScheme

    val activity = LocalActivity.current as? AppCompatActivity

    val listState = rememberLazyDragAndDropListState(
        onDragStart = {
            it.key != "divider" && !(it.key as String).startsWith("disabled-")
        },
        onItemMove = { from, to -> viewModel.moveItem(from.index - 1, to.index - 1) }
    )

    val searchActions by viewModel.searchActions.collectAsState()
    val disabledActions by viewModel.disabledActions.collectAsState()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        if (backStack.size <= 1) {
                            activity?.onBackPressed()
                        } else {
                            backStack.removeLastOrNull()
                        }
                    }) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        CustomTabsIntent.Builder()
                            .setDefaultColorSchemeParams(
                                CustomTabColorSchemeParams.Builder()
                                    .setToolbarColor(colorScheme.primaryContainer.toArgb())
                                    .setSecondaryToolbarColor(colorScheme.secondaryContainer.toArgb())
                                    .build()
                            )
                            .build().launchUrl(
                                context,
                                Uri.parse("https://kvaesitso.mm20.de/docs/user-guide/search/quickactions")
                            )
                    }) {
                        Icon(
                            painterResource(R.drawable.help_24px),
                            contentDescription = "Help"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {

        LazyDragAndDropColumn(
            state = listState,
            bidirectionalDrag = false,
            contentPadding = it,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(
                key = "disabled-info"
            ) {
                Row(
                    modifier = Modifier
                        .padding(start = 28.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.info_20px), null,
                        modifier = Modifier
                            .padding(end = 24.dp)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        modifier = Modifier,
                        text = stringResource(R.string.hint_drag_and_drop_reorder),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            itemsIndexed(
                items = searchActions,
                key = { _, it -> it.key }
            ) { index, item ->
                DraggableItem(
                    state = listState,
                    key = item.key
                ) {

                    val shape = getShape(index, searchActions.size)
                    val draggedShape = MaterialTheme.shapes.medium

                    val elevation by animateDpAsState(if (it) 4.dp else 0.dp)
                    Surface(
                        shadowElevation = elevation,
                        tonalElevation = elevation,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .zIndex(if (it) 1f else 0f),
                        shape = animateShapeAsState(
                            if (it) draggedShape else shape
                        ).value
                    ) {
                        if (item is CustomizableSearchActionBuilder) {
                            Preference(
                                icon = {
                                    SearchActionIcon(item)
                                },
                                title = item.label,
                                onClick = {
                                    viewModel.editAction(item)
                                },
                                controls = {
                                    var showDropdown by remember { mutableStateOf(false) }
                                    IconButton(
                                        onClick = {
                                            showDropdown = true
                                        }
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.more_vert_24px),
                                            stringResource(R.string.edit)
                                        )
                                        DropdownMenuPopup(
                                            expanded = showDropdown,
                                            onDismissRequest = { showDropdown = false }
                                        ) {
                                            DropdownMenuGroup(
                                                shapes = MenuDefaults.groupShapes()
                                            ) {
                                                DropdownMenuItem(
                                                    shape = MenuDefaults.leadingItemShape,
                                                    leadingIcon = {
                                                        Icon(
                                                            painterResource(R.drawable.edit_24px),
                                                            contentDescription = null
                                                        )
                                                    },
                                                    text = {
                                                        Text(stringResource(R.string.edit))
                                                    },
                                                    onClick = {
                                                        viewModel.editAction(item)
                                                        showDropdown = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    shape = MenuDefaults.trailingItemShape,
                                                    leadingIcon = {
                                                        Icon(
                                                            painterResource(R.drawable.delete_24px),
                                                            contentDescription = null
                                                        )
                                                    },
                                                    text = {
                                                        Text(stringResource(R.string.menu_delete))
                                                    },
                                                    onClick = {
                                                        viewModel.removeAction(item)
                                                        showDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
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
                        .height(8.dp)
                )
            }
            itemsIndexed(
                items = disabledActions,
                key = { _, it -> "disabled-${it.key}" }
            ) { index, item ->
                val shape = getShape(index, disabledActions.size)

                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .clip(shape)
                ) {
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
            item(key = "disabled-button") {
                FilledTonalButton(
                    modifier = Modifier
                        .padding(top = 10.dp, start = 12.dp, end = 12.dp)
                        .navigationBarsPadding(),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    onClick = {
                        viewModel.createAction()
                    }) {
                    Icon(
                        painterResource(R.drawable.add_20px),
                        null,
                        modifier = Modifier
                            .padding(end = ButtonDefaults.IconSpacing)
                            .size(ButtonDefaults.IconSize)
                    )
                    Text(stringResource(R.string.create_search_action_title))
                }
            }
        }
    }

    val editAction by viewModel.showEditDialogFor
    val createAction by viewModel.showCreateDialog

    EditSearchActionSheet(
        expanded = createAction,
        initialSearchAction = null,
        onSave = {
            viewModel.addAction(it)
        },
        onDismiss = {
            viewModel.dismissDialogs()
        }
    )
    EditSearchActionSheet(
        expanded = editAction != null,
        initialSearchAction = editAction,
        onSave = {
            viewModel.updateAction(editAction!!, it)
        },
        onDismiss = {
            viewModel.dismissDialogs()
        }
    )
}

@Composable
private fun getShape(index: Int, total: Int): Shape {
    if (total == 1) {
        return MaterialTheme.shapes.medium
    }

    if (total > 1 && index > 0 && index < total - 1) {
        return MaterialTheme.shapes.extraSmall
    }

    val xs = MaterialTheme.shapes.extraSmall
    val md = MaterialTheme.shapes.medium

    if (index == 0) {
        return xs.copy(
            topStart = md.topStart,
            topEnd = md.topEnd
        )
    } else {
        return xs.copy(
            bottomStart = md.bottomStart,
            bottomEnd = md.bottomEnd
        )
    }
}