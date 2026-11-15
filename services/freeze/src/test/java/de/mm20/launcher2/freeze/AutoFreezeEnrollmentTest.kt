package de.mm20.launcher2.freeze

import de.mm20.launcher2.preferences.FrozenAppRestore
import org.junit.Assert.*
import org.junit.Test

class AutoFreezeEnrollmentTest {
    private fun app(pkg: String, user: Int = 0) =
        FrozenAppInventory(
            FrozenAppRestore(pkg, user, true, 0, false),
            FrozenAppGroup.User,
        )

    @Test
    fun inventoryFromOtherFreezersRequiresOnlyNewEligibleCurrentUserApps() {
        val inventory =
            listOf(
                app("com.new.app"),
                app("com.new.app"),
                app("com.existing.app"),
                app("com.never.app"),
                app("com.work.app", 10),
                app("com.telos"),
            )
        assertEquals(
            setOf("com.new.app"),
            autoFreezeImportCandidates(
                inventory,
                0,
                setOf("com.existing.app"),
                setOf("com.never.app"),
                "com.telos",
            ),
        )
    }

    @Test
    fun emptyOrAlreadyEnrolledInventoryNeedsNoPrompt() {
        assertTrue(
            autoFreezeImportCandidates(emptyList(), 0, emptySet(), emptySet(), "com.telos")
                .isEmpty()
        )
        assertTrue(
            autoFreezeImportCandidates(
                    listOf(app("com.existing.app")),
                    0,
                    setOf("com.existing.app"),
                    emptySet(),
                    "com.telos",
                )
                .isEmpty()
        )
    }
}
