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
package com.vitorpamplona.amethyst.commons.model.nip78AppSpecific

import com.vitorpamplona.amethyst.commons.model.AccentColorType
import com.vitorpamplona.amethyst.commons.model.AccountAppearancePreferencesInternal
import com.vitorpamplona.amethyst.commons.model.AccountComposePreferencesInternal
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.AccountSyncedSettingsInternal
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The NIP-78 groups that live in AccountSettings: applied from other devices, published on local edits. */
class AppSpecificLocalGroupsTest {
    private val keyPair = KeyPair()
    private val signer = NostrSignerInternal(keyPair)
    private val settings = AccountSettings(keyPair)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = AppSpecificState(signer, LocalCache, scope, settings)

    @AfterTest
    fun tearDown() = scope.cancel()

    private suspend fun receive(blob: AccountSyncedSettingsInternal) {
        val event =
            signer.sign(
                AppSpecificDataEvent.build(
                    dTag = AppSpecificState.APP_SPECIFIC_DATA_D_TAG,
                    description = signer.nip44Encrypt(JsonMapper.toJson(blob), signer.pubKey),
                ),
            )
        // updateAppSpecificData keeps the blob as the backup first and applies its groups after,
        // on another thread, ending with a save: wait for that save, not just the backup, or the
        // assertions can read a half-applied blob.
        val savedBefore = settings.saveable.value
        LocalCache.justConsumeMyOwnEvent(event)
        withTimeout(5_000) {
            while (settings.backupAppSpecificData?.id != event.id || settings.saveable.value === savedBefore) delay(10)
        }
    }

    @Test
    fun anotherDevicesGroupsApplyHere() =
        runBlocking {
            val theirs = settings.toSyncedSettingsInternal()
            theirs.appearance = AccountAppearancePreferencesInternal("ORANGE")
            theirs.compose = AccountComposePreferencesInternal("— from my other device")
            settings.setChatFeedEnabled(ChatFeedType.NIP17, false)
            settings.changeDefaultHomeFollowList(TopFilter.Global)
            val withTheirFilters = settings.toSyncedSettingsInternal()
            theirs.messages = withTheirFilters.messages
            theirs.feeds = withTheirFilters.feeds
            // Back to this device's own values before the other device's blob arrives.
            settings.setChatFeedEnabled(ChatFeedType.NIP17, true)
            settings.changeDefaultHomeFollowList(TopFilter.AllFollows)

            receive(theirs)

            assertEquals(AccentColorType.ORANGE, settings.accentColor.value)
            assertEquals("— from my other device", settings.composeSignature.value)
            assertEquals(ChatFeedType.ALL - ChatFeedType.NIP17, settings.enabledChatFeeds.value)
            assertEquals(TopFilter.Global, settings.defaultHomeFollowList.value)
        }

    @Test
    fun aBlobWithoutTheGroupsKeepsThisDevicesValues() =
        runBlocking {
            settings.updateAccentColor(AccentColorType.PINK)
            settings.changeCallsEnabled(false)
            settings.changeComposeSignature("— mine")

            val older = settings.toSyncedSettingsInternal()
            older.appearance = null
            older.calls = null
            older.compose = null
            older.messages = null
            older.feeds = null
            older.relayAuth = null
            older.uploads = null
            older.reactions.reactionChoices = listOf("C")

            receive(older)

            assertEquals(listOf("C"), settings.syncedSettings.reactions.reactionChoices.value)
            assertEquals(AccentColorType.PINK, settings.accentColor.value)
            assertEquals(false, settings.callsEnabled.value)
            assertEquals("— mine", settings.composeSignature.value)
        }

    @Test
    fun onlyLocalEditsAskForAPublish() =
        runBlocking {
            var signals = 0
            val collector = scope.launch(start = CoroutineStart.UNDISPATCHED) { settings.syncedChanges.collect { signals++ } }

            val theirs = settings.toSyncedSettingsInternal()
            theirs.appearance = AccountAppearancePreferencesInternal("BLUE")
            receive(theirs)
            assertEquals(AccentColorType.BLUE, settings.accentColor.value)
            assertEquals(0, signals)

            settings.updateAccentColor(AccentColorType.RED)
            withTimeout(5_000) { while (signals == 0) delay(10) }
            assertEquals(1, signals)

            collector.cancel()
        }
}
