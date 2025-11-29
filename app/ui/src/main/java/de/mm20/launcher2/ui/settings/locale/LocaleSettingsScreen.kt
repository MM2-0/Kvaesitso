package de.mm20.launcher2.ui.settings.locale

import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.app.GrammaticalInflectionManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.MeasurementSystem
import de.mm20.launcher2.preferences.TimeFormat
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable

@Serializable
data object LocaleSettingsRoute : NavKey

@Composable
fun LocaleSettingsScreen() {
    val context = LocalContext.current
    val viewModel: LocaleSettingsScreenVM = viewModel()

    val timeFormat by viewModel.timeFormat.collectAsStateWithLifecycle(null)
    val measurementSystem by viewModel.measurementSystem.collectAsStateWithLifecycle(null)

    // The language that has been selected by the user, or null to use the system language
    val selectedLocale = remember {
        AppCompatDelegate.getApplicationLocales().get(0)
    }

    // The current language, including the resolved system language
    val currentLocale = LocalResources.current.configuration?.locales[0]


    PreferenceScreen(
        title = stringResource(R.string.preference_screen_locale)
    ) {
        item {
            PreferenceCategory {
                Preference(
                    icon = R.drawable.flag_24px,
                    title = stringResource(R.string.preference_language),
                    summary = if (selectedLocale == null) {
                        stringResource(R.string.preference_value_system_default)
                    } else {
                        selectedLocale.getDisplayName(selectedLocale)
                            .replaceFirstChar { it.uppercase(selectedLocale) }
                    },
                    enabled = isAtLeastApiLevel(33),
                    onClick = {
                        context.tryStartActivity(
                            Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                                setData("package:${context.packageName}".toUri())
                            }
                        )
                    }
                )
                if (listOf("fr").contains(currentLocale?.language)) {
                    ListPreference(
                        icon = R.drawable.wc_24px,
                        title = stringResource(R.string.preference_form_of_address),
                        value = GrammaticalInflectionManagerCompat.getApplicationGrammaticalGender(
                            context
                        ).let {
                            if (it == GrammaticalInflectionManagerCompat.GRAMMATICAL_GENDER_NOT_SPECIFIED) {
                                GrammaticalInflectionManagerCompat.GRAMMATICAL_GENDER_NEUTRAL
                            } else {
                                it
                            }
                        },
                        onValueChanged = {
                            GrammaticalInflectionManagerCompat.setRequestedApplicationGrammaticalGender(
                                context,
                                it
                            )
                        },
                        enabled = isAtLeastApiLevel(34),
                        items = listOf(
                            stringResource(R.string.preference_form_of_address_neutral) to GrammaticalInflectionManagerCompat.GRAMMATICAL_GENDER_NEUTRAL,
                            stringResource(R.string.preference_form_of_address_fem) to GrammaticalInflectionManagerCompat.GRAMMATICAL_GENDER_FEMININE,
                            stringResource(R.string.preference_form_of_address_masc) to GrammaticalInflectionManagerCompat.GRAMMATICAL_GENDER_MASCULINE,
                        )
                    )
                }
            }
        }
        item {
            PreferenceCategory {
                ListPreference(
                    icon = R.drawable.schedule_24px,
                    title = stringResource(R.string.preference_clock_widget_time_format),
                    value = timeFormat,
                    onValueChanged = {
                        if (it != null) viewModel.setTimeFormat(it)
                    },
                    items = listOf(
                        stringResource(R.string.preference_value_system_default) to TimeFormat.System,
                        stringResource(R.string.preference_clock_widget_time_format_12h) to TimeFormat.TwelveHour,
                        stringResource(R.string.preference_clock_widget_time_format_24h) to TimeFormat.TwentyFourHour,
                    )
                )
                ListPreference(
                    icon = R.drawable.measuring_tape_24px,
                    title = stringResource(R.string.preference_measurement_system),
                    value = measurementSystem,
                    onValueChanged = {
                        if (it != null) viewModel.setMeasurementSystem(it)
                    },
                    items = listOf(
                        stringResource(R.string.preference_value_system_default) to MeasurementSystem.System,
                        stringResource(R.string.preference_measurement_system_metric) to MeasurementSystem.Metric,
                        stringResource(R.string.preference_measurement_system_uk) to MeasurementSystem.UnitedKingdom,
                        stringResource(R.string.preference_measurement_system_us) to MeasurementSystem.UnitedStates,
                    )
                )
            }
        }
    }
}