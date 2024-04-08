package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import de.mm20.launcher2.base.R
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TintedIconLayer
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.NullSerializer
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer

data class PojoSettings(
    @get:StringRes val titleForPage: Int,
    @get:DrawableRes val icon: Int,

    val actionId: String? = null,
    val specialId: String? = null,

    override val key: String,
    override val label: String
) : SavableSearchable {
    override fun overrideLabel(label: String): SavableSearchable {
        return copy(label = label)
    }

    override fun launch(context: Context, options: Bundle?): Boolean {
        return when (specialId) {
            specialIdLauncher -> {
                true
            }

            else -> context.tryStartActivity(
                Intent(actionId).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }, options
            )
        }
    }

    override val preferDetailsOverLaunch: Boolean
        get() = false

    override val domain: String
        get() = Domain

    override fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        val bgColor = R.color.teal
        return StaticLauncherIcon(
            foregroundLayer = TintedIconLayer(
                icon = ContextCompat.getDrawable(context, R.drawable.ic_file_code)!!,
                scale = 0.5f,
                color = ContextCompat.getColor(context, bgColor)
            ),
            backgroundLayer = ColorLayer(ContextCompat.getColor(context, bgColor))
        )
    }

    override fun getSerializer(): SearchableSerializer {
        return NullSerializer()
    }

    companion object {
        const val Domain: String = "settings"

        const val specialIdLauncher = "launcher-settings"
    }
}