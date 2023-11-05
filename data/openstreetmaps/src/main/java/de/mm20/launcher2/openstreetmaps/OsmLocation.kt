package de.mm20.launcher2.openstreetmaps

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.LocationCategory
import de.mm20.launcher2.search.OpeningTime
import de.mm20.launcher2.search.SearchableSerializer

internal data class OsmLocation(
    override val label: String,
    override val category: LocationCategory?,
    override val latitude: Double,
    override val longitude: Double,
    override val street: String?,
    override val houseNumber: String?,
    override val openingHours: List<OpeningTime>?,
    override val preferDetailsOverLaunch: Boolean,
    override val labelOverride: String? = null,
) : Location {

    override val domain: String = DOMAIN

    override fun overrideLabel(label: String): OsmLocation {
        return this.copy(labelOverride = label)
    }

    override val key: String = "$domain://$latitude:$longitude"

    override fun launch(context: Context, options: Bundle?): Boolean {
        return context.tryStartActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:$latitude,$longitude")),
            options
        )
    }

    override fun getPlaceholderIcon(context: Context): Nothing {
        TODO()
    }

    override fun getSerializer(): SearchableSerializer {
        return OsmLocationSerializer()
    }

    companion object {
        const val DOMAIN = "OpenStreetMaps"
    }
}