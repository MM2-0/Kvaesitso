package de.mm20.launcher2.ui.settings.transparencies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.themes.transparencies.Transparencies
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.theme.WallpaperColors
import de.mm20.launcher2.ui.theme.transparency.transparencySchemeOf
import de.mm20.launcher2.ui.theme.wallpaperColorsAsState
import kotlinx.serialization.Serializable

@Serializable
data object TransparencySchemesSettingsRoute : NavKey

@Composable
fun TransparencySchemesSettingsScreen() {
    val viewModel: TransparencySchemesSettingsScreenVM = viewModel()
    val backStack = LocalBackStack.current
    val context = LocalContext.current

    val selectedTheme by viewModel.selectedTransparencies.collectAsStateWithLifecycle(null)
    val themes by viewModel.transparencies.collectAsStateWithLifecycle(emptyList())

    var deleteTransparencies by remember { mutableStateOf<Transparencies?>(null) }

    val (builtin, user) = themes.partition { it.builtIn }

    val wallpaperColors = wallpaperColorsAsState().value

    PreferenceScreen(
        title = stringResource(R.string.preference_screen_transparencies),
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
                                TransparenciesPreview(wallpaperColors, theme)
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
                            viewModel.selectTransparencies(theme)
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
                                    TransparenciesPreview(wallpaperColors, theme)
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
                                                    backStack.add(
                                                        TransparencySchemeSettingsRoute(
                                                            theme.id
                                                        )
                                                    )
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
                                                    deleteTransparencies = theme
                                                    showMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            },
                            onClick = {
                                viewModel.selectTransparencies(theme)
                            }
                        )
                    }
                }
            }
        }
    }
    if (deleteTransparencies != null) {
        AlertDialog(
            onDismissRequest = { deleteTransparencies = null },
            text = {
                Text(
                    stringResource(
                        R.string.confirmation_delete_transparencies_scheme,
                        deleteTransparencies!!.name
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(deleteTransparencies!!)
                        deleteTransparencies = null
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteTransparencies = null }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun TransparenciesPreview(wallpaperColors: WallpaperColors, theme: Transparencies) {
    val transparencies = transparencySchemeOf(theme)

    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .checkerboard(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.onPrimaryContainer,
                12.dp,
            )
            .height(40.dp)
            .width(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = transparencies.background),
                    MaterialTheme.shapes.extraSmall
                )
                .height(40.dp)
                .width(56.dp)
        )
        Box(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = transparencies.surface),
                    MaterialTheme.shapes.extraSmall
                )
                .height(24.dp)
                .width(48.dp)
        )
        Box(
            modifier = Modifier
                .height(32.dp)
                .width(36.dp)
                .shadow(
                    if (transparencies.elevatedSurface < 1f) 0.dp else 8.dp,
                    shape = MaterialTheme.shapes.extraSmall,
                    clip = true,
                )
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp))
        )
    }
}