package de.mm20.launcher2.locations.providers

import android.content.Context
import android.graphics.drawable.Drawable
import de.mm20.launcher2.locations.PluginLocationSerializer
import de.mm20.launcher2.search.Departure
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.LocationCategory
import de.mm20.launcher2.search.OpeningSchedule
import de.mm20.launcher2.search.SearchableSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PluginLocation(
    override val latitude: Double,
    override val longitude: Double,
    override val fixMeUrl: String?,
    override val category: LocationCategory?,
    override val street: String?,
    override val houseNumber: String?,
    override val openingSchedule: OpeningSchedule?,
    override val websiteUrl: String?,
    override val phoneNumber: String?,
    override val userRating: Float?,
    override val departures: List<Departure>?,
    override val label: String,
    override val labelOverride: String? = null,
    val authority: String,
    val id: String,
): Location {
    override val key: String
        get() = "$domain://$authority:$id"

    override fun overrideLabel(label: String): PluginLocation {
        return this.copy(labelOverride = label)
    }

    override val domain: String = DOMAIN

    override fun getSerializer(): SearchableSerializer {
        return PluginLocationSerializer()
    }

    override suspend fun getProviderIcon(context: Context): Drawable? {
        return withContext(Dispatchers.IO) {
            context.packageManager.resolveContentProvider(authority, 0)?.loadIcon(context.packageManager)
        }
    }

    companion object {
        const val DOMAIN = "plugin.location"
    }
}