package de.mm20.launcher2.search

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import de.mm20.launcher2.base.R
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TintedIconLayer

interface AppShortcut : SavableSearchable {

    val appName: String?
    val componentName: ComponentName?
    val packageName: String?

    override val preferDetailsOverLaunch: Boolean
        get() = false

    override suspend fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        return StaticLauncherIcon(
            foregroundLayer = TintedIconLayer(
                color = 0xFF3DDA84.toInt(),
                icon = ContextCompat.getDrawable(context, R.drawable.ic_file_android)!!,
                scale = 0.65f,
            ),
            backgroundLayer = ColorLayer(0xFF3DDA84.toInt()),
        )
    }

    val canDelete: Boolean
        get() = false

    suspend fun delete(context: Context) {}

    val isUnavailable: Boolean
        get() = false

    val profile: AppProfile
}