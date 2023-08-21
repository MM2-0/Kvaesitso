package de.mm20.launcher2.ui.settings.colorscheme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltipBox
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TintedIconLayer
import de.mm20.launcher2.themes.ColorRef
import de.mm20.launcher2.themes.CorePaletteColor
import de.mm20.launcher2.themes.DefaultDarkColorScheme
import de.mm20.launcher2.themes.DefaultLightColorScheme
import de.mm20.launcher2.themes.FullCorePalette
import de.mm20.launcher2.themes.StaticColor
import de.mm20.launcher2.themes.atTone
import de.mm20.launcher2.themes.get
import de.mm20.launcher2.themes.merge
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.colorpicker.HctColorPicker
import de.mm20.launcher2.ui.component.colorpicker.rememberHctColorPickerState
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.ktx.hct
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.theme.colorscheme.darkColorSchemeOf
import de.mm20.launcher2.ui.theme.colorscheme.lightColorSchemeOf
import de.mm20.launcher2.ui.theme.colorscheme.systemCorePalette
import hct.Hct
import palettes.CorePalette
import java.util.UUID
import kotlin.math.roundToInt
import de.mm20.launcher2.themes.Color as ThemeColor

@Composable
fun ThemeSettingsScreen(themeId: UUID) {
    val viewModel: ThemesSettingsScreenVM = viewModel()

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

    PreferenceScreen(
        title = theme?.name ?: "",
        helpUrl = "https://kvaesitso.mm20.de/docs/user-guide/customization/color-schemes",
    ) {
        if (theme == null || previewColorScheme == null) return@PreferenceScreen
        val selectedColorScheme =
            if (previewDarkTheme) theme!!.darkColorScheme else theme!!.lightColorScheme
        val selectedDefaultScheme =
            if (previewDarkTheme) DefaultDarkColorScheme else DefaultLightColorScheme
        item {

            Column(
                modifier = Modifier
            ) {
                Text(
                    stringResource(R.string.preference_custom_colors_corepalette),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(16.dp)
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
                        autoGenerate = {
                            theme!!.corePalette.primary?.let {
                                CorePalette.of(it).n1.keyColor.toInt()
                            }
                        },
                    )
                    CorePaletteColorPreference(
                        title = "NeutralVariant",
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
                        modifier = Modifier.padding(end = 12.dp),
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
                HorizontalDivider()
            }
        }
        item {
            ThemePreferenceCategory(
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
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
                    Icon(Icons.Rounded.Edit, null)
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
            ThemePreferenceCategory(
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
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
                            Icons.Rounded.Tag,
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
                    Icon(Icons.Rounded.PlayArrow, null)
                }
            }
        }
        item {
            ThemePreferenceCategory(
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            ) {
            }
        }
        item {
            ThemePreferenceCategory(
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
                    )

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
                        modifier = Modifier.padding(end = 12.dp),
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
                        Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        item {
            ThemePreferenceCategory(
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    ThemeColorPreference(
                        title = "On Surface Variant",
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
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            ) {

            }
        }
        item {
            ThemePreferenceCategory(
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
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
                            Icons.Rounded.Tag,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    },
                    selected = false,
                    onClick = {},
                )
                OutlinedButton(
                    modifier = Modifier.padding(end = 16.dp)
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
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        }
        item {
            ThemePreferenceCategory(
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            ) {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    isError = true,
                    readOnly = true,
                    label = {Text("Error")}
                )
            }
        }
        item {
            ThemePreferenceCategory(
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
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
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            ) {
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

@Composable
fun CorePaletteColorPreference(
    title: String,
    value: Int?,
    onValueChange: (Int?) -> Unit,
    defaultValue: Int,
    modifier: Modifier = Modifier,
    autoGenerate: (() -> Int?)? = null,
) {
    var showDialog by remember { mutableStateOf(false) }

    PlainTooltipBox(tooltip = { Text(title) }) {
        ColorSwatch(
            color = Color(value ?: defaultValue),
            modifier = modifier
                .size(48.dp)
                .tooltipTrigger()
                .clickable { showDialog = true },
        )
    }

    if (showDialog) {
        BottomSheetDialog(onDismissRequest = { showDialog = false }) {
            Column(
                modifier = Modifier.padding(it)
            ) {
                SwitchPreference(
                    icon = Icons.Rounded.SettingsSuggest,
                    title = "Use system default",
                    value = value == null,
                    onValueChanged = {
                        onValueChange(if (it) null else defaultValue)
                    }
                )
                AnimatedVisibility(
                    value != null,
                    enter = expandVertically(
                        expandFrom = Alignment.Top,
                    ),
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Top,
                    )
                ) {
                    Column {
                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        val colorPickerState = rememberHctColorPickerState(
                            initialColor = Color(value ?: defaultValue),
                            onColorChanged = {
                                onValueChange(it.toArgb())
                            }
                        )
                        HctColorPicker(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.CenterHorizontally),
                            state = colorPickerState,
                        )

                        if (autoGenerate != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 16.dp)
                            )

                            TextButton(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .align(Alignment.End),
                                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                                onClick = {
                                    val autoGenerated = autoGenerate()
                                    onValueChange(autoGenerated)
                                    if (autoGenerated != null) {
                                        colorPickerState.setColor(Color(autoGenerated))
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.AutoFixHigh, null,
                                    modifier = Modifier
                                        .padding(ButtonDefaults.IconSpacing)
                                        .size(ButtonDefaults.IconSize)
                                )
                                Text("From primary color")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemePreferenceCategory(
    title: String,
    previewColorScheme: ColorScheme,
    darkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
    colorPreferences: @Composable () -> Unit = {},
    preview: @Composable FlowRowScope.() -> Unit
) {
    Column(
        modifier = Modifier.padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.shape(position = 0, count = 2),
                    selected = !darkMode,
                    onClick = { onDarkModeChanged(false) }
                ) {
                    Icon(Icons.Rounded.LightMode, null)
                }
                SegmentedButton(
                    shape = SegmentedButtonDefaults.shape(position = 1, count = 2),
                    selected = darkMode,
                    onClick = { onDarkModeChanged(true) }
                ) {
                    Icon(Icons.Rounded.DarkMode, null)
                }
            }
        }
        MaterialTheme(
            colorScheme = previewColorScheme
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    preview()
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            colorPreferences()
        }
        HorizontalDivider()
    }

}

@Composable
fun ThemeColorPreference(
    title: String,
    value: ThemeColor?,
    corePalette: FullCorePalette,
    onValueChange: (ThemeColor?) -> Unit,
    defaultValue: ThemeColor,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    val actualValue = value ?: defaultValue

    PlainTooltipBox(tooltip = { Text(title) }) {
        ColorSwatch(
            color = Color(actualValue.get(corePalette)),
            modifier = modifier
                .size(48.dp)
                .tooltipTrigger()
                .clickable { showDialog = true },
        )
    }

    if (showDialog) {
        BottomSheetDialog(onDismissRequest = { showDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(it),
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = actualValue is ColorRef,
                        onClick = {
                            if (actualValue is ColorRef) return@SegmentedButton
                            onValueChange(defaultValue)
                        },
                        icon = {
                            SegmentedButtonDefaults.SegmentedButtonIcon(
                                active = actualValue is ColorRef,
                            ) {
                                Icon(
                                    Icons.Rounded.Palette,
                                    null,
                                    modifier = Modifier
                                        .size(SegmentedButtonDefaults.IconSize)
                                )
                            }
                        },
                        shape = SegmentedButtonDefaults.shape(position = 0, count = 2)
                    ) {
                        Text("From palette")
                    }
                    SegmentedButton(
                        selected = actualValue is StaticColor,
                        onClick = {
                            onValueChange(StaticColor(actualValue.get(corePalette)))
                        },
                        icon = {
                            SegmentedButtonDefaults.SegmentedButtonIcon(
                                active = actualValue is StaticColor,
                            ) {
                                Icon(
                                    Icons.Rounded.Colorize,
                                    null,
                                    modifier = Modifier
                                        .size(SegmentedButtonDefaults.IconSize)
                                )
                            }
                        },
                        shape = SegmentedButtonDefaults.shape(position = 1, count = 2)
                    ) {
                        Text("Custom")
                    }
                }
                AnimatedContent(
                    actualValue,
                    label = "AnimatedContent",
                    contentKey = { it is StaticColor }
                ) { themeColor ->
                    Column {
                        if (themeColor is StaticColor) {
                            val colorPickerState = rememberHctColorPickerState(
                                initialColor = Color(themeColor.color),
                                onColorChanged = {
                                    onValueChange(StaticColor(it.toArgb()))
                                }
                            )
                            HctColorPicker(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp)
                                    .align(Alignment.CenterHorizontally),
                                state = colorPickerState
                            )
                        } else if (themeColor is ColorRef) {
                            val hct = Hct.fromInt(corePalette.get(themeColor.color))
                            val hue = hct.hue.toFloat()
                            val chroma = hct.chroma.toFloat()
                            var tone by remember(value == null) { mutableStateOf(themeColor.tone.toFloat()) }
                            Row(
                                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                            ) {
                                ColorSwatch(
                                    color = Color(
                                        corePalette
                                            .get(CorePaletteColor.Primary)
                                            .atTone(tone.toInt())
                                    ),
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(64.dp)
                                        .clickable {
                                            onValueChange(
                                                ColorRef(
                                                    CorePaletteColor.Primary,
                                                    tone.roundToInt()
                                                )
                                            )
                                        },
                                    selected = themeColor.color == CorePaletteColor.Primary,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                ColorSwatch(
                                    color = Color(
                                        corePalette
                                            .get(CorePaletteColor.Secondary)
                                            .atTone(tone.toInt())
                                    ),
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(64.dp)
                                        .clickable {
                                            onValueChange(
                                                ColorRef(
                                                    CorePaletteColor.Secondary,
                                                    tone.roundToInt()
                                                )
                                            )
                                        },
                                    selected = themeColor.color == CorePaletteColor.Secondary,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                ColorSwatch(
                                    color = Color(
                                        corePalette
                                            .get(CorePaletteColor.Tertiary)
                                            .atTone(tone.toInt())
                                    ),
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(64.dp)
                                        .clickable {
                                            onValueChange(
                                                ColorRef(
                                                    CorePaletteColor.Tertiary,
                                                    tone.roundToInt()
                                                )
                                            )
                                        },
                                    selected = themeColor.color == CorePaletteColor.Tertiary,
                                )
                            }
                            Row(
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                ColorSwatch(
                                    color = Color(
                                        corePalette
                                            .get(CorePaletteColor.Neutral)
                                            .atTone(tone.toInt())
                                    ),
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(64.dp)
                                        .clickable {
                                            onValueChange(
                                                ColorRef(
                                                    CorePaletteColor.Neutral,
                                                    tone.roundToInt()
                                                )
                                            )
                                        },
                                    selected = themeColor.color == CorePaletteColor.Neutral,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                ColorSwatch(
                                    color = Color(
                                        corePalette
                                            .get(CorePaletteColor.NeutralVariant)
                                            .atTone(tone.toInt())
                                    ),
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(64.dp)
                                        .clickable {
                                            onValueChange(
                                                ColorRef(
                                                    CorePaletteColor.NeutralVariant,
                                                    tone.roundToInt()
                                                )
                                            )
                                        },
                                    selected = themeColor.color == CorePaletteColor.NeutralVariant,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                ColorSwatch(
                                    color = Color(
                                        corePalette
                                            .get(CorePaletteColor.Error)
                                            .atTone(tone.toInt())
                                    ),
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(64.dp)
                                        .clickable {
                                            onValueChange(
                                                ColorRef(
                                                    CorePaletteColor.Error,
                                                    tone.roundToInt()
                                                )
                                            )
                                        },
                                    selected = themeColor.color == CorePaletteColor.Error,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                            ) {
                                Text(
                                    text = "C",
                                    modifier = Modifier.width(32.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center
                                )
                                Slider(
                                    modifier = Modifier.weight(1f),
                                    value = tone,
                                    valueRange = 0f..100f,
                                    onValueChange = {
                                        tone = it
                                        onValueChange(themeColor.copy(tone = it.roundToInt()))
                                    },
                                    track = {
                                        Canvas(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(20.dp)
                                        ) {
                                            drawRoundRect(
                                                brush = Brush.horizontalGradient(
                                                    colors = listOf(
                                                        Color.hct(hue, chroma, 0f),
                                                        Color.hct(hue, chroma, 10f),
                                                        Color.hct(hue, chroma, 20f),
                                                        Color.hct(hue, chroma, 30f),
                                                        Color.hct(hue, chroma, 40f),
                                                        Color.hct(hue, chroma, 50f),
                                                        Color.hct(hue, chroma, 60f),
                                                        Color.hct(hue, chroma, 70f),
                                                        Color.hct(hue, chroma, 80f),
                                                        Color.hct(hue, chroma, 90f),
                                                        Color.hct(hue, chroma, 100f),
                                                    )
                                                ),
                                                style = Fill,
                                                cornerRadius = CornerRadius(
                                                    10.dp.toPx(),
                                                    10.dp.toPx()
                                                )
                                            )
                                        }
                                    },
                                    thumb = {
                                        Box(
                                            modifier = Modifier
                                                .padding(vertical = 2.dp, horizontal = 8.dp)
                                                .size(16.dp)
                                                .shadow(1.dp, CircleShape)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                        )
                                    }
                                )
                                Text(
                                    text = tone.roundToInt().toString(),
                                    modifier = Modifier.width(32.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 16.dp)
                        )

                        TextButton(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .align(Alignment.End),
                            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                            onClick = { onValueChange(null) }
                        ) {
                            Icon(
                                Icons.Rounded.RestartAlt, null,
                                modifier = Modifier
                                    .padding(ButtonDefaults.IconSpacing)
                                    .size(ButtonDefaults.IconSize)
                            )
                            Text("Restore default")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColorSwatch(
    color: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    val darkTheme = LocalDarkTheme.current
    val iconColor = Color(Hct.fromInt(color.toArgb()).let {
        val tone = if (darkTheme) {
            if (it.tone.toInt() > 40) 30f
            else 60f
        } else {
            if (it.tone.toInt() < 60) 80f
            else 40f
        }
        it.apply {
            this.tone = tone.toDouble()
        }.toInt()
    })
    val borderColor = Color(Hct.fromInt(color.toArgb()).let {
        val tone = if (darkTheme) 30f else 80f
        it.apply {
            this.tone = tone.toDouble()
        }.toInt()
    })
    Box(
        modifier = modifier
            .clip(CircleShape)
            .border(
                if (selected) 4.dp else 1.dp,
                borderColor,
                CircleShape
            )
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.CheckCircle,
                null,
                modifier = Modifier.size(32.dp),
                tint = iconColor,
            )
        }
    }
}