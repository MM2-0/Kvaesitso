package de.mm20.launcher2.ui.settings.colorscheme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.CustomColors
import de.mm20.launcher2.preferences.ktx.toSettingsColorsScheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import palettes.CorePalette
import palettes.TonalPalette
import scheme.Scheme

class CustomColorSchemeSettingsScreenVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    val advancedMode = dataStore.data.map { it.appearance.customColors.advancedMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setAdvancedMode(advancedMode: Boolean) {
        viewModelScope.launch {
            val lightScheme = dataStore.updateData {
                val customColors = it.appearance.customColors.toBuilder()
                    .setAdvancedMode(advancedMode)
                it.toBuilder()
                    .setAppearance(
                        it.appearance.toBuilder()
                            .setCustomColors(customColors)
                    )
                    .build()
            }.appearance.customColors.lightScheme

            if (!advancedMode) {
                setBaseColors(CustomColors.BaseColors
                    .newBuilder()
                    .setAccent1(lightScheme.primary)
                    .setAccent2(lightScheme.secondary)
                    .setAccent3(lightScheme.tertiary)
                    .setNeutral1(lightScheme.surface)
                    .setNeutral2(lightScheme.surface)
                    .setError(lightScheme.error)
                    .build()
                )
            }
        }
    }

    fun generateFromPrimaryColor() {
        viewModelScope.launch {
            val primary = dataStore.data.map { it.appearance.customColors.baseColors.accent1 }.first()
            val scheme = Scheme.light(primary)
            setBaseColors(
                CustomColors.BaseColors.newBuilder()
                    .setAccent1(scheme.primary)
                    .setAccent2(scheme.secondary)
                    .setAccent3(scheme.tertiary)
                    .setNeutral1(scheme.surface)
                    .setNeutral2(scheme.outline)
                    .setError(scheme.error)
                    .build()
            )
        }
    }

    val baseColors = dataStore.data.map { it.appearance.customColors.baseColors }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setBaseColors(baseColors: CustomColors.BaseColors) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setAppearance(
                        it.appearance.toBuilder()
                            .setCustomColors(
                                it.appearance.customColors.toBuilder()
                                    .setBaseColors(baseColors)
                            )
                    )
                    .build()
            }
            setDarkScheme(baseColorsToDarkScheme(baseColors))
            setLightScheme(baseColorsToLightScheme(baseColors))
        }
    }

    val darkScheme = dataStore.data.map { it.appearance.customColors.darkScheme }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setDarkScheme(darkScheme: CustomColors.Scheme) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setAppearance(
                        it.appearance.toBuilder()
                            .setCustomColors(
                                it.appearance.customColors.toBuilder()
                                    .setDarkScheme(darkScheme)
                            )
                    )
                    .build()
            }
        }
    }

    val lightScheme = dataStore.data.map { it.appearance.customColors.lightScheme }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setLightScheme(lightScheme: CustomColors.Scheme) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setAppearance(
                        it.appearance.toBuilder()
                            .setCustomColors(
                                it.appearance.customColors.toBuilder()
                                    .setLightScheme(lightScheme)
                            )
                    )
                    .build()
            }
        }
    }

    private fun baseColorsToDarkScheme(baseColors: CustomColors.BaseColors): CustomColors.Scheme {
        val a1 = TonalPalette.fromInt(baseColors.accent1)
        val a2 = TonalPalette.fromInt(baseColors.accent2)
        val a3 = TonalPalette.fromInt(baseColors.accent3)
        val n1 = TonalPalette.fromInt(baseColors.neutral1)
        val n2 = TonalPalette.fromInt(baseColors.neutral2)
        val error = TonalPalette.fromInt(baseColors.error)

        val scheme = Scheme.darkFromCorePalette(CorePalette(a1, a2, a3, n1, n2, error))
        return scheme.toSettingsColorsScheme()
    }

    private fun baseColorsToLightScheme(baseColors: CustomColors.BaseColors): CustomColors.Scheme {
        val a1 = TonalPalette.fromInt(baseColors.accent1)
        val a2 = TonalPalette.fromInt(baseColors.accent2)
        val a3 = TonalPalette.fromInt(baseColors.accent3)
        val n1 = TonalPalette.fromInt(baseColors.neutral1)
        val n2 = TonalPalette.fromInt(baseColors.neutral2)
        val error = TonalPalette.fromInt(baseColors.error)

        val scheme = Scheme.lightFromCorePalette(CorePalette(a1, a2, a3, n1, n2, error))
        return scheme.toSettingsColorsScheme()
    }
}