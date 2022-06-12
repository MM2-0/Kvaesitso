package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.romanize
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.Settings.IconSettings.LegacyIconBackground
import de.mm20.launcher2.search.R
import java.text.Collator

abstract class Searchable : Comparable<Searchable> {

    abstract val key: String
    abstract val label: String

    open fun serialize(): String = ""

    open fun getLaunchIntent(context: Context): Intent? = null

    open fun launch(context: Context, options: Bundle?): Boolean {
        val intent = getLaunchIntent(context) ?: return false
        return if (context.tryStartActivity(intent, options)) {
            true
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.error_activity_not_found, label),
                Toast.LENGTH_SHORT
            ).show()
            false
        }
    }

    open suspend fun loadIcon(
        context: Context,
        size: Int,
        legacyIconBackground: LegacyIconBackground
    ): LauncherIcon? = null

    abstract fun getPlaceholderIcon(context: Context): LauncherIcon

    override fun compareTo(other: Searchable): Int {
        return Collator.getInstance().apply { strength = Collator.SECONDARY }
            .compare(label.romanize(), other.label.romanize())
    }

    override fun equals(other: Any?): Boolean {
        return if (other is Searchable) key == other.key
        else super.equals(other)
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + label.hashCode()
        return result
    }

    override fun toString(): String {
        return "$label ($key)"
    }
}