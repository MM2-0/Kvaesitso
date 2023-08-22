package de.mm20.launcher2.ui.settings.colorscheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalNavController
import de.mm20.launcher2.ui.theme.colorscheme.systemCorePalette

@Composable
fun ThemesSettingsScreen() {
    val viewModel: ThemesSettingsScreenVM = viewModel()
    val navController = LocalNavController.current

    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle(null)
    val themes by viewModel.themes.collectAsStateWithLifecycle(emptyList())

    val systemPalette = systemCorePalette()

    PreferenceScreen(title = stringResource(R.string.preference_screen_colors)) {
        item {
            PreferenceCategory {
                for (theme in themes) {
                    var showMenu by remember { mutableStateOf(false) }
                    Preference(
                        icon = if (theme.id == selectedTheme) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                        title = theme.name,
                        controls = {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Rounded.MoreVert, null)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                if (!theme.builtIn) {
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Edit, null)
                                        },
                                        text = { Text("Edit") },
                                        onClick = {
                                            navController?.navigate("settings/appearance/themes/${theme.id}")
                                            showMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .height(8.dp)
                            .clip(
                                MaterialTheme.shapes.small.copy(
                                    topStart = CornerSize(0f),
                                    topEnd = CornerSize(0f)
                                )
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(theme.corePalette.primary ?: systemPalette.primary))
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        Box(
                            modifier = Modifier
                                .background(Color(theme.corePalette.secondary ?: systemPalette.secondary))
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        Box(
                            modifier = Modifier
                                .background(Color(theme.corePalette.tertiary ?: systemPalette.tertiary))
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        Box(
                            modifier = Modifier
                                .background(Color(theme.corePalette.neutral ?: systemPalette.neutral))
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    Color(theme.corePalette.neutralVariant ?: systemPalette.neutralVariant))
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        Box(
                            modifier = Modifier
                                .background(Color(theme.corePalette.error ?: systemPalette.error))
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}