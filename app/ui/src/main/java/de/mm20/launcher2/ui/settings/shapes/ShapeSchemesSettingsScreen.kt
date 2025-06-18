package de.mm20.launcher2.ui.settings.shapes

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.themes.shapes.CornerStyle
import de.mm20.launcher2.themes.shapes.Shapes
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalNavController

@Composable
fun ShapeSchemesSettingsScreen() {
    val viewModel: ShapeSchemesSettingsScreenVM = viewModel()
    val navController = LocalNavController.current
    val context = LocalContext.current

    val selectedTheme by viewModel.selectedShapesId.collectAsStateWithLifecycle(null)
    val themes by viewModel.shapes.collectAsStateWithLifecycle(emptyList())

    var deleteShapes by remember { mutableStateOf<Shapes?>(null) }

    val (builtin, user) = themes.partition { it.builtIn }

    PreferenceScreen(
        title = stringResource(R.string.preference_screen_shapes),
        topBarActions = {
            IconButton(onClick = { viewModel.createNew(context) }) {
                Icon(Icons.Rounded.Add, null)
            }
        },
    ) {
        item {
            PreferenceCategory {
                for (theme in builtin) {
                    var showMenu by remember { mutableStateOf(false) }
                    Preference(
                        icon = if (theme.id == selectedTheme) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                        title = theme.name,
                        controls = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ShapesPreview(theme)
                                IconButton(
                                    modifier = Modifier.padding(start = 12.dp),
                                    onClick = { showMenu = true }) {
                                    Icon(Icons.Rounded.MoreVert, null)
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(Icons.Rounded.ContentCopy, null)
                                        },
                                        text = { Text(stringResource(R.string.duplicate)) },
                                        onClick = {
                                            viewModel.duplicate(theme)
                                            showMenu = false
                                        }
                                    )
                                }
                            }
                        },
                        onClick = {
                            viewModel.selectShapes(theme)
                        }
                    )
                }
            }
        }
        if (user.isNotEmpty()) {
            item {
                PreferenceCategory {
                    for (theme in user) {
                        var showMenu by remember { mutableStateOf(false) }
                        Preference(
                            icon = if (theme.id == selectedTheme) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                            title = theme.name,
                            controls = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ShapesPreview(theme)
                                    IconButton(
                                        modifier = Modifier.padding(start = 12.dp),
                                        onClick = { showMenu = true }) {
                                        Icon(Icons.Rounded.MoreVert, null)
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(Icons.Rounded.Edit, null)
                                            },
                                            text = { Text(stringResource(R.string.edit)) },
                                            onClick = {
                                                navController?.navigate("settings/appearance/shapes/${theme.id}")
                                                showMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(Icons.Rounded.ContentCopy, null)
                                            },
                                            text = { Text(stringResource(R.string.duplicate)) },
                                            onClick = {
                                                viewModel.duplicate(theme)
                                                showMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(Icons.Rounded.Delete, null)
                                            },
                                            text = { Text(stringResource(R.string.menu_delete)) },
                                            onClick = {
                                                deleteShapes = theme
                                                showMenu = false
                                            }
                                        )
                                    }
                                }
                            },
                            onClick = {
                                viewModel.selectShapes(theme)
                            }
                        )
                    }
                }
            }
        }
    }
    if (deleteShapes != null) {
        AlertDialog(
            onDismissRequest = { deleteShapes = null },
            text = {
                Text(
                    stringResource(
                        R.string.confirmation_delete_shapes_scheme,
                        deleteShapes!!.name
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(deleteShapes!!)
                        deleteShapes = null
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteShapes = null }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ShapesPreview(theme: Shapes) {
    val shape = theme.medium
    val baseShape = theme.baseShape

    val topStart =
        (shape?.radii?.get(0)?.toFloat() ?: baseShape.radii?.get(0)?.toFloat() ?: 8f) / 3f * 2f
    val topEnd =
        (shape?.radii?.get(1)?.toFloat() ?: baseShape.radii?.get(1)?.toFloat() ?: 8f) / 3f * 2f
    val bottomEnd =
        (shape?.radii?.get(2)?.toFloat() ?: baseShape.radii?.get(2)?.toFloat() ?: 8f) / 3f * 2f
    val bottomStart =
        (shape?.radii?.get(3)?.toFloat() ?: baseShape.radii?.get(3)?.toFloat() ?: 8f) / 3f * 2f
    Box(
        modifier = Modifier
            .size(32.dp)
            .border(
                2.dp,
                MaterialTheme.colorScheme.primary,
                if ((theme.medium?.corners ?: theme.baseShape.corners
                    ?: CornerStyle.Rounded) == CornerStyle.Cut
                ) {
                    CutCornerShape(
                        topStart = topStart.dp,
                        topEnd = topEnd.dp,
                        bottomEnd = bottomEnd.dp,
                        bottomStart = bottomStart.dp
                    )
                } else {
                    RoundedCornerShape(
                        topStart = topStart.dp,
                        topEnd = topEnd.dp,
                        bottomEnd = bottomEnd.dp,
                        bottomStart = bottomStart.dp
                    )
                }
            )
    )

}