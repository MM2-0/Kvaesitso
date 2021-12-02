package de.mm20.launcher2.icons.providers

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import de.mm20.launcher2.icons.CalendarDynamicLauncherIcon
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.obtainTypedArrayOrNull
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.data.Application
import de.mm20.launcher2.search.data.Searchable

class CalendarIconProvider(val context: Context): IconProvider {
    override suspend fun getIcon(searchable: Searchable, size: Int): LauncherIcon? {
        if(searchable !is Application) return null
        val component = ComponentName(searchable.`package`, searchable.activity)
        val pm = context.packageManager
        val ai = try {
            pm.getActivityInfo(component, PackageManager.GET_META_DATA)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        val resources = pm.getResourcesForActivity(component)
        var arrayId = ai.metaData?.getInt("com.teslacoilsw.launcher.calendarIconArray") ?: 0
        if (arrayId == 0) arrayId = ai.metaData?.getInt("com.google.android.calendar.dynamic_icons")
            ?: return null
        if (arrayId == 0) return null
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
        return CalendarDynamicLauncherIcon(
            context = context,
            background = ColorDrawable(0),
            foreground = ColorDrawable(0),
            foregroundScale = 1.5f,
            backgroundScale = 1.5f,
            packageName = component.packageName,
            drawableIds = drawableIds,
            autoGenerateBackgroundMode = LauncherPreferences.instance.legacyIconBg.toInt()
        )
    }
}