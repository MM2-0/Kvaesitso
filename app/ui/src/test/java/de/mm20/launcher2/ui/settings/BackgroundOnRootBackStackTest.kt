package de.mm20.launcher2.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundOnRootBackStackTest {
    @Test
    fun childBackPopsButRootBackPreservesSameEntryForRecents() {
        val mediaTv = Any()
        val entries = mutableListOf(mediaTv, Any())
        var backgrounded = 0
        val navigation = BackgroundOnRootBackStack(entries) { backgrounded++ }

        navigation.removeLastOrNull()
        assertEquals(0, backgrounded)
        assertEquals(listOf(mediaTv), entries)
        repeat(3) { navigation.removeLastOrNull() }
        assertEquals(3, backgrounded)
        assertEquals(listOf(mediaTv), entries)
    }

    @Test
    fun explicitRouteReplacementStillWorks() {
        val entries = mutableListOf("media")
        var backgrounded = false
        val navigation = BackgroundOnRootBackStack(entries) { backgrounded = true }
        navigation.clear()
        navigation.add("television")
        navigation.add("settings")
        navigation.removeLastOrNull()
        assertEquals(listOf("television"), entries)
        assertEquals(false, backgrounded)
    }
}
