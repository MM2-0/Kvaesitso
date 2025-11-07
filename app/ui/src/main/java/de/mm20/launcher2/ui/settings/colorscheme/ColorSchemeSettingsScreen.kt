package de.mm20.launcher2.ui.settings.colorscheme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TintedIconLayer
import de.mm20.launcher2.themes.colors.DefaultDarkColorScheme
import de.mm20.launcher2.themes.colors.DefaultLightColorScheme
import de.mm20.launcher2.themes.colors.merge
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.theme.colorscheme.darkColorSchemeOf
import de.mm20.launcher2.ui.theme.colorscheme.lightColorSchemeOf
import de.mm20.launcher2.ui.theme.colorscheme.systemCorePalette
import palettes.CorePalette
import java.util.UUID

@Composable
fun ColorSchemeSettingsScreen(themeId: UUID) {
    val viewModel: ColorSchemesSettingsScreenVM = viewModel()

    val context = LocalContext.current
    val dark = LocalDarkTheme.current

    val theme by remember(
        viewModel,
        themeId
    ) { viewModel.getTheme(themeId) }.collectAsStateWithLifecycle(null)

    var previewDarkTheme by remember(dark) { mutableStateOf(dark) }
    val previewColorScheme =
        theme?.let { if (previewDarkTheme) darkColorSchemeOf(it) else lightColorSchemeOf(it) }

    val systemPalette = systemCorePalette()

    val mergedCorePalette by remember(theme?.corePalette, systemPalette) {
        derivedStateOf {
            theme?.corePalette?.merge(systemPalette) ?: systemPalette
        }
    }

    var editName by remember { mutableStateOf(false) }

    if (editName) {
        var name by remember(theme) { mutableStateOf(theme?.name ?: "") }
        AlertDialog(
            onDismissRequest = { editName = false },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateTheme(theme!!.copy(name = name))
                        editName = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        )
    }

    PreferenceScreen(
        title = {
            Text(
                theme?.name ?: "",
                modifier = Modifier.clickable {
                    editName = true
                }
            )
        },
        helpUrl = "https://kvaesitso.mm20.de/docs/user-guide/customization/color-schemes",
    ) {
        if (theme == null || previewColorScheme == null) return@PreferenceScreen
        val selectedColorScheme =
            if (previewDarkTheme) theme!!.darkColorScheme else theme!!.lightColorScheme
        val selectedDefaultScheme =
            if (previewDarkTheme) DefaultDarkColorScheme else DefaultLightColorScheme
        item {

            PreferenceCategory(
                title = stringResource(R.string.preference_custom_colors_corepalette),
            ) {
                CorePaletteColorPreference(
                    title = "Primary",
                    value = theme?.corePalette?.primary,
                    onValueChange = {
                        viewModel.updateTheme(
                            theme!!.copy(
                                corePalette = theme!!.corePalette.copy(
                                    primary = it
                                )
                            )
                        )
                    },
                    defaultValue = systemPalette.primary,
                )
                CorePaletteColorPreference(
                    title = "Secondary",
                    value = theme?.corePalette?.secondary,
                    onValueChange = {
                        viewModel.updateTheme(
                            theme!!.copy(
                                corePalette = theme!!.corePalette.copy(
                                    secondary = it
                                )
                            )
                        )
                    },
                    defaultValue = systemPalette.secondary,
                    autoGenerate = {
                        theme!!.corePalette.primary?.let {
                            CorePalette.of(it).a2.keyColor.toInt()
                        }
                    },
                )
                CorePaletteColorPreference(
                    title = "Tertiary",
                    value = theme?.corePalette?.tertiary,
                    onValueChange = {
                        viewModel.updateTheme(
                            theme!!.copy(
                                corePalette = theme!!.corePalette.copy(
                                    tertiary = it
                                )
                            )
                        )
                    },
                    defaultValue = systemPalette.tertiary,
                    autoGenerate = {
                        theme!!.corePalette.primary?.let {
                            CorePalette.of(it).a3.keyColor.toInt()
                        }
                    },
                )
                CorePaletteColorPreference(
                    title = "Neutral",
                    value = theme?.corePalette?.neutral,
                    onValueChange = {
                        viewModel.updateTheme(
                            theme!!.copy(
                                corePalette = theme!!.corePalette.copy(
                                    neutral = it
                                )
                            )
                        )
                    },
                    defaultValue = systemPalette.neutral,
                    autoGenerate = {
                        theme!!.corePalette.primary?.let {
                            CorePalette.of(it).n1.keyColor.toInt()
                        }
                    },
                )
                CorePaletteColorPreference(
                    title = "Neutral Variant",
                    value = theme?.corePalette?.neutralVariant,
                    onValueChange = {
                        viewModel.updateTheme(
                            theme!!.copy(
                                corePalette = theme!!.corePalette.copy(
                                    neutralVariant = it
                                )
                            )
                        )
                    },
                    defaultValue = systemPalette.neutralVariant,
                    autoGenerate = {
                        theme!!.corePalette.primary?.let {
                            CorePalette.of(it).n2.keyColor.toInt()
                        }
                    },
                )
                CorePaletteColorPreference(
                    title = "Error",
                    value = theme?.corePalette?.error,
                    onValueChange = {
                        viewModel.updateTheme(
                            theme!!.copy(
                                corePalette = theme!!.corePalette.copy(
                                    error = it
                                )
                            )
                        )
                    },
                    defaultValue = systemPalette.error,
                    autoGenerate = {
                        theme!!.corePalette.primary?.let {
                            CorePalette.of(it).error.keyColor.toInt()
                        }
                    },
                )
            }
        }
        item {
            ColorSchemePreferenceCategory(
                title = "Primary colors",
                previewColorScheme = previewColorScheme,
                darkMode = previewDarkTheme,
                onDarkModeChanged = { previewDarkTheme = it },
                colorPreferences = {
                    ThemeColorPreference(
                        title = "Primary",
                        value = selectedColorScheme.primary,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            primary = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            primary = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.primary,
                    )
                    ThemeColorPreference(
                        title = "On Primary",
                        value = selectedColorScheme.onPrimary,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            onPrimary = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            onPrimary = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.onPrimary,
                    )
                    ThemeColorPreference(
                        title = "Primary Container",
                        value = selectedColorScheme.primaryContainer,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            primaryContainer = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            primaryContainer = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.primaryContainer,
                    )
                    ThemeColorPreference(
                        title = "On Primary Container",
                        value = selectedColorScheme.onPrimaryContainer,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            onPrimaryContainer = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            onPrimaryContainer = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.onPrimaryContainer,
                    )

                },
            ) {
                Button(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .align(Alignment.CenterVertically),
                    onClick = { }) {
                    Text("Button")
                }
                Switch(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .align(Alignment.CenterVertically),
                    checked = true,
                    onCheckedChange = {}
                )
                FloatingActionButton(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .align(Alignment.CenterVertically),
                    onClick = { }
                ) {
                    Icon(painterResource(R.drawable.edit_24px), null)
                }
                ShapedLauncherIcon(
                    size = 48.dp,
                    icon = {
                        StaticLauncherIcon(
                            foregroundLayer = TintedIconLayer(
                                ContextCompat.getDrawable(
                                    context,
                                    R.drawable.ic_launcher_monochrome
                                )!!,
                                scale = 1.5f,
                            ),
                            backgroundLayer = ColorLayer(),
                        )
                    }
                )
            }
        }
        item {
            ColorSchemePreferenceCategory(
                title = "Secondary colors",
                previewColorScheme = previewColorScheme,
                darkMode = previewDarkTheme,
                onDarkModeChanged = { previewDarkTheme = it },
                colorPreferences = {
                    ThemeColorPreference(
                        title = "Secondary",
                        value = selectedColorScheme.secondary,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            secondary = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            secondary = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.secondary,
                    )
                    ThemeColorPreference(
                        title = "On Secondary",
                        value = selectedColorScheme.onSecondary,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            onSecondary = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            onSecondary = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.onSecondary,
                    )
                    ThemeColorPreference(
                        title = "Secondary Container",
                        value = selectedColorScheme.secondaryContainer,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            secondaryContainer = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            secondaryContainer = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.secondaryContainer,
                    )
                    ThemeColorPreference(
                        title = "On Secondary Container",
                        value = selectedColorScheme.onSecondaryContainer,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            onSecondaryContainer = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            onSecondaryContainer = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.onSecondaryContainer,
                    )
                },
            ) {
                Text(
                    "Headline",
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .align(Alignment.CenterVertically),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.titleSmall,
                )
                FilterChip(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .align(Alignment.CenterVertically),
                    label = { Text("Tag") },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.tag_20px),
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    },
                    selected = true,
                    onClick = {},
                )
                FilledTonalIconButton(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .align(Alignment.CenterVertically),
                    onClick = { },
                ) {
                    Icon(painterResource(R.drawable.play_arrow_24px), null)
                }
            }
        }
        item {
            ColorSchemePreferenceCategory(
                title = "Tertiary colors",
                previewColorScheme = previewColorScheme,
                darkMode = previewDarkTheme,
                onDarkModeChanged = { previewDarkTheme = it },
                colorPreferences = {
                    ThemeColorPreference(
                        title = "Tertiary",
                        value = selectedColorScheme.tertiary,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            tertiary = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            tertiary = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.tertiary,
                    )
                    ThemeColorPreference(
                        title = "On Tertiary",
                        value = selectedColorScheme.onTertiary,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            onTertiary = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            onTertiary = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.onTertiary,
                    )
                    ThemeColorPreference(
                        title = "Tertiary Container",
                        value = selectedColorScheme.tertiaryContainer,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            tertiaryContainer = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            tertiaryContainer = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.tertiaryContainer,
                    )
                    ThemeColorPreference(
                        title = "On Tertiary Container",
                        value = selectedColorScheme.onTertiaryContainer,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            onTertiaryContainer = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            onTertiaryContainer = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.onTertiaryContainer,
                    )
                },
            ) {
                ShapedLauncherIcon(
                    badge = { Badge() },
                    size = 48.dp,
                )
            }
        }
        item {
            ColorSchemePreferenceCategory(
                title = "Surface colors",
                previewColorScheme = previewColorScheme,
                darkMode = previewDarkTheme,
                onDarkModeChanged = { previewDarkTheme = it },
                colorPreferences = {
                    ThemeColorPreference(
                        title = "Surface Dim",
                        value = selectedColorScheme.surfaceDim,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            surfaceDim = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            surfaceDim = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.surfaceDim,
                    )
                    ThemeColorPreference(
                        title = "Surface",
                        value = selectedColorScheme.surface,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            surface = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            surface = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.surface,
                    )
                    ThemeColorPreference(
                        title = "Surface Bright",
                        value = selectedColorScheme.surfaceBright,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            surfaceBright = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            surfaceBright = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.surfaceBright,
                    )
                    ThemeColorPreference(
                        title = "Surface Tint",
                        value = selectedColorScheme.surfaceTint,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            surfaceTint = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            surfaceTint = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.surfaceTint,
                    )
                },
            ) {
                ElevatedCard(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(64.dp),
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = 0.dp
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Text",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                ElevatedCard(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(64.dp),
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = 1.dp
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Text",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                ElevatedCard(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(64.dp),
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = 8.dp
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.search_24px), null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        item {
            ColorSchemePreferenceCategory(
                title = "Surface container colors",
                previewColorScheme = previewColorScheme,
                darkMode = previewDarkTheme,
                onDarkModeChanged = { previewDarkTheme = it },
                colorPreferences = {
                    ThemeColorPreference(
                        title = "Surface Container Lowest",
                        value = selectedColorScheme.surfaceContainerLowest,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            surfaceContainerLowest = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            surfaceContainerLowest = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.surfaceContainerLowest,
                    )
                    ThemeColorPreference(
                        title = "Surface Container Low",
                        value = selectedColorScheme.surfaceContainerLow,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            surfaceContainerLow = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            surfaceContainerLow = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.surfaceContainerLow,
                    )
                    ThemeColorPreference(
                        title = "Surface Container",
                        value = selectedColorScheme.surfaceContainer,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            surfaceContainer = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            surfaceContainer = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.surfaceContainer,
                    )
                    ThemeColorPreference(
                        title = "Surface Container High",
                        value = selectedColorScheme.surfaceContainerHigh,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            surfaceContainerHigh = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            surfaceContainerHigh = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.surfaceContainerHigh,
                    )
                    ThemeColorPreference(
                        title = "Surface Container Highest",
                        value = selectedColorScheme.surfaceContainerHighest,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            surfaceContainerHighest = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            surfaceContainerHighest = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.surfaceContainerHighest,
                    )
                    ThemeColorPreference(
                        title = "Surface Variant",
                        value = selectedColorScheme.surfaceVariant,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            surfaceVariant = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            surfaceVariant = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.surfaceVariant,
                    )
                },
            ) {
                Banner(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .align(Alignment.CenterVertically)
                        .width(240.dp),
                    text = "Banner",
                    icon = R.drawable.lock_24px,
                )
                Switch(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .align(Alignment.CenterVertically),
                    checked = false,
                    onCheckedChange = {}
                )
            }
        }
        item {
            ColorSchemePreferenceCategory(
                title = "Content colors",
                previewColorScheme = previewColorScheme,
                darkMode = previewDarkTheme,
                onDarkModeChanged = { previewDarkTheme = it },
                colorPreferences = {
                    ThemeColorPreference(
                        title = "On Surface",
                        value = selectedColorScheme.onSurface,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            onSurface = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            onSurface = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.onSurface,
                    )


                    ThemeColorPreference(
                        title = "On Surface Variant",
                        value = selectedColorScheme.onSurfaceVariant,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            onSurfaceVariant = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            onSurfaceVariant = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.onSurfaceVariant,
                    )

                    ThemeColorPreference(
                        title = "On Background",
                        value = selectedColorScheme.onBackground,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            onBackground = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            onBackground = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.onSurfaceVariant,
                    )
                },
            ) {
                ElevatedCard(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(64.dp),
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = 0.dp
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Text",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                ElevatedCard(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(64.dp),
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = 1.dp
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Text",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                IconButton(
                    onClick = {  },
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .align(Alignment.CenterVertically),
                ) {
                    Icon(painterResource(R.drawable.star_24px), null)
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.extraSmall,
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp,
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .width(IntrinsicSize.Max),
                    ) {
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(painterResource(R.drawable.open_in_new_24px), null)
                            },
                            text = { Text("Menu") },
                            onClick = { })
                    }
                }
            }
        }
        item {
            ColorSchemePreferenceCategory(
                title = "Outline colors",
                previewColorScheme = previewColorScheme,
                darkMode = previewDarkTheme,
                onDarkModeChanged = { previewDarkTheme = it },
                colorPreferences = {
                    ThemeColorPreference(
                        title = "Outline",
                        value = selectedColorScheme.outline,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            outline = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            outline = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.outline,
                    )
                    ThemeColorPreference(
                        title = "Outline Variant",
                        value = selectedColorScheme.outlineVariant,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            outlineVariant = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            outlineVariant = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.outlineVariant,
                    )
                },
            ) {
                FilterChip(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .align(Alignment.CenterVertically),
                    label = { Text("Tag") },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.tag_20px),
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    },
                    selected = false,
                    onClick = {},
                )
                OutlinedButton(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .align(Alignment.CenterVertically),
                    onClick = { }) {
                    Text("Button")
                }
                OutlinedCard(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(64.dp)
                        .align(Alignment.CenterVertically)
                ) {}
                VerticalDivider(
                    modifier = Modifier
                        .height(64.dp)
                        .padding(end = 16.dp),
                )
            }
        }
        item {
            ColorSchemePreferenceCategory(
                title = "Error colors",
                previewColorScheme = previewColorScheme,
                darkMode = previewDarkTheme,
                onDarkModeChanged = { previewDarkTheme = it },
                colorPreferences = {
                    ThemeColorPreference(
                        title = "Error",
                        value = selectedColorScheme.error,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            error = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            error = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.error,
                    )
                    ThemeColorPreference(
                        title = "On Error",
                        value = selectedColorScheme.onError,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            onError = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            onError = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.onError,
                    )
                    ThemeColorPreference(
                        title = "Error Container",
                        value = selectedColorScheme.errorContainer,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            errorContainer = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            errorContainer = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.errorContainer,
                    )
                    ThemeColorPreference(
                        title = "On Error Container",
                        value = selectedColorScheme.onErrorContainer,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            onErrorContainer = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            onErrorContainer = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.onErrorContainer,
                    )
                },
            ) {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    isError = true,
                    readOnly = true,
                    label = { Text("Error") }
                )
            }
        }
        item {
            ColorSchemePreferenceCategory(
                title = "Inverse colors",
                previewColorScheme = previewColorScheme,
                darkMode = previewDarkTheme,
                onDarkModeChanged = { previewDarkTheme = it },
                colorPreferences = {
                    ThemeColorPreference(
                        title = "Inverse Surface",
                        value = selectedColorScheme.inverseSurface,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            inverseSurface = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            inverseSurface = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.inverseSurface,
                    )
                    ThemeColorPreference(
                        title = "Inverse Surface",
                        value = selectedColorScheme.inverseOnSurface,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            inverseOnSurface = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            inverseOnSurface = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.inverseOnSurface,
                    )
                    ThemeColorPreference(
                        title = "Inverse Primary",
                        value = selectedColorScheme.inversePrimary,
                        corePalette = mergedCorePalette,
                        onValueChange = {
                            viewModel.updateTheme(
                                if (previewDarkTheme) {
                                    theme!!.copy(
                                        darkColorScheme = theme!!.darkColorScheme.copy(
                                            inversePrimary = it
                                        )
                                    )
                                } else {
                                    theme!!.copy(
                                        lightColorScheme = theme!!.lightColorScheme.copy(
                                            inversePrimary = it
                                        )
                                    )
                                }
                            )
                        },
                        defaultValue = selectedDefaultScheme.inversePrimary,
                    )
                },
            ) {
                Box(modifier = Modifier.width(240.dp)) {
                    Snackbar(
                        action = {
                            TextButton(
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary),
                                onClick = { },
                                content = { Text("Action") }
                            )
                        },
                        content = {
                            Text("Snackbar")
                        }
                    )
                }
            }
        }
    }
}

