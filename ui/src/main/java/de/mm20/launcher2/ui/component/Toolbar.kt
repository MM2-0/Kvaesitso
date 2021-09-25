package de.mm20.launcher2.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.favorites.FavoritesViewModel
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import kotlin.math.min

@Composable
fun Toolbar(
    modifier: Modifier = Modifier,
    leftActions: List<ToolbarAction> = emptyList(),
    rightActions: List<ToolbarAction> = emptyList()
) {
    val slots = integerResource(R.integer.config_toolbarSlots)
    Row(
        modifier = modifier
            .padding(4.dp)
    ) {
        Icons(leftActions, slots)
        Spacer(modifier = Modifier.weight(1f, fill = true))
        Icons(rightActions, slots)
    }
}

@Composable
fun Icons(actions: List<ToolbarAction>, slots: Int) {
    for (i in 0 until min(slots, actions.size)) {
        if (i == slots - 1 && slots != actions.size) {
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    OverflowMenuItems(items = actions.subList(slots - 1, actions.size)) {
                        showMenu = false
                    }
                }
            }
        } else {
            val action = actions[i]
            when (action) {
                is DefaultToolbarAction -> {
                    IconButton(action.action) {
                        Icon(action.icon, contentDescription = action.label)
                    }
                }
                is ToggleToolbarAction -> {
                    IconToggleButton(action.isChecked, action.onCheckedChange) {
                        Icon(action.icon, contentDescription = action.label)
                    }
                }
                is SubmenuToolbarAction -> {
                    Box {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton({
                            showMenu = true
                        }) {
                            Icon(action.icon, contentDescription = action.label)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.animateContentSize()
                        ) {
                            OverflowMenuItems(items = action.children) {
                                showMenu = false
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnScope.OverflowMenuItems(items: List<ToolbarAction>, onDismiss: () -> Unit) {
    var selectedSubMenu by remember { mutableStateOf(-1) }
    if (selectedSubMenu == -1) {
        items.forEachIndexed { i, action ->
            when (action) {
                is SubmenuToolbarAction -> {
                    DropdownMenuItem(
                        onClick = { selectedSubMenu = i },
                    ) {
                        Text(
                            action.label, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.subtitle2
                        )
                        Icon(imageVector = Icons.Rounded.ArrowRight, contentDescription = null)
                    }
                }
                is ToggleToolbarAction -> {
                    DropdownMenuItem(
                        onClick = { action.onCheckedChange(!action.isChecked) },
                    ) {
                        Text(
                            action.label,
                            style = MaterialTheme.typography.subtitle2
                        )
                    }
                }
                is DefaultToolbarAction -> {
                    DropdownMenuItem(onClick = {
                        action.action
                        onDismiss()
                    }) {
                        Text(
                            action.label,
                            style = MaterialTheme.typography.subtitle2
                        )
                    }
                }
            }
        }
    } else {
        val submenu = items[selectedSubMenu] as SubmenuToolbarAction
        OverflowMenuItems(items = submenu.children, onDismiss)
    }
}

interface ToolbarAction {
    val label: String
    val icon: ImageVector
}

data class DefaultToolbarAction(
    override val label: String,
    override val icon: ImageVector,
    val action: () -> Unit
) : ToolbarAction

data class SubmenuToolbarAction(
    override val label: String,
    override val icon: ImageVector,
    val children: List<ToolbarAction>
) : ToolbarAction

data class ToggleToolbarAction(
    override val label: String,
    override val icon: ImageVector,
    val isChecked: Boolean,
    val onCheckedChange: (Boolean) -> Unit
) : ToolbarAction

@Composable
fun favoritesToolbarAction(item: Searchable): ToggleToolbarAction {
    val viewModel = viewModel<FavoritesViewModel>()
    val isPinned by viewModel.isPinned(item).observeAsState(false)

    return ToggleToolbarAction(
        label = stringResource(
            if (isPinned) R.string.favorites_menu_unpin else R.string.favorites_menu_pin
        ),
        icon = if (isPinned) Icons.Rounded.Star else Icons.Rounded.StarBorder,
        isChecked = isPinned,
        onCheckedChange = {
            if (it) {
                viewModel.pinItem(item)
            } else {
                viewModel.unpinItem(item)
            }
        }
    )
}

@Composable
fun hideToolbarAction(item: Searchable): ToggleToolbarAction {
    val viewModel = viewModel<FavoritesViewModel>()
    val isHidden by viewModel.isHidden(item).observeAsState(false)

    return ToggleToolbarAction(
        label = stringResource(
            if (isHidden) R.string.menu_unhide else R.string.menu_hide
        ),
        icon = if (isHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
        isChecked = isHidden,
        onCheckedChange = {
            if (it) {
                viewModel.hideItem(item)
            } else {
                viewModel.unhideItem(item)
            }
        }
    )
}