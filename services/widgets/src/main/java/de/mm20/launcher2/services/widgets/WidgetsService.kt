package de.mm20.launcher2.services.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import androidx.core.content.getSystemService
import de.mm20.launcher2.widgets.CalendarWidget
import de.mm20.launcher2.widgets.FavoritesWidget
import de.mm20.launcher2.widgets.MusicWidget
import de.mm20.launcher2.widgets.NotesWidget
import de.mm20.launcher2.widgets.WeatherWidget
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class WidgetsService(
    private val context: Context,
    private val widgetRepository: WidgetRepository,
) {
    suspend fun getAppWidgetProviders(): List<AppWidgetProviderInfo> = withContext(Dispatchers.IO) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val launcherApps =
            context.getSystemService<LauncherApps>() ?: return@withContext emptyList()
        val profiles = launcherApps.profiles
        val widgets = mutableListOf<AppWidgetProviderInfo>()
        for (profile in profiles) {
            widgets.addAll(appWidgetManager.getInstalledProvidersForProfile(profile))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Ignore widgets that the launcher is not supposed to access
            widgets.filter {
                it.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0
            }
        } else {
            widgets
        }
    }

    fun getAvailableBuiltInWidgets(): Flow<List<BuiltInWidgetInfo>> {
        return flowOf(getBuiltInWidgets())
    }

    fun getBuiltInWidgets(): List<BuiltInWidgetInfo> {
        return listOf(
            BuiltInWidgetInfo(
                type = WeatherWidget.Type,
                label = context.getString(R.string.widget_name_weather),
            ),
            BuiltInWidgetInfo(
                type = MusicWidget.Type,
                label = context.getString(R.string.widget_name_music),
            ),
            BuiltInWidgetInfo(
                type = CalendarWidget.Type,
                label = context.getString(R.string.widget_name_calendar),
            ),
            BuiltInWidgetInfo(
                type = FavoritesWidget.Type,
                label = context.getString(R.string.widget_name_favorites),
            ),
            BuiltInWidgetInfo(
                type = NotesWidget.Type,
                label = context.getString(R.string.widget_name_notes),
            ),
        )
    }

    fun addWidget(widget: Widget, position: Int, parentId: UUID? = null) {
        widgetRepository.create(widget, position, parentId)
    }

    fun updateWidget(widget: Widget) {
        widgetRepository.update(widget)
    }

    fun getWidgets() = widgetRepository.get()

    fun isFavoritesWidgetFirst(): Flow<Boolean> {
        return widgetRepository.get(limit = 1).map {
            it.firstOrNull() is FavoritesWidget
        }
    }

    fun countWidgets(type: String) = widgetRepository.count(type)

    fun removeWidget(widget: Widget) {
        widgetRepository.delete(widget)
    }

    companion object {
        const val AppWidgetHostId = 44203
    }
}