package de.mm20.launcher2.applock

import org.junit.Assert.*
import org.junit.Test

class VirtualAppTaskTest {
    private val contract = SettingsDeepLinkContract

    @Test fun independentlyOpenedAppsHaveDistinctTasks() {
        val routes = listOf(contract.ROUTE_FILES, contract.ROUTE_MEDIA, contract.ROUTE_VIEWER,
            contract.ROUTE_STORE, contract.ROUTE_NETWORK, contract.ROUTE_CALCULATOR,
            contract.ROUTE_VOICE_RECORDER, contract.ROUTE_SCREEN_RECORDER, contract.ROUTE_SCREENSHOT,
            contract.ROUTE_NOTES, contract.ROUTE_ERMIS, contract.ROUTE_CALENDAR, contract.ROUTE_DOWNLOADS,
            "settings/clock", "settings/akou", "settings/magnifier", "settings/flashlight",
            contract.ROUTE_WEATHER, contract.ROUTE_SYNC, contract.ROUTE_BACKUP,
            contract.ROUTE_KEYBOARD, contract.ROUTE_KEEPASS, contract.ROUTE_AETHER, contract.ROUTE_FIT)
        val ids = routes.map { contract.virtualAppTaskId(it) }
        assertFalse(ids.contains(null))
        assertEquals(routes.size, ids.toSet().size)
    }

    @Test fun keyboardKeepassAetherAndFitHaveStableAliases() {
        assertEquals("keyboard", contract.virtualAppTaskId(contract.ROUTE_KEYBOARD))
        assertEquals("keepass", contract.virtualAppTaskId(contract.ROUTE_KEEPASS))
        assertEquals("aether", contract.virtualAppTaskId(contract.ROUTE_AETHER))
        assertEquals("fit", contract.virtualAppTaskId(contract.ROUTE_FIT))
        assertEquals(
            "de.mm20.launcher2.ui.settings.TelosApp_keyboard",
            contract.virtualAppActivityClassName(contract.ROUTE_KEYBOARD),
        )
        assertEquals(
            "de.mm20.launcher2.ui.settings.TelosApp_fit",
            contract.virtualAppActivityClassName(contract.ROUTE_FIT),
        )
    }

    @Test fun recentsAliasesHaveStableIndependentComponents() {
        assertEquals("de.mm20.launcher2.ui.settings.TelosApp_ermis", contract.virtualAppActivityClassName(contract.ROUTE_ERMIS))
        assertNotEquals(contract.virtualAppActivityClassName(contract.ROUTE_FILES), contract.virtualAppActivityClassName(contract.ROUTE_ERMIS))
        assertEquals(contract.virtualAppActivityClassName(contract.ROUTE_MEDIA), contract.virtualAppActivityClassName(contract.ROUTE_RADIO))
        assertNull(contract.virtualAppActivityClassName("settings/unknown"))
    }

    @Test fun mediaAndViewerAliasesReuseTheirAppTask() {
        for (route in listOf(contract.ROUTE_MEDIA, contract.ROUTE_MUSIC, contract.ROUTE_RADIO, contract.ROUTE_VIDEO, contract.ROUTE_TV, contract.ROUTE_STREMIO))
            assertEquals("media", contract.virtualAppTaskId(route))
        assertEquals(contract.virtualAppTaskId(contract.ROUTE_VIEWER), contract.virtualAppTaskId(contract.ROUTE_PHOTOS))
    }

    @Test fun phoneTabsShareATaskButMessagesAreSeparate() {
        assertEquals("phone", contract.virtualAppTaskId(contract.ROUTE_COMMS))
        assertEquals("phone", contract.virtualAppTaskId(contract.ROUTE_COMMS, "contacts"))
        assertEquals("messages", contract.virtualAppTaskId(contract.ROUTE_COMMS, "messages"))
    }

    @Test fun settingsAndUnknownRoutesDoNotBecomeAppTasks() {
        for (route in listOf(null, "", "settings/wallpaper", contract.ROUTE_INTRUDER_PHOTOS))
            assertNull(contract.virtualAppTaskId(route))
    }
}
