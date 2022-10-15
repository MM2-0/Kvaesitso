package de.mm20.launcher2.ui.settings.colorscheme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.CustomColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import palettes.TonalPalette
import scheme.Scheme

class CustomColorSchemeSettingsScreenVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    val advancedMode = dataStore.data.map { it.appearance.customColors.advancedMode }.asLiveData()
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
                    .setNeutral2(scheme.surfaceVariant)
                    .setError(scheme.error)
                    .build()
            )
        }
    }

    val baseColors = dataStore.data.map { it.appearance.customColors.baseColors }.asLiveData()
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

    val darkScheme = dataStore.data.map { it.appearance.customColors.darkScheme }.asLiveData()
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

    val lightScheme = dataStore.data.map { it.appearance.customColors.lightScheme }.asLiveData()
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

        return CustomColors.Scheme.newBuilder()
            .setPrimary(a1.tone(80))
            .setSurfaceTint(a1.tone(80))
            .setOnPrimary(a1.tone(20))
            .setPrimaryContainer(a1.tone(30))
            .setOnPrimaryContainer(a1.tone(90))
            .setSecondary(a2.tone(80))
            .setOnSecondary(a2.tone(20))
            .setSecondaryContainer(a2.tone(30))
            .setOnSecondaryContainer(a2.tone(90))
            .setTertiary(a3.tone(80))
            .setOnTertiary(a3.tone(20))
            .setTertiaryContainer(a3.tone(30))
            .setOnTertiaryContainer(a3.tone(90))
            .setError(error.tone(80))
            .setOnError(error.tone(20))
            .setErrorContainer(error.tone(30))
            .setOnErrorContainer(error.tone(80))
            .setBackground(n1.tone(10))
            .setOnBackground(n1.tone(90))
            .setSurface(n1.tone(10))
            .setOnSurface(n1.tone(90))
            .setSurfaceVariant(n2.tone(30))
            .setOnSurfaceVariant(n2.tone(80))
            .setOutline(n2.tone(60))
            .setOutlineVariant(n2.tone(30))
            .setInverseSurface(n1.tone(90))
            .setInverseOnSurface(n1.tone(20))
            .setInversePrimary(a1.tone(40))
            .setScrim(n1.tone(0))
            .build()
    }

    private fun baseColorsToLightScheme(baseColors: CustomColors.BaseColors): CustomColors.Scheme {
        val a1 = TonalPalette.fromInt(baseColors.accent1)
        val a2 = TonalPalette.fromInt(baseColors.accent2)
        val a3 = TonalPalette.fromInt(baseColors.accent3)
        val n1 = TonalPalette.fromInt(baseColors.neutral1)
        val n2 = TonalPalette.fromInt(baseColors.neutral2)
        val error = TonalPalette.fromInt(baseColors.error)

        return CustomColors.Scheme.newBuilder()
            .setPrimary(a1.tone(40))
            .setSurfaceTint(a1.tone(40))
            .setOnPrimary(a1.tone(100))
            .setPrimaryContainer(a1.tone(90))
            .setOnPrimaryContainer(a1.tone(10))
            .setSecondary(a2.tone(40))
            .setOnSecondary(a2.tone(100))
            .setSecondaryContainer(a2.tone(90))
            .setOnSecondaryContainer(a2.tone(10))
            .setTertiary(a3.tone(40))
            .setOnTertiary(a3.tone(100))
            .setTertiaryContainer(a3.tone(90))
            .setOnTertiaryContainer(a3.tone(10))
            .setError(error.tone(40))
            .setOnError(error.tone(100))
            .setErrorContainer(error.tone(90))
            .setOnErrorContainer(error.tone(10))
            .setBackground(n1.tone(99))
            .setOnBackground(n1.tone(10))
            .setSurface(n1.tone(99))
            .setOnSurface(n1.tone(10))
            .setSurfaceVariant(n2.tone(90))
            .setOnSurfaceVariant(n2.tone(30))
            .setOutline(n2.tone(50))
            .setOutlineVariant(n2.tone(80))
            .setInverseSurface(n1.tone(20))
            .setInverseOnSurface(n1.tone(95))
            .setInversePrimary(a1.tone(80))
            .setScrim(n1.tone(0))
            .build()
    }
}