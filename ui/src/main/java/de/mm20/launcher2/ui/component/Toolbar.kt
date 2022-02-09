package de.mm20.launcher2.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.unit.dp
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
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.animateContentSize()
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
                        text = {
                            Text(
                                action.label, modifier = Modifier.weight(1f),
                            )
                        }
                    )
                }
                is DefaultToolbarAction -> {
                    DropdownMenuItem(
                        onClick = {
                            action.action()
                            onDismiss()
                        },
                        text = {
                            Text(
                                action.label,
                            )
                        }
                    )
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