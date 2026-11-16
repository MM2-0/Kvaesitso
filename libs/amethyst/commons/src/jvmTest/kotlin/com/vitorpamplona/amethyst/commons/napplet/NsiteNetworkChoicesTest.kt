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
package com.vitorpamplona.amethyst.commons.napplet

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Where an nSite (or, on desktop, a web page) starts: the user's choice for it, else the Tor settings' switch. */
class NsiteNetworkChoicesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    private fun store(name: String): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(produceFile = { folder.root.toOkioPath() / "$name.preferences_pb" })

    @Test
    fun aSiteNeverSetFollowsTheSwitch() =
        runTest {
            var viaTor = false
            val choices = NsiteNetworkChoices(store("a"), scope) { viaTor }
            choices.awaitReady()
            assertFalse(choices.useTor("author:never-set"))
            viaTor = true
            assertTrue(choices.useTor("author:never-set"))
        }

    @Test
    fun theUsersChoiceForASiteWinsEitherWay() =
        runTest {
            val choices = NsiteNetworkChoices(store("b"), scope) { false }
            choices.awaitReady()
            choices.set("author:pinned", useTor = true)
            assertTrue(choices.useTor("author:pinned"))

            val torByDefault = NsiteNetworkChoices(store("c"), scope) { true }
            torByDefault.awaitReady()
            torByDefault.set("author:open", useTor = false)
            assertFalse(torByDefault.useTor("author:open"))
        }

    @Test
    fun aChoiceStoredOnDiskIsRead() =
        runTest {
            val disk = store("d")
            disk.edit { it[stringPreferencesKey("author:pinned")] = "TOR" }
            val choices = NsiteNetworkChoices(disk, scope) { false }
            choices.awaitReady()
            assertTrue(choices.useTor("author:pinned"))
        }

    @Test
    fun everySiteIsOnTorUntilTheStoredChoicesHaveLoaded() =
        runTest {
            // A store that hasn't answered yet: a site pinned to Tor must not start on the open web.
            val gate = CompletableDeferred<Unit>()
            val slow =
                object : DataStore<Preferences> {
                    override val data: Flow<Preferences> =
                        flow {
                            gate.await()
                            emit(emptyPreferences())
                        }

                    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = transform(emptyPreferences())
                }
            val choices = NsiteNetworkChoices(slow, scope) { false }
            assertTrue(choices.useTor("author:unknown-yet"))
            gate.complete(Unit)
            choices.awaitReady()
            assertFalse(choices.useTor("author:unknown-yet"))
        }
}
