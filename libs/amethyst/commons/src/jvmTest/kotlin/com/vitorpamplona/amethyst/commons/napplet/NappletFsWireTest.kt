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

import com.vitorpamplona.amethyst.commons.napplet.fs.FakeNappletFilePort
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsSession
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletPickedFile
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.InMemoryNappletPermissionStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * NAP-FS on the wire, end to end through [NappletRequestRouter]: the message shapes the spec's examples
 * show, the way a napplet like PhotoHop opens a file in chunks and exports one in chunks.
 */
class NappletFsWireTest {
    private val signer = NostrSignerInternal(KeyPair("00".repeat(31).plus("07").hexToByteArray()))
    private val applet = NappletIdentity(authorPubKey = "aa".repeat(32), identifier = "", instanceId = "run")
    private val port = FakeNappletFilePort()
    private val files = NappletFsSession(port)
    private val broker =
        NappletBroker(
            signer,
            NappletPermissionLedger(InMemoryNappletPermissionStore()),
            { _, _, _ -> GrantState.ALLOW_SESSION },
        )

    private suspend fun send(payload: String): JsonObject {
        val outcome = assertIs<NappletRequestRouter.Outcome.Reply>(NappletRequestRouter.route(broker, applet, setOf(NappletCapability.FS), payload, files))
        return Json.parseToJsonElement(outcome.payload).jsonObject
    }

    @Test
    fun infoCarriesLimits() =
        runTest {
            val reply = send("""{"type":"fs.info","id":"i1"}""")
            assertEquals("fs.info.result", reply["type"]!!.jsonPrimitive.content)
            assertNull(reply["error"])
            val info = reply["info"]!!.jsonObject
            assertEquals(0, info["roots"]!!.jsonArray.size)
            assertEquals(NappletFsSession.MAX_READ_BYTES, info["limits"]!!.jsonObject["maxReadBytes"]!!.jsonPrimitive.int)
        }

    @Test
    fun openAndReadAFileInRanges() =
        runTest {
            port.disk["content://p/1"] = "Hello world!".encodeToByteArray()
            port.opens.addLast(listOf(NappletPickedFile("content://p/1", "note.txt", 12)))
            val picked = send("""{"type":"fs.pickFile","id":"p1","options":{"permissions":["read"],"accept":[{"mime":"text/plain"},{"extension":".md"}]}}""")
            val entry =
                picked["result"]!!
                    .jsonObject["entries"]!!
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals("/picked/note.txt", entry["path"]!!.jsonPrimitive.content)
            assertEquals("file", entry["kind"]!!.jsonPrimitive.content)
            assertEquals(12L, entry["size"]!!.jsonPrimitive.long)

            val read = send("""{"type":"fs.read","id":"r1","path":"/picked/note.txt","options":{"offset":6,"length":5}}""")
            val result = read["result"]!!.jsonObject
            assertEquals("d29ybGQ=", result["data"]!!.jsonPrimitive.content)
            assertEquals(6L, result["offset"]!!.jsonPrimitive.long)
            assertEquals(5, result["bytesRead"]!!.jsonPrimitive.int)
            assertFalse(result["eof"]!!.jsonPrimitive.boolean)
            assertEquals(12L, result["size"]!!.jsonPrimitive.long)
        }

    @Test
    fun saveAFileInChunks() =
        runTest {
            port.saves.addLast(NappletPickedFile("/tmp/out/export.json", "export.json"))
            send("""{"type":"fs.pickSaveFile","id":"p3","options":{"permissions":["write","create"],"suggestedName":"export.json","accept":[{"mime":"application/json"}]}}""")
            val first = send("""{"type":"fs.write","id":"w1","path":"/picked/export.json","data":"SGVsbG8g","options":{"mode":"replace"}}""")
            assertEquals(6, first["result"]!!.jsonObject["bytesWritten"]!!.jsonPrimitive.int)
            val second = send("""{"type":"fs.write","id":"w2","path":"/picked/export.json","data":"d29ybGQh","options":{"mode":"append"}}""")
            assertEquals(12L, second["result"]!!.jsonObject["size"]!!.jsonPrimitive.long)
            assertEquals("Hello world!", port.disk["/tmp/out/export.json"]!!.decodeToString())
        }

    @Test
    fun failuresCarryOnlyTheErrorCode() =
        runTest {
            port.opens.addLast(null)
            val cancelled = send("""{"type":"fs.pickFile","id":"p4"}""")
            assertEquals(mapOf("type" to "fs.pickFile.result", "error" to "cancelled"), cancelled.mapValues { it.value.jsonPrimitive.content })

            val hidden = send("""{"type":"fs.read","id":"r2","path":"/hidden/private.txt"}""")
            assertEquals(mapOf("type" to "fs.read.result", "error" to "not-found"), hidden.mapValues { it.value.jsonPrimitive.content })

            val badData = send("""{"type":"fs.write","id":"w3","path":"/picked/x","data":"not base64!"}""")
            assertEquals("invalid-data", badData["error"]!!.jsonPrimitive.content)

            // Malformed (no path): still NAP-FS's shape, never a generic failure.
            val malformed = send("""{"type":"fs.stat","id":"s1"}""")
            assertEquals(mapOf("type" to "fs.stat.result", "error" to "unsupported"), malformed.mapValues { it.value.jsonPrimitive.content })
            val wrongType = send("""{"type":"fs.read","id":"r3","path":"/picked/x","options":{"offset":"0"}}""")
            assertEquals("unsupported", wrongType["error"]!!.jsonPrimitive.content)
        }

    @Test
    fun refusalsAreNapFsErrors() {
        assertEquals("""{"type":"fs.pickFile.result","error":"permission-denied"}""", NappletProtocolJson.encodeResponse("fs.pickFile", NappletResponse.Denied(NappletCapability.FS, "no")))
        assertEquals("""{"type":"fs.read.result","error":"io-error"}""", NappletProtocolJson.encodeResponse("fs.read", NappletResponse.Failed("/home/me/x: boom")))
        assertEquals("""{"type":"fs.unwatch.result"}""", NappletProtocolJson.encodeResponse("fs.unwatch", NappletResponse.Done))
    }

    @Test
    fun decodesTheSpecRequests() {
        assertEquals(
            NappletRequest.FsWrite("/shared/db.bin", "AAECAw==", "patch", 4096),
            NappletProtocolJson.decodeRequest("""{"type":"fs.write","id":"w2","path":"/shared/db.bin","data":"AAECAw==","options":{"mode":"patch","offset":4096}}"""),
        )
        val pick = assertIs<NappletRequest.FsPick>(NappletProtocolJson.decodeRequest("""{"type":"fs.pickFile","id":"p","options":{"accept":[".png",{"mime":"image/jpeg"},{"extension":"webp"}]}}"""))
        assertEquals(listOf(".png", "image/jpeg", ".webp"), pick.options.accept.map { it.token() })
        assertEquals(NappletRequest.FsMove("/a", "/b"), NappletProtocolJson.decodeRequest("""{"type":"fs.move","id":"m","fromPath":"/a","toPath":"/b"}"""))
    }
}
