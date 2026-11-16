/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.model.preferences

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppPreferenceStoresTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun stores() = AppPreferenceStores(rootFilesDir = { folder.root.toOkioPath() })

    /**
     * The load-bearing assumption of the whole move.
     *
     * Android's `Context.preferencesDataStore(name = "x")` resolves to
     * `filesDir/datastore/x.preferences_pb`. A store taken off that delegate
     * and put on this holder has to land on the same file, or every existing
     * install silently starts from empty settings. This pins the shape.
     */
    @Test
    fun theFilePathMatchesTheAndroidDelegate() {
        val root = folder.root.toOkioPath()

        assertEquals(root / "datastore" / "shared_settings.preferences_pb", stores().file("shared_settings"))
        assertEquals(root / "datastore" / "favorite_apps.preferences_pb", stores().file("favorite_apps"))
    }

    /** DataStore refuses a second live store on one path, so this must dedupe. */
    @Test
    fun oneStorePerFile() {
        val subject = stores()

        assertSame(subject.getDataStore("shared_settings"), subject.getDataStore("shared_settings"))
        assertSame(subject.sharedSettings(), subject.getDataStore("shared_settings"))
    }

    @Test
    fun differentNamesAreDifferentStores() =
        runTest {
            val subject = stores()
            val key = stringPreferencesKey("k")

            subject.getDataStore("one").edit { it[key] = "first" }
            subject.getDataStore("two").edit { it[key] = "second" }

            assertEquals("first", subject.getDataStore("one").data.first()[key])
            assertEquals("second", subject.getDataStore("two").data.first()[key])
        }

    @Test
    fun writesLandInTheExpectedFile() =
        runTest {
            val subject = stores()
            subject.sharedSettings().edit { it[stringPreferencesKey("ui.theme")] = "DARK" }

            val expected = java.io.File(folder.root, "datastore/shared_settings.preferences_pb")
            assertTrue(expected.absolutePath, expected.exists())
        }

    /**
     * Migrations are chosen per file name, and the name reaches the chooser.
     *
     * Both users of this depend on it: shared_settings takes the UI copy, and
     * the Cashu counters take a different migration per account because they
     * are one file per npub. A holder that ignored the name, or applied one
     * file's migration to another, would copy an account's counters into
     * someone else's — and a counter that moves backwards costs real ecash.
     */
    @Test
    fun migrationsAreChosenPerFileNameAndTheNameIsPassedThrough() =
        runTest {
            val asked = mutableListOf<String>()
            val marker = stringPreferencesKey("from")

            val subject =
                AppPreferenceStores(
                    rootFilesDir = { folder.root.toOkioPath() },
                    migrations = { name ->
                        asked += name
                        if (name.startsWith("cashu_")) {
                            listOf(CopyOnceMigration("migrated.$name") { out -> out[marker] = name })
                        } else {
                            emptyList()
                        }
                    },
                )

            assertEquals("cashu_npubA", subject.getDataStore("cashu_npubA").data.first()[marker])
            assertEquals("cashu_npubB", subject.getDataStore("cashu_npubB").data.first()[marker])
            assertNull("a file with no migration must stay untouched", subject.sharedSettings().data.first()[marker])

            assertTrue("cashu_npubA" in asked && "cashu_npubB" in asked && "shared_settings" in asked)
        }

    /**
     * Releasing a store lets the same file be opened again.
     *
     * DataStore's registry is keyed by path and `cancel()` only asks, so this is
     * only safe because [AppPreferenceStores.release] joins the scope's job.
     * Without the join this test is exactly the "multiple DataStores active for
     * the same file" crash.
     */
    @Test
    fun aReleasedStoreCanBeReopenedAndStillHasItsData() =
        runTest {
            val key = stringPreferencesKey("k")
            val subject = stores()

            subject.getDataStore("reopen").edit { it[key] = "written once" }

            assertTrue("something was open", subject.release("reopen"))
            assertFalse("and now nothing is", subject.release("reopen"))

            // a genuinely new instance over the same file
            val reopened = subject.getDataStore("reopen")
            assertEquals("written once", reopened.data.first()[key])
        }

    /**
     * The guard the Cashu and UI copies both rest on, now checked the way it
     * actually happens at runtime: the migration runs when the file is first
     * opened, and must not run again when a later instance opens the same file.
     */
    @Test
    fun aMigrationRunsOnceEvenAcrossAReopen() =
        runTest {
            val marker = stringPreferencesKey("copied")
            var runs = 0

            val subject =
                AppPreferenceStores(
                    rootFilesDir = { folder.root.toOkioPath() },
                    migrations = {
                        listOf(
                            CopyOnceMigration("migrated.once") { out ->
                                runs++
                                out[marker] = "run $runs"
                            },
                        )
                    },
                )

            assertEquals("run 1", subject.getDataStore("once").data.first()[marker])
            assertEquals(1, runs)

            subject.release("once")

            assertEquals("still the first copy", "run 1", subject.getDataStore("once").data.first()[marker])
            assertEquals("the migration must not run a second time", 1, runs)
        }
}
