package de.mm20.launcher2.search.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.R
import java.text.Collator

abstract class Searchable : Comparable<Searchable> {

    abstract val key: String
    abstract val label: String

    open val badgeKey
        get() = key

    open fun serialize(): String = ""

    open fun getLaunchIntent(context: Context): Intent? = null

    open fun launch(context: Context, options: Bundle?): Boolean {
        val intent = getLaunchIntent(context) ?: return false
        return try {
            context.startActivity(intent, options)
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.activity_not_found_searchable, label), Toast.LENGTH_SHORT).show()
            false
        }
    }

    open suspend fun loadIconAsync(context: Context, size: Int): LauncherIcon? = null
    abstract fun getPlaceholderIcon(context: Context): LauncherIcon

    override fun compareTo(other: Searchable): Int {
        return Collator.getInstance().apply { strength = Collator.SECONDARY }.compare(label, other.label)
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