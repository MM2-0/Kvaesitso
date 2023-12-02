package de.mm20.launcher2.search

import android.content.Context
import android.os.Bundle
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.ktx.romanize
import java.text.Collator

interface SavableSearchable : Searchable, Comparable<SavableSearchable>  {
    val key: String

    val label: String
    val labelOverride: String?
        get() = null

    fun overrideLabel(label: String): SavableSearchable

    fun launch(context: Context, options: Bundle?): Boolean

    /**
     * If this is true, tapping the item will open the details popup instead of launching it
     */
    val preferDetailsOverLaunch: Boolean

    fun getPlaceholderIcon(context: Context): StaticLauncherIcon

    suspend fun loadIcon(
        context: Context,
        size: Int,
        themed: Boolean
    ): LauncherIcon? = null


    override fun compareTo(other: SavableSearchable): Int {
        val label1 = labelOverride ?: label
        val label2 = other.labelOverride ?: other.label
        return Collator.getInstance().apply { strength = Collator.SECONDARY }
            .compare(label1.romanize(), label2.romanize())
    }

    val domain: String
    fun getSerializer(): SearchableSerializer

    interface Companion {
        val Domain: String
    }

}