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
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingSettings
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class NowPlayingSettingsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun raw(): DataStore<Preferences> {
        val file = File(folder.root, "shared.preferences_pb")
        return PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { file.toOkioPath() },
        )
    }

    @Test
    fun anUnreadableFileStillCountsAsRestored() =
        runBlocking {
            val unreadable =
                object : DataStore<Preferences> {
                    override val data: Flow<Preferences> = flow { throw IOException("disk gone") }

                    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("disk gone")
                }
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val state = NowPlayingSettingsState()
            NowPlayingSettingsStore(unreadable, scope, "alice", state)

            // Whatever waits for the saved settings carries on with the defaults instead of forever.
            assertEquals(false, withTimeout(5_000) { state.sharesOtherApps().first() })
            scope.cancel()
        }

    @Test
    fun appLabelsSurviveTheEncoding() {
        val apps = mapOf("com.spotify.music" to "Spotify", "org.mpris.MediaPlayer2.vlc" to "VLC media player")

        assertEquals(apps, NowPlayingSettingsStore.decodeApps(NowPlayingSettingsStore.encodeApps(apps)))
        assertEquals(emptyMap<String, String>(), NowPlayingSettingsStore.decodeApps(setOf("no-separator", "\tno-id")))
    }

    @Test
    fun settingsAreWrittenAndRestoredPerAccount() =
        runBlocking {
            val store = raw()
            val expected =
                NowPlayingSettings(
                    shareInApp = true,
                    shareOtherApps = true,
                    blockedApps = setOf("com.google.android.youtube"),
                    knownApps = mapOf("com.google.android.youtube" to "YouTube", "com.spotify.music" to "Spotify"),
                )

            val writerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val written = NowPlayingSettingsState()
            NowPlayingSettingsStore(store, writerScope, "alice", written)
            // Let the store finish its initial restore before changing anything.
            withTimeout(5_000) { store.data.first() }
            Thread.sleep(200)
            written.setShareInApp(true)
            written.setShareOtherApps(true)
            written.rememberApp("com.google.android.youtube", "YouTube")
            written.rememberApp("com.spotify.music", "Spotify")
            written.setAppBlocked("com.google.android.youtube", true)

            withTimeout(5_000) {
                // The block is the last change made, so once it is on disk everything is.
                while (store.data.first()[stringSetPreferencesKey("nowPlaying.alice.blockedApps")].isNullOrEmpty()) Thread.sleep(20)
            }
            writerScope.cancel()

            val readerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val restored = NowPlayingSettingsState()
            NowPlayingSettingsStore(store, readerScope, "alice", restored)
            withTimeout(5_000) { restored.flow.first { it.isEnabled() } }
            assertEquals(expected, restored.flow.value)

            // Another account on the same file starts from the defaults.
            val other = NowPlayingSettingsState()
            NowPlayingSettingsStore(store, readerScope, "bob", other)
            Thread.sleep(200)
            assertEquals(NowPlayingSettings(), other.flow.value)
            readerScope.cancel()
        }
}
