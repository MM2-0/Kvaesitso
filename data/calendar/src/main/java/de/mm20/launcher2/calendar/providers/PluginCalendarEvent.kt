package de.mm20.launcher2.calendar.providers

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import de.mm20.launcher2.calendar.PluginCalendarEventSerializer
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.plugin.config.StorageStrategy
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.UpdatableSearchable
import de.mm20.launcher2.search.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PluginCalendarEvent(
    val id: String,
    val authority: String,
    override val color: Int?,
    override val startTime: Long?,
    override val endTime: Long,
    override val allDay: Boolean,
    override val description: String?,
    override val calendarName: String?,
    override val location: String?,
    override val attendees: List<String>,
    val uri: Uri,
    override val isCompleted: Boolean?,
    override val label: String,
    override val labelOverride: String? = null,
    override val timestamp: Long,
    internal val storageStrategy: StorageStrategy,
    override val updatedSelf: (suspend (SavableSearchable) -> UpdateResult<CalendarEvent>)?,
) : CalendarEvent, UpdatableSearchable<CalendarEvent> {
    override val domain: String = Domain

    override val key: String
        get() = "$domain://$authority:$id"

    override fun overrideLabel(label: String): SavableSearchable {
        return copy(
            labelOverride = label
        )
    }

    override fun launch(context: Context, options: Bundle?): Boolean {
        return context.tryStartActivity(
            Intent(
                Intent.ACTION_VIEW
            ).apply {
                data = uri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }, options
        )
    }

    override fun getSerializer(): SearchableSerializer {
        return PluginCalendarEventSerializer()
    }

    override suspend fun getProviderIcon(context: Context): Drawable? {
        return withContext(Dispatchers.IO) {
            context.packageManager.resolveContentProvider(authority, 0)?.loadIcon(context.packageManager)
        }
    }

    companion object {
        const val Domain = "plugin.calendar"
    }
}