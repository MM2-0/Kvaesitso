package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import de.mm20.launcher2.appshortcuts.R
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TintedIconLayer
import de.mm20.launcher2.search.SavableSearchable

interface AppShortcut: SavableSearchable {

    val appName: String?

    override val preferDetailsOverLaunch: Boolean
        get() = false

    override fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        return StaticLauncherIcon(
            foregroundLayer = TintedIconLayer(
                color = 0xFF3DDA84.toInt(),
                icon = ContextCompat.getDrawable(context, R.drawable.ic_file_android)!!,
                scale = 0.65f,
            ),
            backgroundLayer = ColorLayer(0xFF3DDA84.toInt()),
        )
    }

    companion object {
        fun fromPinRequestIntent(context: Context, data: Intent): AppShortcut? {
            return LauncherShortcut.fromPinRequestIntent(context, data)
                ?: LegacyShortcut.fromPinRequestIntent(context, data)
        }
    }
}