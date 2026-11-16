package de.mm20.launcher2.appmanagement.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryPolicyTest {
    @Test fun holdSetMergesRecommendedAndUserPackages() {
        val set = BatteryPolicy.holdSet("com.example.nav,  ", BatteryPolicy.RECOMMENDED_GPS)
        assertTrue(set.contains("com.google.android.apps.maps"))
        assertTrue(set.contains("com.example.nav"))
        assertEquals(BatteryPolicy.RECOMMENDED_GPS.size + 1, set.size)
    }
}
