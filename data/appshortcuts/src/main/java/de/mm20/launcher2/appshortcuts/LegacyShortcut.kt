package de.mm20.launcher2.appshortcuts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ShortcutIconResource
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Bundle
import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.icons.*
import de.mm20.launcher2.ktx.getDrawableOrNull
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.AppShortcut
import de.mm20.launcher2.search.SearchableSerializer

internal data class LegacyShortcut(
    val intent: Intent,
    override val label: String,
    override val appName: String?,
    val iconResource: ShortcutIconResource?,
    override val labelOverride: String? = null,
) : AppShortcut {

    override val domain = Domain
    override val key: String = "$domain://${intent.toUri(0)}"

    override fun overrideLabel(label: String): LegacyShortcut {
        return this.copy(labelOverride = label)
    }


    override fun launch(context: Context, options: Bundle?): Boolean {
        return context.tryStartActivity(intent, options)
    }

    override val componentName: ComponentName?
        get() = intent.component

    override val packageName: String?
        get() = intent.`package` ?: intent.component?.packageName

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        if (iconResource == null) return null
        val resources = try {
            context.packageManager.getResourcesForApplication(iconResource.packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            CrashReporter.logException(e)
            return null
        }
        val drawableId =
            resources.getIdentifier(iconResource.resourceName, "drawable", iconResource.packageName)
        if (drawableId == 0) return null
        val icon = resources.getDrawableOrNull(drawableId) ?: return null
        if (icon is AdaptiveIconDrawable) {
            if (themed && isAtLeastApiLevel(33) && icon.monochrome != null) {
                return StaticLauncherIcon(
                    foregroundLayer = TintedIconLayer(
                        scale = 1f,
                        icon = icon.monochrome!!,
                    ),
                    backgroundLayer = ColorLayer()
                )
            }
            return StaticLauncherIcon(
                foregroundLayer = icon.foreground?.let {
                    StaticIconLayer(
                        icon = it,
                        scale = 1.5f,
                    )
                } ?: TransparentLayer,
                backgroundLayer = icon.background?.let {
                    StaticIconLayer(
                        icon = it,
                        scale = 1.5f,
                    )
                } ?: TransparentLayer,
            )
        }
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(
                icon = icon,
                scale = 1f
            ),
            backgroundLayer = TransparentLayer
        )
    }

    override fun getSerializer(): SearchableSerializer {
        return LegacyShortcutSerializer()
    }

    companion object {

        const val Domain = "legacyshortcut"

        fun fromPinRequestIntent(context: Context, data: Intent): LegacyShortcut? {
            val intent: Intent? = data.extras?.getParcelable(Intent.EXTRA_SHORTCUT_INTENT)
            val name: String? = data.extras?.getString(Intent.EXTRA_SHORTCUT_NAME)
            val iconResource: ShortcutIconResource? =
                data.extras?.getParcelable(Intent.EXTRA_SHORTCUT_ICON_RESOURCE)

            if (intent == null || name == null) {
                Log.w("MM20", "Pin request intent is missing required extras: intent=$intent, name=$name")
                return null
            }

            val packageName = intent.`package` ?: intent.component?.packageName

            return LegacyShortcut(
                intent = intent,
                appName = packageName?.let {
                    context.packageManager.getApplicationInfo(
                        it, 0
                    ).loadLabel(context.packageManager).toString()
                },
                label = name,
                iconResource = iconResource
            )
        }
    }
}