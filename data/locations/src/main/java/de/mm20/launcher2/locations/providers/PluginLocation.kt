package de.mm20.launcher2.locations.providers

import android.content.Context
import android.graphics.drawable.Drawable
import de.mm20.launcher2.locations.PluginLocationSerializer
import de.mm20.launcher2.plugin.config.StorageStrategy
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.UpdatableSearchable
import de.mm20.launcher2.search.UpdateResult
import de.mm20.launcher2.search.location.Address
import de.mm20.launcher2.search.location.Attribution
import de.mm20.launcher2.search.location.Departure
import de.mm20.launcher2.search.location.LocationIcon
import de.mm20.launcher2.search.location.OpeningSchedule
import de.mm20.launcher2.search.location.PaymentMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PluginLocation(
    override val latitude: Double,
    override val longitude: Double,
    override val fixMeUrl: String?,
    override val icon: LocationIcon?,
    override val category: String?,
    override val address: Address?,
    override val openingSchedule: OpeningSchedule?,
    override val websiteUrl: String?,
    override val phoneNumber: String?,
    override val emailAddress: String?,
    override val userRating: Float?,
    override val userRatingCount: Int?,
    override val departures: List<Departure>?,
    override val label: String,
    override val timestamp: Long,
    override val attribution: Attribution?,
    override val acceptedPaymentMethods: Map<PaymentMethod, Boolean>?,
    override val updatedSelf: (suspend (SavableSearchable) -> UpdateResult<Location>)?,
    override val labelOverride: String? = null,
    val authority: String,
    val id: String,
    val storageStrategy: StorageStrategy,
) : Location, UpdatableSearchable<Location> {
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
            context.packageManager.resolveContentProvider(authority, 0)
                ?.loadIcon(context.packageManager)
        }
    }

    companion object {
        const val DOMAIN = "plugin.location"
    }
}