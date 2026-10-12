package org.woheller69.androidaddressformatter

import de.mm20.launcher2.search.location.Address
import org.junit.Assert.assertEquals
import org.junit.Test

class OsmAddressFormatterTest {

    private val formatter = OsmAddressFormatter(false, false, false)

    @Test
    fun formatsUnitedKingdomAddressExactly() {
        val address = Address(address = "Baker Street", address2 = "221B", city = "London", postalCode = "NW1 6XE")

        val rendered = formatter.format(address, "GB")

        assertEquals("221B Baker Street\nLondon\nNW1 6XE\n", rendered)
    }

    @Test
    fun formatsUnitedStatesAddressExactly() {
        val address = Address(
            address = "Pennsylvania Avenue NW",
            address2 = "1600",
            city = "Washington",
            state = "District of Columbia",
            postalCode = "20500"
        )

        val rendered = formatter.format(address, "US")

        assertEquals("1600 Pennsylvania Avenue NW\nWashington, DC 20500\n", rendered)
    }

    @Test
    fun formatsGermanAddressExactly() {
        val address = Address(address = "Musterstrasse", address2 = "1", postalCode = "10115", city = "Berlin")

        val rendered = formatter.format(address, "DE")

        assertEquals("Musterstrasse 1\n10115 Berlin\n", rendered)
    }

    @Test
    fun formatsJapaneseAddressExactly() {
        val address = Address(
            address = "Chiyoda",
            address2 = "1-1",
            postalCode = "100-0001",
            state = "Tokyo",
            city = "Chiyoda"
        )

        val rendered = formatter.format(address, "JP")

        assertEquals("1-1 Chiyoda\nChiyoda, Tokyo 100-0001\n", rendered)
    }

    @Test
    fun formatsSouthAfricanAddressExactly() {
        val address = Address(address = "Long Street", address2 = "1", city = "Cape Town", postalCode = "8001")

        val rendered = formatter.format(address, "ZA")

        assertEquals("1 Long Street\nCape Town\n8001\n", rendered)
    }

    @Test
    fun formatsBrazilianAddressExactly() {
        val address = Address(
            address = "Rua das Flores",
            address2 = "123",
            city = "Sao Paulo",
            state = "SP",
            postalCode = "01234567"
        )

        val rendered = formatter.format(address, "BR")

        assertEquals("Rua das Flores 123\nSao Paulo - SP\n01234-567\n", rendered)
    }

    @Test
    fun formatsIncompleteUnitedStatesAddressExactly() {
        val address = Address(city = "Washington", state = "District of Columbia")

        val rendered = formatter.format(address, "US")

        assertEquals("Washington, District of Columbia\n", rendered)
    }

    @Test
    fun formatsIncompleteUnitedKingdomAddressExactly() {
        val address = Address(city = "London")

        val rendered = formatter.format(address, "GB")

        assertEquals("London\n", rendered)
    }

    @Test
    fun formatsIncompleteBrazilianAddressExactly() {
        val address = Address(city = "Sao Paulo", state = "SP")

        val rendered = formatter.format(address, "BR")

        assertEquals("Sao Paulo\nSP\n", rendered)
    }
}


