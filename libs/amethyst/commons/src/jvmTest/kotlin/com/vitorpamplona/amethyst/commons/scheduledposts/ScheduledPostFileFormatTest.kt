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

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The scheduled-post file survived the move off Jackson.
 *
 * This file is a user's queued posts: pre-signed events waiting for their publish
 * time. If the format shifted when the serializer changed, an existing install
 * would silently drop everything it had queued on the first launch after the
 * update — no crash, no error, just an empty queue.
 *
 * [JACKSON_OUTPUT] is the exact string the Jackson build wrote for [sample],
 * captured from it before the switch. The assertions are that the kotlinx reader
 * loads it and the kotlinx writer reproduces it byte for byte — the second half
 * matters because it is what a downgrade, or an older `amy` build sharing the same
 * file, has to keep reading.
 */
class ScheduledPostFileFormatTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val sample =
        ScheduledPostFile(
            version = 1,
            posts =
                listOf(
                    ScheduledPost(
                        id = "id1",
                        accountPubkey = "pk1",
                        signedEventJson = """{"k":1}""",
                        relayUrls = listOf("wss://a", "wss://b"),
                        extraEventsJson = listOf("e1"),
                        publishAtSec = 111L,
                        createdAtSec = 222L,
                        status = ScheduledPostStatus.PENDING,
                        lastAttemptAtSec = null,
                        attemptCount = 0,
                        lastError = null,
                        terminatedAtSec = 333L,
                    ),
                ),
        )

    @Test
    fun writesTheBytesJacksonWrote() {
        assertEquals(JACKSON_OUTPUT, json.encodeToString(sample))
    }

    @Test
    fun readsAFileWrittenByTheJacksonBuild() {
        val loaded = json.decodeFromString<ScheduledPostFile>(JACKSON_OUTPUT)

        assertEquals(1, loaded.version)
        assertEquals(1, loaded.posts.size)
        val post = loaded.posts.first()
        assertEquals("id1", post.id)
        assertEquals("""{"k":1}""", post.signedEventJson)
        assertEquals(listOf("wss://a", "wss://b"), post.relayUrls)
        assertEquals(ScheduledPostStatus.PENDING, post.status)
        assertEquals(333L, post.terminatedAtSec)
    }

    @Test
    fun aFileFromANewerBuildStillLoads() {
        // Forward compatibility: the reader must not choke on a key it does not know,
        // which is what FAIL_ON_UNKNOWN_PROPERTIES=false used to buy.
        val withExtras =
            JACKSON_OUTPUT
                .replace("""{"version":1""", """{"version":1,"somethingNew":{"a":1}""")
                .replace(""""id":"id1"""", """"id":"id1","perPostFutureField":true""")

        val loaded = json.decodeFromString<ScheduledPostFile>(withExtras)

        assertEquals("id1", loaded.posts.first().id)
    }

    @Test
    fun versionIsNotDroppedJustBecauseItEqualsItsDefault() {
        // kotlinx omits a value equal to its default unless encodeDefaults is set.
        // Losing "version" would make the file unreadable to anything that checks it.
        assertEquals(true, json.encodeToString(sample).startsWith("""{"version":1,"""))
    }

    companion object {
        private const val JACKSON_OUTPUT =
            """{"version":1,"posts":[{"id":"id1","accountPubkey":"pk1","signedEventJson":"{\"k\":1}",""" +
                """"relayUrls":["wss://a","wss://b"],"extraEventsJson":["e1"],"publishAtSec":111,""" +
                """"createdAtSec":222,"status":"PENDING","lastAttemptAtSec":null,"attemptCount":0,""" +
                """"lastError":null,"terminatedAtSec":333}]}"""
    }
}
