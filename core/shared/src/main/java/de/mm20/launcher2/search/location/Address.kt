package de.mm20.launcher2.search.location

import kotlinx.serialization.Serializable

@Serializable
data class Address(
    /**
     * Address line 1 (e.g. street name and number)
     */
    val address: String? = null,
    /**
     * Address line 2 (e.g. apartment number)
     */
    val address2: String? = null,
    /**
     * Address line 3 (e.g. additional information)
     */
    val address3: String? = null,
    /**
     * City
     */
    val city: String? = null,
    /**
     * State, province, or region
     */
    val state: String? = null,
    /**
     * Postal code
     */
    val postalCode: String? = null,
    /**
     * Country
     */
    val country: String? = null,
)