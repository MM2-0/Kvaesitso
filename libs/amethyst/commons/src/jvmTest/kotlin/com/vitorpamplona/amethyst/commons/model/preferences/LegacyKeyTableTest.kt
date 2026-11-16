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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** A [LegacyPreferenceSource] over a map, standing in for the encrypted file. */
class FakeLegacySource(
    private val values: Map<String, Any>,
) : LegacyPreferenceSource {
    var opened = 0
        private set

    init {
        opened++
    }

    override fun keys() = values.keys

    override fun getBoolean(name: String) = values[name] as Boolean?

    override fun getString(name: String) = values[name] as String?

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(name: String) = values[name] as Set<String>?
}

class LegacyKeyTableTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val flag = booleanPreferencesKey("flag")
    private val text = stringPreferencesKey("text")
    private val bag = stringSetPreferencesKey("bag")

    private val table =
        LegacyKeyTable(
            "migrated.test",
            listOf(
                LegacyBooleanKey("legacy_flag", flag),
                LegacyStringKey("legacy_text", text),
                LegacyStringSetKey("legacy_bag", bag),
            ),
        )

    private var seq = 0

    private fun store(
        source: () -> LegacyPreferenceSource,
        name: String = "t_${seq++}",
    ): DataStore<Preferences> {
        val file = File(folder.root, "$name.preferences_pb")
        return PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            migrations = listOf(table.migration(source)),
            produceFile = { file.toOkioPath() },
        )
    }

    @Test
    fun copiesEveryTypeAcrossTheRename() =
        runTest {
            val legacy =
                FakeLegacySource(
                    mapOf(
                        "legacy_flag" to true,
                        "legacy_text" to "hello",
                        "legacy_bag" to setOf("a", "b"),
                    ),
                )

            val prefs = store({ legacy }).data.first()

            assertEquals(true, prefs[flag])
            assertEquals("hello", prefs[text])
            assertEquals(setOf("a", "b"), prefs[bag])
        }

    /**
     * The distinction the whole copy rests on: a key the user never set must
     * stay absent, so it keeps reading as unset and falls back to its own
     * default. Writing `false` here would turn off features whose default is on.
     */
    @Test
    fun anAbsentLegacyKeyStaysAbsent() =
        runTest {
            val prefs = store({ FakeLegacySource(mapOf("legacy_text" to "only me")) }).data.first()

            assertEquals("only me", prefs[text])
            assertNull(prefs[flag])
            assertNull(prefs[bag])
        }

    @Test
    fun hasRunReportsTheMarker() =
        runTest {
            assertFalse(table.hasRun(emptyPreferences()))

            val prefs = store({ FakeLegacySource(emptyMap()) }).data.first()

            assertTrue(table.hasRun(prefs))
        }

    /**
     * Decrypting an `EncryptedSharedPreferences` is not free, so the legacy
     * file must not be opened on launches where the copy has already run.
     */
    @Test
    fun theLegacyFileIsNotOpenedOnceTheCopyHasRun() =
        runTest {
            var opens = 0
            val open = {
                opens++
                FakeLegacySource(mapOf("legacy_text" to "x")) as LegacyPreferenceSource
            }
            val file = File(folder.root, "reopen.preferences_pb")

            // DataStore registers a live store per file path and only releases
            // it when the owning scope ends, so each "launch" gets its own
            // scope and gives it back.
            fun open(scope: CoroutineScope) =
                PreferenceDataStoreFactory.createWithPath(
                    scope = scope,
                    migrations = listOf(table.migration(open)),
                    produceFile = { file.toOkioPath() },
                )

            val first = CoroutineScope(Dispatchers.IO + SupervisorJob())
            assertEquals("x", open(first).data.first()[text])
            assertEquals(1, opens)
            first.cancel()
            first.coroutineContext.job.join()

            val second = CoroutineScope(Dispatchers.IO + SupervisorJob())
            assertEquals("x", open(second).data.first()[text])
            assertEquals(1, opens)
            second.cancel()
        }

    /**
     * The legacy names are what the Android app has written since its first
     * release. A rename here resets that setting for everyone who had it, so
     * the list is pinned rather than regenerated.
     */
    @Test
    fun theShippedTablesCoverTheirKeys() {
        assertEquals(
            setOf(
                "stripLocationOnUpload",
                "optimizeMediaOnUpload",
                "mirrorUploadsToAllServers",
                "useLocalBlossomCache",
                "localBlossomCacheProfilePicturesOnly",
                "defaultFileServer",
            ),
            UploadSettingsStore.legacyTable.legacyNames,
        )

        assertEquals(
            setOf(
                "nostr_pubkey",
                "login_with_external_signer",
                "signer_package_name",
                "localRelayServers",
                "openBackupConflicts",
                "has_backed_up_keys",
            ),
            AccountIdentityStore.legacyTable.legacyNames,
        )

        assertEquals(FollowListSlot.entries.size, TopNavFollowListStore.legacyTable.keys.size)
        assertEquals(LatestEventSlot.entries.count { it.existedInLegacyPrefs }, LatestEventCacheStore.legacyTable.keys.size)
    }

    /** Several tables share one store, so their markers must not collide. */
    @Test
    fun everyShippedMarkerIsDistinct() {
        val markers =
            listOf(
                TopNavFollowListStore.legacyTable,
                LatestEventCacheStore.legacyTable,
                UploadSettingsStore.legacyTable,
                DialogDismissalStore.legacyTable,
                RelayAuthStore.legacyTable,
                FeedVisibilityStore.legacyTable,
                NotificationPrefsStore.legacyTable,
                AccountIdentityStore.legacyTable,
            ).map { it.markerName }

        assertEquals(markers.size, markers.toSet().size)
    }
}
