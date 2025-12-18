package de.mm20.launcher2.ui.settings.colorscheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.themes.colors.Colors
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.theme.colorscheme.darkColorSchemeOf
import de.mm20.launcher2.ui.theme.colorscheme.lightColorSchemeOf
import kotlinx.serialization.Serializable

@Serializable
data object ColorSchemesSettingsRoute : NavKey

@Composable
fun ColorSchemesSettingsScreen() {
    val viewModel: ColorSchemesSettingsScreenVM = viewModel()
    val backStack = LocalBackStack.current
    val context = LocalContext.current

    val selectedTheme by viewModel.selectedColors.collectAsStateWithLifecycle(null)
    val themes by viewModel.colors.collectAsStateWithLifecycle(emptyList())

    var deleteColors by remember { mutableStateOf<Colors?>(null) }

    val (builtin, user) = themes.partition { it.builtIn }

    PreferenceScreen(
        title = stringResource(R.string.preference_screen_colors),
        topBarActions = {
            IconButton(onClick = { viewModel.createNew(context) }) {
                Icon(painterResource(R.drawable.add_24px), null)
            }
        },
    ) {
        item {
            PreferenceCategory {
                for (theme in builtin) {
                    var showMenu by remember { mutableStateOf(false) }
                    Preference(
                        icon = if (theme.id == selectedTheme) R.drawable.radio_button_checked_24px else R.drawable.radio_button_unchecked_24px,
                        title = theme.name,
                        controls = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ColorSchemePreview(theme)
                                IconButton(
                                    modifier = Modifier.padding(start = 12.dp),
                                    onClick = { showMenu = true }) {
                                    Icon(painterResource(R.drawable.more_vert_24px), null)
                                }
                                DropdownMenuPopup(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuGroup(
                                        shapes = MenuDefaults.groupShapes(),
                                    ) {
                                        DropdownMenuItem(
                                            shape = MenuDefaults.standaloneItemShape,
                                            leadingIcon = {
                                                Icon(
                                                    painterResource(R.drawable.content_copy_24px),
                                                    null
                                                )
                                            },
                                            text = { Text(stringResource(R.string.duplicate)) },
                                            onClick = {
                                                viewModel.duplicate(theme)
                                                showMenu = false
                                            }
                                        )
                                    }

                                }
                            }
                        },
                        onClick = {
                            viewModel.selectTheme(theme)
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
                            icon = if (theme.id == selectedTheme) R.drawable.radio_button_checked_24px else R.drawable.radio_button_unchecked_24px,
                            title = theme.name,
                            controls = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ColorSchemePreview(theme)
                                    IconButton(
                                        modifier = Modifier.padding(start = 12.dp),
                                        onClick = { showMenu = true }) {
                                        Icon(painterResource(R.drawable.more_vert_24px), null)
                                    }
                                    DropdownMenuPopup(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuGroup(
                                            shapes = MenuDefaults.groupShapes(),
                                        ) {
                                            DropdownMenuItem(
                                                shape = MenuDefaults.leadingItemShape,
                                                leadingIcon = {
                                                    Icon(
                                                        painterResource(R.drawable.edit_24px),
                                                        null
                                                    )
                                                },
                                                text = { Text(stringResource(R.string.edit)) },
                                                onClick = {
                                                    backStack.add(ColorSchemeSettingsRoute(id = theme.id))
                                                    showMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                shape = MenuDefaults.middleItemShape,
                                                leadingIcon = {
                                                    Icon(
                                                        painterResource(R.drawable.content_copy_24px),
                                                        null
                                                    )
                                                },
                                                text = { Text(stringResource(R.string.duplicate)) },
                                                onClick = {
                                                    viewModel.duplicate(theme)
                                                    showMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                shape = MenuDefaults.trailingItemShape,
                                                leadingIcon = {
                                                    Icon(
                                                        painterResource(R.drawable.delete_24px),
                                                        null
                                                    )
                                                },
                                                text = { Text(stringResource(R.string.menu_delete)) },
                                                onClick = {
                                                    deleteColors = theme
                                                    showMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            },
                            onClick = {
                                viewModel.selectTheme(theme)
                            }
                        )
                    }
                }
            }
        }
    }
    if (deleteColors != null) {
        AlertDialog(
            onDismissRequest = { deleteColors = null },
            text = {
                Text(
                    stringResource(
                        R.string.confirmation_delete_color_scheme,
                        deleteColors!!.name
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(deleteColors!!)
                        deleteColors = null
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteColors = null }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun ColorSchemePreview(colors: Colors) {
    val dark = LocalDarkTheme.current
    val scheme = if (dark) darkColorSchemeOf(colors) else lightColorSchemeOf(colors)
    Box(
        modifier = Modifier
            .height(28.dp)
            .width(72.dp)
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.small)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(scheme.surface)
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(scheme.surfaceVariant)
            )
        }

        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .size(16.dp)
                    .background(scheme.primary)
            )
            Box(
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .size(16.dp)
                    .background(scheme.secondary)
            )
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .size(16.dp)
                    .background(scheme.tertiary)
            )
        }
    }
}