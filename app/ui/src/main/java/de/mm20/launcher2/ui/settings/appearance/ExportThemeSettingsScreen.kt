package de.mm20.launcher2.ui.settings.appearance

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.TextPreference

@Composable
fun ExportThemeSettingsScreen() {
    val viewModel = viewModel<ExportThemeSettingsScreenVM>()

    val context = LocalContext.current

    val colorSchemes by viewModel.colorSchemes.collectAsState(emptyList())
    val shapeThemes by viewModel.shapeSchemes.collectAsState(emptyList())

    val isValidSelection by remember {
        derivedStateOf {
            viewModel.colorScheme != null || viewModel.shapeScheme != null
        }
    }

    val fileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.de.mm20.launcher2.theme")) {
        if (it != null) viewModel.exportTheme(context, it)
    }

    LaunchedEffect(Unit) {
        viewModel.init()
    }
    PreferenceScreen(
        title = stringResource(R.string.theme_export_title)
    ) {
        item {
            PreferenceCategory {
                TextPreference(
                    stringResource(R.string.theme_bundle_name),
                    value = viewModel.themeName,
                    summary = viewModel.themeName.takeIf { it.isNotBlank() },
                    onValueChanged = {
                        viewModel.themeName = it
                    }
                )
                TextPreference(
                    stringResource(R.string.theme_bundle_author),
                    value = viewModel.themeAuthor,
                    summary = viewModel.themeAuthor.takeIf { it.isNotBlank() },
                    onValueChanged = {
                        viewModel.themeAuthor = it
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                ListPreference(
                    stringResource(R.string.preference_screen_colors),
                    icon = Icons.Rounded.Palette,
                    value = viewModel.colorScheme,
                    items = listOf(stringResource(R.string.no_selection) to null) + colorSchemes.map {
                        it.name to it
                    },
                    onValueChanged = { newValue ->
                        viewModel.setColorScheme(newValue)
                    }
                )
                ListPreference(
                    stringResource(R.string.preference_screen_shapes),
                    icon = Icons.Rounded.CropSquare,
                    value = viewModel.shapeScheme,
                    items = listOf(stringResource(R.string.no_selection) to null) + shapeThemes.map {
                        it.name to it
                    },
                    onValueChanged = { newValue ->
                        viewModel.setShapeScheme(newValue)
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                var showDropdown by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.shapes.extraSmall
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(SplitButtonDefaults.Spacing)
                ) {
                    SplitButtonDefaults.LeadingButton(
                        modifier = Modifier.weight(1f),
                        enabled = isValidSelection,
                        onClick = {
                            viewModel.shareTheme(context)
                        }
                    ) {
                        Icon(
                            Icons.Rounded.Share,
                            null,
                            modifier = Modifier
                                .padding(end = ButtonDefaults.IconSpacing)
                                .size(SplitButtonDefaults.LeadingIconSize),
                        )
                        Text(stringResource(R.string.menu_share))
                    }
                    SplitButtonDefaults.TrailingButton(
                        onClick = {
                            showDropdown = !showDropdown
                        },
                        enabled = isValidSelection,
                    ) {
                        val rotation: Float by animateFloatAsState(
                            targetValue = if (showDropdown) 180f else 0f,
                            label = "Trailing Icon Rotation"
                        )
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            modifier =
                                Modifier
                                    .size(SplitButtonDefaults.TrailingIconSize)
                                    .graphicsLayer {
                                        this.rotationZ = rotation
                                    },
                            contentDescription = null
                        )

                        DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.save_as_file)) },
                                onClick = {
                                    fileChooserLauncher.launch("${viewModel.themeName}.kvtheme")
                                    showDropdown = false
                                },
                                leadingIcon = { Icon(Icons.Rounded.Save, contentDescription = null) }
                            )
                        }
                    }
                }
            }
        }
    }
}