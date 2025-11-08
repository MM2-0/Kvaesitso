package de.mm20.launcher2.ui.settings.typography

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.themes.typography.Typography
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalNavController
import de.mm20.launcher2.ui.theme.typography.typographyOf
import kotlinx.serialization.Serializable

@Serializable
data object TypographiesSettingsRoute

@Composable
fun TypographiesSettingsScreen() {
    val viewModel: TypographySettingsScreenVM = viewModel()
    val navController = LocalNavController.current
    val context = LocalContext.current

    val selectedTheme by viewModel.selectedTypography.collectAsStateWithLifecycle(null)
    val themes by viewModel.typography.collectAsStateWithLifecycle(emptyList())

    var deleteTypography by remember { mutableStateOf<Typography?>(null) }

    val (builtin, user) = themes.partition { it.builtIn }


    PreferenceScreen(
        title = stringResource(R.string.preference_screen_typography),
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
                    val typo = typographyOf(theme)
                    Preference(
                        icon = {
                            Icon(
                                painterResource(
                                    if (theme.id == selectedTheme) R.drawable.radio_button_checked_24px else R.drawable.radio_button_unchecked_24px
                                ),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        title = {
                            Text(
                                text = theme.name,
                                maxLines = 1,
                                style = typo.titleMedium
                            )
                        },
                        controls = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TypographyPreview(typo)
                                IconButton(
                                    modifier = Modifier.padding(start = 12.dp),
                                    onClick = { showMenu = true }) {
                                    Icon(painterResource(R.drawable.more_vert_24px), null)
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(painterResource(R.drawable.content_copy_24px), null)
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
                            viewModel.selectTypography(theme)
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
                        val typo = typographyOf(theme)
                        Preference(
                            icon = {
                                Icon(
                                    painterResource(
                                        if (theme.id == selectedTheme) R.drawable.radio_button_checked_24px else R.drawable.radio_button_unchecked_24px
                                    ),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            title = {
                                Text(
                                    text = theme.name,
                                    maxLines = 1,
                                    style = typo.titleMedium
                                )
                            },
                            controls = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TypographyPreview(typo)
                                    IconButton(
                                        modifier = Modifier.padding(start = 12.dp),
                                        onClick = { showMenu = true }) {
                                        Icon(painterResource(R.drawable.more_vert_24px), null)
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(painterResource(R.drawable.edit_24px), null)
                                            },
                                            text = { Text(stringResource(R.string.edit)) },
                                            onClick = {
                                                navController?.navigate(
                                                    TypographySettingsRoute(theme.id.toString())
                                                )
                                                showMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(painterResource(R.drawable.content_copy_24px), null)
                                            },
                                            text = { Text(stringResource(R.string.duplicate)) },
                                            onClick = {
                                                viewModel.duplicate(theme)
                                                showMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(painterResource(R.drawable.delete_24px), null)
                                            },
                                            text = { Text(stringResource(R.string.menu_delete)) },
                                            onClick = {
                                                deleteTypography = theme
                                                showMenu = false
                                            }
                                        )
                                    }
                                }
                            },
                            onClick = {
                                viewModel.selectTypography(theme)
                            }
                        )
                    }
                }
            }
        }
    }
    if (deleteTypography != null) {
        AlertDialog(
            onDismissRequest = { deleteTypography = null },
            text = {
                Text(
                    stringResource(
                        R.string.confirmation_delete_transparencies_scheme,
                        deleteTypography!!.name
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(deleteTypography!!)
                        deleteTypography = null
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteTypography = null }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun TypographyPreview(typography: androidx.compose.material3.Typography) {
    val previewTexts = PreviewTexts()

    Column(
        modifier = Modifier.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = previewTexts.Short1,
            style = typography.titleSmall,
        )
        Text(
            text = previewTexts.Short2,
            style = typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}