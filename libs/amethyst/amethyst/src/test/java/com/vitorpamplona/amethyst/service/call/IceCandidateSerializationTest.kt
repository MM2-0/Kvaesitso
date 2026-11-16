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
package com.vitorpamplona.amethyst.service.call

import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class IceCandidateSerializationTest {
    @Test
    fun serializeCandidateProducesValidJson() {
        val json = CallIceCandidateEvent.serializeCandidate("candidate:123 1 udp 456", "audio", 1)
        assert(json.contains(""""candidate":"candidate:123 1 udp 456""""))
        assert(json.contains(""""sdpMid":"audio""""))
        assert(json.contains(""""sdpMLineIndex":1"""))
    }

    @Test
    fun serializeAndParseRoundTrip() {
        val sdp = "candidate:999 1 udp 789 10.0.0.1 5000 typ host"
        val sdpMid = "video"
        val sdpMLineIndex = 2

        val json = CallIceCandidateEvent.serializeCandidate(sdp, sdpMid, sdpMLineIndex)

        // Create a minimal event to test parsing
        val event =
            CallIceCandidateEvent(
                id = "test",
                pubKey = "test",
                createdAt = 0,
                tags = emptyArray(),
                content = json,
                sig = "test",
            )

        assertEquals(sdp, event.candidateSdp())
        assertEquals(sdpMid, event.sdpMid())
        assertEquals(sdpMLineIndex, event.sdpMLineIndex())
    }

    @Test
    fun parseCandidateHandlesMissingFields() {
        val event =
            CallIceCandidateEvent(
                id = "test",
                pubKey = "test",
                createdAt = 0,
                tags = emptyArray(),
                content = """{"candidate":"test"}""",
                sig = "test",
            )

        assertEquals("test", event.candidateSdp())
        assertEquals("0", event.sdpMid()) // default
        assertEquals(0, event.sdpMLineIndex()) // default
    }
}
