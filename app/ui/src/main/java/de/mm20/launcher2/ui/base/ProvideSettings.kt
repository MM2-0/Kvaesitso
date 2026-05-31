package de.mm20.launcher2.ui.base

import android.icu.util.Calendar
import android.icu.util.LocaleData
import android.icu.util.ULocale
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.IconShape
import de.mm20.launcher2.preferences.MeasurementSystem
import de.mm20.launcher2.preferences.TimeFormat
import de.mm20.launcher2.preferences.ui.GridSettings
import de.mm20.launcher2.preferences.ui.LocaleSettings
import de.mm20.launcher2.preferences.ui.SearchUiSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.ui.component.ProvideIconShape
import de.mm20.launcher2.ui.locals.LocalCalendarSystemIds
import de.mm20.launcher2.ui.locals.LocalCalendarSystems
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalHideAppDetails
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.locals.LocalMeasurementSystem
import de.mm20.launcher2.ui.locals.LocalTimeFormat
import de.mm20.launcher2.widgets.AppsWidget
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

@Composable
fun ProvideSettings(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val settings: UiSettings = koinInject()
    val searchUiSettings: SearchUiSettings = koinInject()
    val widgetRepository: WidgetRepository = koinInject()
    val localeSettings: LocaleSettings = koinInject()

    val iconShape by remember {
        settings.iconShape.distinctUntilChanged()
    }.collectAsState(IconShape.Circle)

    val favoritesEnabled by remember {
        combine(
            widgetRepository.exists(AppsWidget.Type),
            settings.favoritesEnabled,
        ) { a, b -> a || b }.distinctUntilChanged()
    }.collectAsState(true)

    val gridSettings by remember {
        settings.gridSettings.distinctUntilChanged()
    }.collectAsState(GridSettings())

    val hideAppDetails by remember {
        searchUiSettings.hideAppDetails.distinctUntilChanged()
    }.collectAsState(false)

    val timeFormat by remember(context) {
        localeSettings.timeFormat
            .map {
                if (it == TimeFormat.System) {
                    if (DateFormat.is24HourFormat(context)) TimeFormat.TwentyFourHour else TimeFormat.TwelveHour
                } else {
                    it
                }
            }.distinctUntilChanged()
    }.collectAsState(null)

    val measurementSystem by remember {
        localeSettings.measurementSystem.map { ms ->
            if (ms == MeasurementSystem.System) {
                return@map if (isAtLeastApiLevel(28)) {
                    val systemMs = LocaleData.getMeasurementSystem(ULocale.getDefault())
                    when (systemMs) {
                        LocaleData.MeasurementSystem.UK -> MeasurementSystem.UnitedKingdom
                        LocaleData.MeasurementSystem.US -> MeasurementSystem.UnitedStates
                        else -> MeasurementSystem.Metric
                    }
                } else {
                    MeasurementSystem.Metric
                }
            }
            return@map ms
        }.distinctUntilChanged()
    }.collectAsState(null)

    val calendarIds by remember {
        localeSettings.primaryCalendar.combine(localeSettings.secondaryCalendar) { p, s ->
            listOf(p, s)
        }
    }.collectAsState(listOf(null, null))

    val calendars by remember {
        derivedStateOf {
            val p = calendarIds.first()
            val s = calendarIds.last()
            val primary = if (p == null) {
                Calendar.getInstance(ULocale.getDefault())
            } else {
                Calendar.getInstance(ULocale.getDefault().setKeywordValue("calendar", p))
            }
            val secondary = if (s == null) {
                null
            } else {
                Calendar.getInstance(ULocale.getDefault().setKeywordValue("calendar", s))
            }
            listOfNotNull(
                primary, secondary
            )
        }
    }

    if (timeFormat == null || measurementSystem == null) return

    CompositionLocalProvider(
        LocalFavoritesEnabled provides favoritesEnabled,
        LocalHideAppDetails provides hideAppDetails,
        LocalGridSettings provides gridSettings,
        LocalTimeFormat provides timeFormat!!,
        LocalMeasurementSystem provides measurementSystem!!,
        LocalCalendarSystems provides calendars,
        LocalCalendarSystemIds provides calendarIds,
    ) {
        ProvideIconShape(iconShape) {
            content()
        }
    }

}