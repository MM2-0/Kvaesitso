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
import java.util.Locale

internal data class OsmLocation(
    val id: Long,
    override val label: String,
    override val category: LocationCategory?,
    override val latitude: Double,
    override val longitude: Double,
    override val street: String?,
    override val houseNumber: String?,
    override val openingHours: List<OpeningTime>?,
    override val websiteUrl: String?,
    override val preferDetailsOverLaunch: Boolean,
    override val labelOverride: String? = null,
) : Location {

    override val domain: String = DOMAIN

    override fun overrideLabel(label: String): OsmLocation {
        return this.copy(labelOverride = label)
    }

    override val key: String = "$domain://$id"

    override fun launch(context: Context, options: Bundle?): Boolean {
        return context.tryStartActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:$latitude,$longitude?q=${Uri.encode(label)}")
            ),
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
        fun fromOverpassResponse(
            result: OverpassResponse
        ): List<OsmLocation> = result.elements.mapNotNull {
            OsmLocation(
                id = it.id,
                label = it.tags["name"] ?: it.tags["brand"] ?: return@mapNotNull null,
                category = try {
                    it.tags["amenity"]?.let { LocationCategory.valueOf(it.uppercase(Locale.ROOT)) }
                } catch (_: Exception) {
                    LocationCategory.OTHER
                },
                latitude = it.lat,
                longitude = it.lon,
                street = it.tags["addr:street"],
                houseNumber = it.tags["addr:housenumber"],
                openingHours = it.tags["opening_hours"]?.let { OpeningTime.fromOverpassElement(it) },
                websiteUrl = it.tags["website"],
                preferDetailsOverLaunch = !it.tags.containsKey("website"),
            )
        }

        const val DOMAIN = "OpenStreetMaps"
    }
}