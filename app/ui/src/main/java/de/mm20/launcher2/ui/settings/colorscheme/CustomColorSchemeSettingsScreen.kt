package de.mm20.launcher2.ui.settings.colorscheme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ColorPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen

@Composable
fun CustomColorSchemeSettingsScreen() {
    val viewModel: CustomColorSchemeSettingsScreenVM = viewModel()

    val advancedMode by viewModel.advancedMode.collectAsState()

    PreferenceScreen(
        title = stringResource(R.string.preference_screen_colors),
        helpUrl = "https://kvaesitso.mm20.de/docs/user-guide/customization/color-schemes",
        topBarActions = {
            var showOverflowMenu by remember { mutableStateOf(false) }
            IconButton(onClick = { showOverflowMenu = true }) {
                Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = null)
            }
            DropdownMenu(
                expanded = showOverflowMenu,
                onDismissRequest = { showOverflowMenu = false }) {
                if (advancedMode == false) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.preference_colors_auto_generate)) },
                        onClick = {
                            viewModel.generateFromPrimaryColor()
                            showOverflowMenu = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (advancedMode == true) {
                                    R.string.preference_custom_colors_simple_mode
                                } else {
                                    R.string.preference_custom_colors_advanced_mode
                                }
                            )

                        )
                    },
                    onClick = {
                        viewModel.setAdvancedMode(advancedMode?.not() ?: true)
                        showOverflowMenu = false
                    }
                )
            }
        }
    ) {
        if (advancedMode == false) {
            item {
                PreferenceCategory {
                    val baseColors by viewModel.baseColors.collectAsState()
                    ColorPreference(
                        title = stringResource(R.string.preference_custom_colors_a1),
                        value = baseColors?.let { Color(it.accent1) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = baseColors ?: return@ColorPreference
                            viewModel.setBaseColors(
                                colors.toBuilder()
                                    .setAccent1(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = stringResource(R.string.preference_custom_colors_a2),
                        value = baseColors?.let { Color(it.accent2) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = baseColors ?: return@ColorPreference
                            viewModel.setBaseColors(
                                colors.toBuilder()
                                    .setAccent2(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = stringResource(R.string.preference_custom_colors_a3),
                        value = baseColors?.let { Color(it.accent3) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = baseColors ?: return@ColorPreference
                            viewModel.setBaseColors(
                                colors.toBuilder()
                                    .setAccent3(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = stringResource(R.string.preference_custom_colors_n1),
                        value = baseColors?.let { Color(it.neutral1) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = baseColors ?: return@ColorPreference
                            viewModel.setBaseColors(
                                colors.toBuilder()
                                    .setNeutral1(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = stringResource(R.string.preference_custom_colors_n2),
                        value = baseColors?.let { Color(it.neutral2) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = baseColors ?: return@ColorPreference
                            viewModel.setBaseColors(
                                colors.toBuilder()
                                    .setNeutral2(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = stringResource(R.string.preference_custom_colors_error),
                        value = baseColors?.let { Color(it.error) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = baseColors ?: return@ColorPreference
                            viewModel.setBaseColors(
                                colors.toBuilder()
                                    .setError(it.toArgb())
                                    .build()
                            )
                        }
                    )
                }
            }
        }
        if (advancedMode == true) {
            item {
                PreferenceCategory(stringResource(R.string.preference_category_custom_colors_light)) {
                    val lightScheme by viewModel.lightScheme.collectAsState()
                    ColorPreference(
                        title = "Primary",
                        value = lightScheme?.let { Color(it.primary) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setPrimary(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Primary",
                        value = lightScheme?.let { Color(it.onPrimary) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setOnPrimary(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "Primary Container",
                        value = lightScheme?.let { Color(it.primaryContainer) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setPrimaryContainer(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Primary Container",
                        value = lightScheme?.let { Color(it.onPrimaryContainer) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setOnPrimaryContainer(it.toArgb())
                                    .build()
                            )
                        }
                    )

                    ColorPreference(
                        title = "Secondary",
                        value = lightScheme?.let { Color(it.secondary) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setSecondary(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Secondary",
                        value = lightScheme?.let { Color(it.onSecondary) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setOnSecondary(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "Secondary Container",
                        value = lightScheme?.let { Color(it.secondaryContainer) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setSecondaryContainer(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Secondary Container",
                        value = lightScheme?.let { Color(it.onSecondaryContainer) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setOnSecondaryContainer(it.toArgb())
                                    .build()
                            )
                        }
                    )

                    ColorPreference(
                        title = "Tertiary",
                        value = lightScheme?.let { Color(it.tertiary) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setTertiary(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Tertiary",
                        value = lightScheme?.let { Color(it.onTertiary) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setOnTertiary(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "Tertiary Container",
                        value = lightScheme?.let { Color(it.tertiaryContainer) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setTertiaryContainer(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Tertiary Container",
                        value = lightScheme?.let { Color(it.onTertiaryContainer) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setOnTertiaryContainer(it.toArgb())
                                    .build()
                            )
                        }
                    )

                    ColorPreference(
                        title = "Background",
                        value = lightScheme?.let { Color(it.background) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setBackground(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Background",
                        value = lightScheme?.let { Color(it.onBackground) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setOnBackground(it.toArgb())
                                    .build()
                            )
                        }
                    )

                    ColorPreference(
                        title = "Surface",
                        value = lightScheme?.let { Color(it.surface) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setSurface(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Surface",
                        value = lightScheme?.let { Color(it.onSurface) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setOnSurface(it.toArgb())
                                    .build()
                            )
                        }
                    )

                    ColorPreference(
                        title = "Surface Tint",
                        value = lightScheme?.let { Color(it.surfaceTint) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setSurfaceTint(it.toArgb())
                                    .build()
                            )
                        }
                    )

                    ColorPreference(
                        title = "Surface Variant",
                        value = lightScheme?.let { Color(it.surfaceVariant) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setSurfaceVariant(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Surface Variant",
                        value = lightScheme?.let { Color(it.onSurfaceVariant) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setOnSurfaceVariant(it.toArgb())
                                    .build()
                            )
                        }
                    )

                    ColorPreference(
                        title = "Error",
                        value = lightScheme?.let { Color(it.error) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setError(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Error",
                        value = lightScheme?.let { Color(it.onError) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setOnError(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "Error Container",
                        value = lightScheme?.let { Color(it.errorContainer) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setErrorContainer(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Error Container",
                        value = lightScheme?.let { Color(it.onErrorContainer) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setOnErrorContainer(it.toArgb())
                                    .build()
                            )
                        }
                    )

                    ColorPreference(
                        title = "Inverse Primary",
                        value = lightScheme?.let { Color(it.inversePrimary) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setInversePrimary(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "Inverse Surface",
                        value = lightScheme?.let { Color(it.inverseSurface) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setInverseSurface(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "Inverse On Surface",
                        value = lightScheme?.let { Color(it.inverseOnSurface) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setInverseOnSurface(it.toArgb())
                                    .build()
                            )
                        }
                    )

                    ColorPreference(
                        title = "Outline",
                        value = lightScheme?.let { Color(it.outline) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = lightScheme ?: return@ColorPreference
                            viewModel.setLightScheme(
                                colors.toBuilder()
                                    .setOutline(it.toArgb())
                                    .build()
                            )
                        }
                    )
                }

                PreferenceCategory(stringResource(R.string.preference_category_custom_colors_dark)) {
                    val darkScheme by viewModel.darkScheme.collectAsState()
                    ColorPreference(
                        title = "Primary",
                        value = darkScheme?.let { Color(it.primary) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setPrimary(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Primary",
                        value = darkScheme?.let { Color(it.onPrimary) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setOnPrimary(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "Primary Container",
                        value = darkScheme?.let { Color(it.primaryContainer) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setPrimaryContainer(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Primary Container",
                        value = darkScheme?.let { Color(it.onPrimaryContainer) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setOnPrimaryContainer(it.toArgb())
                                    .build()
                            )
                        }
                    )

                    ColorPreference(
                        title = "Secondary",
                        value = darkScheme?.let { Color(it.secondary) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setSecondary(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Secondary",
                        value = darkScheme?.let { Color(it.onSecondary) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setOnSecondary(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "Secondary Container",
                        value = darkScheme?.let { Color(it.secondaryContainer) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setSecondaryContainer(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Secondary Container",
                        value = darkScheme?.let { Color(it.onSecondaryContainer) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setOnSecondaryContainer(it.toArgb())
                                    .build()
                            )
                        }
                    )

                    ColorPreference(
                        title = "Tertiary",
                        value = darkScheme?.let { Color(it.tertiary) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setTertiary(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Tertiary",
                        value = darkScheme?.let { Color(it.onTertiary) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setOnTertiary(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "Tertiary Container",
                        value = darkScheme?.let { Color(it.tertiaryContainer) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setTertiaryContainer(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Tertiary Container",
                        value = darkScheme?.let { Color(it.onTertiaryContainer) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setOnTertiaryContainer(it.toArgb())
                                    .build()
                            )
                        }
                    )

                    ColorPreference(
                        title = "Background",
                        value = darkScheme?.let { Color(it.background) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setBackground(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Background",
                        value = darkScheme?.let { Color(it.onBackground) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setOnBackground(it.toArgb())
                                    .build()
                            )
                        }
                    )

                    ColorPreference(
                        title = "Surface",
                        value = darkScheme?.let { Color(it.surface) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setSurface(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Surface",
                        value = darkScheme?.let { Color(it.onSurface) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setOnSurface(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "Surface Tint",
                        value = darkScheme?.let { Color(it.surfaceTint) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setSurfaceTint(it.toArgb())
                                    .build()
                            )
                        }
                    )

                    ColorPreference(
                        title = "Surface Variant",
                        value = darkScheme?.let { Color(it.surfaceVariant) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setSurfaceVariant(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Surface Variant",
                        value = darkScheme?.let { Color(it.onSurfaceVariant) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setOnSurfaceVariant(it.toArgb())
                                    .build()
                            )
                        }
                    )

                    ColorPreference(
                        title = "Error",
                        value = darkScheme?.let { Color(it.error) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setError(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Error",
                        value = darkScheme?.let { Color(it.onError) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setOnError(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "Error Container",
                        value = darkScheme?.let { Color(it.errorContainer) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setErrorContainer(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "On Error Container",
                        value = darkScheme?.let { Color(it.onErrorContainer) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setOnErrorContainer(it.toArgb())
                                    .build()
                            )
                        }
                    )

                    ColorPreference(
                        title = "Inverse Primary",
                        value = darkScheme?.let { Color(it.inversePrimary) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setInversePrimary(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "Inverse Surface",
                        value = darkScheme?.let { Color(it.inverseSurface) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setInverseSurface(it.toArgb())
                                    .build()
                            )
                        }
                    )
                    ColorPreference(
                        title = "Inverse On Surface",
                        value = darkScheme?.let { Color(it.inverseOnSurface) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setInverseOnSurface(it.toArgb())
                                    .build()
                            )
                        }
                    )

                    ColorPreference(
                        title = "Outline",
                        value = darkScheme?.let { Color(it.outline) },
                        onValueChanged = {
                            if (it == null) return@ColorPreference
                            val colors = darkScheme ?: return@ColorPreference
                            viewModel.setDarkScheme(
                                colors.toBuilder()
                                    .setOutline(it.toArgb())
                                    .build()
                            )
                        }
                    )
                }
            }
        }
    }
}