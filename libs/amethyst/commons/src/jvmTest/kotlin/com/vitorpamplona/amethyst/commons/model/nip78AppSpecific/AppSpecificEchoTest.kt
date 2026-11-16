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

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The echo of a settings version this app published must not revert edits made after it was
 * signed: the pickers publish after a debounce, so newer toggles can be waiting while it comes back.
 */
class AppSpecificEchoTest {
    private val keyPair = KeyPair()
    private val signer = NostrSignerInternal(keyPair)
    private val settings = AccountSettings(keyPair)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = AppSpecificState(signer, LocalCache, scope, settings)

    @AfterTest
    fun tearDown() = scope.cancel()

    private suspend fun awaitBackup(event: AppSpecificDataEvent) =
        withTimeout(5_000) {
            while (settings.backupAppSpecificData?.id != event.id) delay(10)
        }

    @Test
    fun theEchoOfOurOwnPublishKeepsNewerEdits() =
        runBlocking {
            settings.changeReactionTypes(listOf("A"))
            val published = state.saveNewAppSpecificData()

            // A tap after the publish was signed, waiting out the debounce for the next one.
            settings.changeReactionTypes(listOf("A", "B"))

            LocalCache.justConsumeMyOwnEvent(published)
            awaitBackup(published)

            assertEquals(listOf("A", "B"), settings.syncedSettings.reactions.reactionChoices.value)
        }

    @Test
    fun aVersionFromElsewhereStillMerges() =
        runBlocking {
            settings.changeReactionTypes(listOf("A"))
            val mine = settings.syncedSettings.toInternal(settings.mutedPublicChats.value)
            mine.reactions.reactionChoices = listOf("C")

            // Same key, but not built from these live settings: another device, or a restore.
            val other =
                signer.sign(
                    AppSpecificDataEvent.build(
                        dTag = AppSpecificState.APP_SPECIFIC_DATA_D_TAG,
                        description = signer.nip44Encrypt(JsonMapper.toJson(mine), signer.pubKey),
                    ),
                )

            LocalCache.justConsumeMyOwnEvent(other)
            awaitBackup(other)

            withTimeout(5_000) {
                while (settings.syncedSettings.reactions.reactionChoices.value != listOf("C")) delay(10)
            }
        }
}
