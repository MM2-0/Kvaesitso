package de.mm20.launcher2.ui.settings.locale

import android.content.Intent
import android.icu.text.Transliterator
import android.icu.util.ULocale
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.text.toUpperCase
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
import java.util.Locale

@Serializable
data object LocaleSettingsRoute : NavKey

@Composable
fun LocaleSettingsScreen() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val viewModel: LocaleSettingsScreenVM = viewModel()

    val timeFormat by viewModel.timeFormat.collectAsStateWithLifecycle(null)
    val measurementSystem by viewModel.measurementSystem.collectAsStateWithLifecycle(null)
    val transliterator by viewModel.transliterator.collectAsStateWithLifecycle(null)

    // The language that has been selected by the user, or null to use the system language
    val selectedLocale = remember {
        AppCompatDelegate.getApplicationLocales().get(0)
    }

    val locales = LocalResources.current.configuration?.locales

    // The current language, including the resolved system language
    val currentLocale = locales?.get(0)

    val transliterators: List<Pair<String, String?>> = remember(locales) {
        if (!isAtLeastApiLevel(29)) return@remember listOf()

        if (locales?.isEmpty == true) return@remember listOf(resources.getString(R.string.preference_transliteration_disabled) to null)

        val scripts = mutableSetOf<String>()
        val languages = mutableSetOf<String>()

        val transliterators = mutableMapOf<String?, String>(
            null to resources.getString(R.string.preference_transliteration_disabled),
            "" to resources.getString(R.string.preference_transliteration_auto),
        )

        val availableIds = Transliterator.getAvailableIDs().toList()

        for (i in 0..<locales!!.size()) {
            val locale = locales.get(i)
            val ulocale = ULocale.addLikelySubtags(ULocale.forLocale(locale))

            val lng = ulocale.language
            val scr = ulocale.script

            if (!languages.contains(lng)) {
                val filter = "${lng}-${lng}_Latn"

                val ids = availableIds.filter { it.startsWith(filter) }

                for (id in ids) {
                    transliterators[id] = "${ulocale.displayLanguage.replaceFirstChar { ulocale.displayLanguage.first().uppercase() }} ($id)"
                }

                languages.add(lng)
            }

            if (!scripts.contains(ulocale.script)) {
                val filter = "${scr}-Latn"

                val ids = availableIds.filter { it.startsWith(filter) }

                for (id in ids) {
                    transliterators[id] = "${ulocale.displayScript.replaceFirstChar { ulocale.displayScript.first().uppercase() }} ($id)"
                }
                scripts.add(ulocale.script)
            }
        }

        transliterators.map { it.value to it.key }
    }



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
                if (isAtLeastApiLevel(29) && transliterators.size > 2) {
                    ListPreference(
                        icon = R.drawable.translate_24px,
                        title = stringResource(R.string.preference_transliteration),
                        items = transliterators,
                        value = transliterator,
                        onValueChanged = {
                            viewModel.setTransliterator(it)
                        },
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