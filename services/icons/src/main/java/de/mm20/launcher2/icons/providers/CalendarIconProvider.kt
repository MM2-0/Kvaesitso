package de.mm20.launcher2.icons.providers

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import de.mm20.launcher2.icons.DynamicCalendarIcon
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.obtainTypedArrayOrNull
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.data.LauncherApp

class CalendarIconProvider(val context: Context, val themed: Boolean): IconProvider {
    override suspend fun getIcon(searchable: SavableSearchable, size: Int): LauncherIcon? {
        if(searchable !is LauncherApp) return null
        val component = ComponentName(searchable.`package`, searchable.activity)
        val pm = context.packageManager
        val ai = try {
            pm.getActivityInfo(component, PackageManager.GET_META_DATA)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        var arrayId = ai.metaData?.getInt("com.teslacoilsw.launcher.calendarIconArray") ?: 0
        if (arrayId == 0) arrayId = ai.metaData?.getInt("com.google.android.calendar.dynamic_icons")
            ?: return null
        if (arrayId == 0) return null
        val resources = try {
            pm.getResourcesForActivity(component)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        val typedArray = resources.obtainTypedArrayOrNull(arrayId) ?: return null
        if (typedArray.length() != 31) {
            typedArray.recycle()
            return null
        }
        val drawableIds = IntArray(31)
        for (i in 0 until 31) {
            drawableIds[i] = typedArray.getResourceId(i, 0)
        }
        typedArray.recycle()
        return DynamicCalendarIcon(
            resources = resources,
            resourceIds = drawableIds,
            isThemed = themed
        )
    }
}