package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.content.Intent.ShortcutIconResource
import android.graphics.drawable.AdaptiveIconDrawable
import android.util.Log
import de.mm20.launcher2.icons.*
import de.mm20.launcher2.ktx.getDrawableOrNull
import de.mm20.launcher2.ktx.isAtLeastApiLevel

class LegacyShortcut(
    val intent: Intent,
    override val label: String,
    appName: String?,
    val iconResource: ShortcutIconResource?,
) : AppShortcut(appName) {
    override val key: String
        get() = "legacyshortcut://${intent.toUri(0)}"

    override fun getLaunchIntent(context: Context): Intent {
        return intent
    }

    val packageName: String?
        get() = intent.`package` ?: intent.component?.packageName

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        if (iconResource == null) return null
        val resources = context.packageManager.getResourcesForApplication(iconResource.packageName)
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

    companion object {
        fun fromPinRequestIntent(context: Context, data: Intent): LegacyShortcut? {
            val intent: Intent? = data.extras?.getParcelable(Intent.EXTRA_SHORTCUT_INTENT)
            val name: String? = data.extras?.getString(Intent.EXTRA_SHORTCUT_NAME)
            val iconResource: ShortcutIconResource? =
                data.extras?.getParcelable(Intent.EXTRA_SHORTCUT_ICON_RESOURCE)

            if (intent == null || name == null) {
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