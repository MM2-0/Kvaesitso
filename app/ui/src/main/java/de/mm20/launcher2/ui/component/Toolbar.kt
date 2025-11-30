package de.mm20.launcher2.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.ktx.toDp
import de.mm20.launcher2.ui.locals.LocalWindowPosition
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
                    Icon(
                        painterResource(R.drawable.more_vert_24px),
                        contentDescription = stringResource(R.string.action_more_actions)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                ) {
                    DropdownMenuPopup(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.animateContentSize()
                    ) {
                        DropdownMenuGroup(
                            shapes = MenuDefaults.groupShapes()
                        ) {
                            OverflowMenuItems(items = actions.subList(slots - 1, actions.size)) {
                                showMenu = false
                            }
                        }
                    }
                }
            }
        } else {
            val action = actions[i]
            Tooltip(action.label) {
                when (action) {
                    is DefaultToolbarAction -> {
                        IconButton(
                            onClick = action.action,
                        ) {
                            Icon(painterResource(action.icon), contentDescription = action.label)
                        }
                    }

                    is SubmenuToolbarAction -> {
                        Box {
                            var showMenu by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = {
                                    showMenu = true
                                },
                            ) {
                                Icon(
                                    painterResource(action.icon),
                                    contentDescription = action.label
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .offset(0.dp, LocalWindowPosition.current.toDp())
                            ) {
                                DropdownMenuPopup(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    modifier = Modifier.animateContentSize()
                                ) {
                                    DropdownMenuGroup(
                                        shapes = MenuDefaults.groupShapes()
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
                        },
                        leadingIcon = {
                            Icon(painterResource(action.icon), null)
                        },
                        trailingIcon = {
                            Icon(painterResource(R.drawable.arrow_right_24px), null)
                        },
                        shape = when {
                            i == 0 -> MenuDefaults.leadingItemShape
                            i == items.lastIndex -> MenuDefaults.trailingItemShape
                            else -> MenuDefaults.middleItemShape
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
                        },
                        leadingIcon = {
                            Icon(painterResource(action.icon), null)
                        },
                        shape = when {
                            i == 0 -> MenuDefaults.leadingItemShape
                            i == items.lastIndex -> MenuDefaults.trailingItemShape
                            else -> MenuDefaults.middleItemShape
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
    val icon: Int
}

data class DefaultToolbarAction(
    override val label: String,
    override val icon: Int,
    val action: () -> Unit
) : ToolbarAction

data class SubmenuToolbarAction(
    override val label: String,
    override val icon: Int,
    val children: List<ToolbarAction>
) : ToolbarAction