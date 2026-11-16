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
package com.vitorpamplona.amethyst.commons.scheduledposts

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The checks every platform's drain gets from the shared publisher, before anything is sent. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledPostPublisherTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var store: ScheduledPostStore

    private val author = NostrSignerSync(KeyPair())

    @Before
    fun setUp() {
        store = ScheduledPostStore(File(temp.root, "scheduled_posts.json").path) { 2_000 }
    }

    private fun note(content: String = "hello"): Event = author.sign<Event>(1_000, 1, emptyArray(), content)

    private fun post(
        event: Event,
        accountPubkey: String = author.pubKey,
    ) = ScheduledPost(
        id = "post-1",
        accountPubkey = accountPubkey,
        signedEventJson = event.toJson(),
        relayUrls = listOf("wss://relay.example/"),
        extraEventsJson = emptyList(),
        publishAtSec = 1_000,
        createdAtSec = 500,
    )

    private fun publisher(failures: MutableList<String?> = mutableListOf()) =
        ScheduledPostPublisher(
            store = store,
            client = mockk<INostrClient>(relaxed = true),
            resolveRelays = { emptySet() },
            onFailed = { _, error -> failures.add(error) },
        )

    @Test
    fun aPostSignedByAnotherKeyIsFailedWithoutPublishing() =
        runTest {
            store.add(post(note(), accountPubkey = "b".repeat(64)))
            val failures = mutableListOf<String?>()

            val report = publisher(failures).drainDue(nowSec = 2_000, accountPubkey = "b".repeat(64))

            assertEquals(1, report.failed)
            assertEquals(listOf("pubkey mismatch"), failures)
            assertEquals(ScheduledPostStatus.FAILED, store.list().single().status)
        }

    @Test
    fun aTamperedEventIsFailedWithoutPublishing() =
        runTest {
            val signed = note()
            val tampered = Event(signed.id, signed.pubKey, signed.createdAt, signed.kind, signed.tags, "edited", signed.sig)
            store.add(post(tampered))
            val failures = mutableListOf<String?>()

            val report = publisher(failures).drainDue(nowSec = 2_000)

            assertEquals(1, report.failed)
            assertEquals(listOf("invalid signature"), failures)
        }

    @Test
    fun aCancelledDrainDoesNotFailThePost() =
        runTest {
            store.add(post(note()))
            val failures = mutableListOf<String?>()

            // No relay ever answers, so the publish waits; the drain is cancelled mid-wait, as when
            // the worker is stopped.
            val drain = launch { publisher(failures).drainDue(nowSec = 2_000) }
            advanceTimeBy(1_000)
            drain.cancel()
            drain.join()

            assertEquals(emptyList<String?>(), failures)
            assertEquals(ScheduledPostStatus.PUBLISHING, store.list().single().status)
        }
}
