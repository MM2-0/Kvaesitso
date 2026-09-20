package de.mm20.launcher2.ui.settings.locale

import android.icu.text.ListFormatter
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.preferences.MeasurementSystem
import de.mm20.launcher2.preferences.ui.LocaleSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data object MeasurementSystemSettingsRoute : NavKey

@Composable
fun MeasurementSystemSettingsScreen() {
    val localeSettings = koinInject<LocaleSettings>()
    val measurementSystem by localeSettings.measurementSystem.collectAsStateWithLifecycle(null)

    val items = listOf<MeasurementSystemItem>(
        MeasurementSystemItem(MeasurementSystem.System, R.string.preference_value_system_default),
        MeasurementSystemItem(
            MeasurementSystem.Metric,
            R.string.preference_measurement_system_metric,
            listOf(
                R.string.unit_degree_celsius_symbol,
                R.string.unit_millimeter_symbol,
                R.string.unit_kilometer_per_hour_symbol,
                R.string.unit_meter_symbol,
                R.string.unit_kilometer_symbol,
            ),
        ),
        MeasurementSystemItem(
            MeasurementSystem.MetricScientific,
            R.string.preference_measurement_system_metric_scientific,
            listOf(
                R.string.unit_degree_celsius_symbol,
                R.string.unit_millimeter_symbol,
                R.string.unit_meter_per_second_symbol,
                R.string.unit_meter_symbol,
                R.string.unit_kilometer_symbol,
            ),
        ),
        MeasurementSystemItem(
            MeasurementSystem.UnitedKingdom,
            R.string.preference_measurement_system_uk,
            listOf(
                R.string.unit_degree_celsius_symbol,
                R.string.unit_millimeter_symbol,
                R.string.unit_mile_per_hour_symbol,
                R.string.unit_yard_symbol,
                R.string.unit_mile_symbol,
            ),
        ),
        MeasurementSystemItem(
            MeasurementSystem.UnitedStates,
            R.string.preference_measurement_system_us,
            listOf(
                R.string.unit_degree_fahrenheit_symbol,
                R.string.unit_inch_symbol,
                R.string.unit_mile_per_hour_symbol,
                R.string.unit_foot_symbol,
                R.string.unit_mile_symbol,
            ),
        )
    )

    PreferenceScreen(
        title = { Text(stringResource(R.string.preference_measurement_system)) }
    ) {
        item {
            PreferenceCategory {
                for (item in items) {
                    val selected = measurementSystem == item.measurementSystem
                    Preference(
                        title = { Text(stringResource(item.title)) },
                        icon = {
                            Icon(
                                painterResource(
                                    if (selected) R.drawable.radio_button_checked_24px
                                    else R.drawable.radio_button_unchecked_24px,
                                ),
                                null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = {
                            localeSettings.setMeasurementSystem(item.measurementSystem)
                        },
                        summary = if (item.previewUnits == null) null else {
                            {
                                val formatter = ListFormatter.getInstance()
                                Text(
                                    text = formatter.format(
                                        item.previewUnits.map { stringResource(it) }
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

private data class MeasurementSystemItem(
    val measurementSystem: MeasurementSystem,
    @StringRes val title: Int,
    @StringRes val previewUnits: List<Int>? = null
)