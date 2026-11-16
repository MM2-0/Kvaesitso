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
package com.vitorpamplona.amethyst.napplet

import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletStorageScope
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests the one place the trust boundary parses untrusted applet input ([NappletProtocolJson]).
 * Verifies the upstream `{type:"domain.action", id}` envelope round-trips. Runs as a plain JVM
 * unit test — the codec uses kotlinx.serialization, not Android's `org.json`.
 */
class NappletProtocolJsonTest {
    private val json = Json

    @Test
    fun resourcePolicyAndTransportFailuresUseResourceErrorEnvelopes() {
        val failures =
            listOf(
                NappletResponse.Denied(NappletCapability.RESOURCE, "No consent") to "blocked-by-policy",
                NappletResponse.Unsupported("resource.bytes") to "blocked-by-policy",
                NappletResponse.Failed("Fetch failed") to "network-error",
            )
        for (method in listOf("resource.info", "resource.bytes", "resource.bytesMany")) {
            for ((failure, code) in failures) {
                val result = json.parseToJsonElement(NappletProtocolJson.encodeResponse(method, failure)).jsonObject
                assertEquals("$method.error", result["type"]?.jsonPrimitive?.content)
                assertEquals(code, result["error"]?.jsonPrimitive?.content)
                assertFalse(result.containsKey("blob"))
            }
        }
    }

    private inline fun assertThrowsAny(block: () -> Unit) {
        try {
            block()
            fail("Expected an exception")
        } catch (_: Exception) {
            // expected
        }
    }

    private fun sampleEvent() =
        Event(
            id = "a".repeat(64),
            pubKey = "b".repeat(64),
            createdAt = 1_700_000_000L,
            kind = 1,
            tags = arrayOf(arrayOf("t", "napplet")),
            content = "hello",
            sig = "c".repeat(128),
        )

    // ---- decode requests (upstream "domain.action" envelope) ----

    @Test
    fun decodesGetPublicKey() {
        assertEquals(NappletRequest.GetPublicKey, NappletProtocolJson.decodeRequest("""{"type":"identity.getPublicKey","id":"1"}"""))
    }

    @Test
    fun decodesPublishFromAnUnsignedTemplateInTheEventField() {
        // @napplet/shim carries the unsigned template in the `event` field. The shell signs it.
        val req = NappletProtocolJson.decodeRequest("""{"type":"relay.publish","id":"1","event":{"kind":1,"tags":[["t","x"]],"content":"gm"}}""")
        assertEquals(NappletRequest.Publish(1, arrayOf(arrayOf("t", "x")), "gm"), req)
    }

    @Test
    fun decodesPublishEncrypted() {
        val req =
            NappletProtocolJson.decodeRequest(
                """{"type":"relay.publishEncrypted","id":"1","event":{"kind":4,"tags":[],"content":"hi"},"recipient":"pk","encryption":"nip04"}""",
            )
        assertEquals(NappletRequest.PublishEncrypted(4, emptyArray(), "hi", "pk", "nip04"), req)
    }

    @Test
    fun decodesQueryAndSubscribeFromFilterObjectOrFiltersArray() {
        val single = NappletProtocolJson.decodeRequest("""{"type":"relay.query","filter":{"kinds":[1],"#t":["nostr"],"limit":5}}""") as NappletRequest.QueryEvents
        assertEquals(listOf(1), single.filters.first().kinds)
        assertEquals(
            listOf("nostr"),
            single.filters
                .first()
                .tags
                ?.get("t"),
        )
        assertEquals(5, single.filters.first().limit)

        // Multiple filters are all honored, not just the first.
        val array = NappletProtocolJson.decodeRequest("""{"type":"relay.query","filters":[{"authors":["aa"]},{"kinds":[7]}]}""") as NappletRequest.QueryEvents
        assertEquals(2, array.filters.size)
        assertEquals(listOf("aa"), array.filters[0].authors)
        assertEquals(listOf(7), array.filters[1].kinds)

        val sub = NappletProtocolJson.decodeRequest("""{"type":"relay.subscribe","filter":{"kinds":[1]}}""") as NappletRequest.Subscribe
        assertEquals(listOf(1), sub.filters.first().kinds)
    }

    @Test
    fun decodesIdentityReads() {
        assertEquals(NappletRequest.IdentityRead("getProfile"), NappletProtocolJson.decodeRequest("""{"type":"identity.getProfile"}"""))
        assertEquals(NappletRequest.IdentityRead("getFollows"), NappletProtocolJson.decodeRequest("""{"type":"identity.getFollows"}"""))
        assertEquals(NappletRequest.IdentityRead("getList", "bookmarks"), NappletProtocolJson.decodeRequest("""{"type":"identity.getList","listType":"bookmarks"}"""))
        // getPublicKey keeps its dedicated request type, not the generic read.
        assertEquals(NappletRequest.GetPublicKey, NappletProtocolJson.decodeRequest("""{"type":"identity.getPublicKey"}"""))
    }

    @Test
    fun encodesIdentityJsonUnderMethodSpecificFields() {
        // Field names must match @napplet/nap identity result messages, not a generic "result".
        val follows = json.parseToJsonElement(NappletProtocolJson.encodeResponse("identity.getFollows", NappletResponse.Json("""["aa","bb"]"""))).jsonObject
        assertEquals(2, follows["pubkeys"]?.jsonArray?.size)

        val relays = json.parseToJsonElement(NappletProtocolJson.encodeResponse("identity.getRelays", NappletResponse.Json("""{"wss://r":{"read":true,"write":false}}"""))).jsonObject
        assertEquals(
            true,
            relays["relays"]
                ?.jsonObject
                ?.get("wss://r")
                ?.jsonObject
                ?.get("read")
                ?.jsonPrimitive
                ?.boolean,
        )

        val profile = json.parseToJsonElement(NappletProtocolJson.encodeResponse("identity.getProfile", NappletResponse.Json("""{"name":"x"}"""))).jsonObject
        assertEquals(
            "x",
            profile["profile"]
                ?.jsonObject
                ?.get("name")
                ?.jsonPrimitive
                ?.content,
        )

        // A malformed payload degrades to a JSON null rather than throwing.
        val bad = json.parseToJsonElement(NappletProtocolJson.encodeResponse("identity.getProfile", NappletResponse.Json("not json"))).jsonObject
        assertEquals(JsonNull, bad["profile"])
    }

    @Test
    fun decodesStorageOps() {
        // Wire types are storage.get/set/remove/keys (the SDK functions are getItem/setItem/removeItem/keys).
        assertEquals(NappletRequest.StorageGet("k"), NappletProtocolJson.decodeRequest("""{"type":"storage.get","key":"k"}"""))
        assertEquals(NappletRequest.StorageSet("k", "v"), NappletProtocolJson.decodeRequest("""{"type":"storage.set","key":"k","value":"v"}"""))
        assertEquals(NappletRequest.StorageRemove("k"), NappletProtocolJson.decodeRequest("""{"type":"storage.remove","key":"k"}"""))
        assertEquals(NappletRequest.StorageKeys(), NappletProtocolJson.decodeRequest("""{"type":"storage.keys"}"""))
        assertEquals(
            NappletRequest.StorageGet("k", NappletStorageScope.INSTANCE),
            NappletProtocolJson.decodeRequest("""{"type":"storage.get","key":"k","scope":"instance"}"""),
        )
    }

    @Test
    fun decodesValueResourceUpload() {
        assertEquals(NappletRequest.PayInvoice("lnbc1"), NappletProtocolJson.decodeRequest("""{"type":"value.payInvoice","invoice":"lnbc1"}"""))
        assertEquals(NappletRequest.ResourceInfo, NappletProtocolJson.decodeRequest("""{"type":"resource.info"}"""))
        assertEquals(NappletRequest.ResourceBytes("https://x"), NappletProtocolJson.decodeRequest("""{"type":"resource.bytes","url":"https://x"}"""))
        assertEquals(
            NappletRequest.ResourceBytesMany(listOf("https://x", "data:text/plain,hi")),
            NappletProtocolJson.decodeRequest("""{"type":"resource.bytesMany","urls":["https://x","data:text/plain,hi"]}"""),
        )
        // "SGk=" is base64 for "Hi"; shell.html inlines the request Blob as request.dataBase64.
        val up = NappletProtocolJson.decodeRequest("""{"type":"upload.upload","request":{"dataBase64":"SGk=","mimeType":"text/plain","filename":"a.txt"}}""") as NappletRequest.UploadBlob
        assertEquals("text/plain", up.contentType)
        assertEquals("a.txt", up.filename)
        assertEquals("Hi", up.bytes.decodeToString())
    }

    @Test
    fun uploadAcceptsUnpaddedBase64() {
        // java.util.Base64 decoded "SGk" (no '=') as "Hi"; the commonMain decoder must too.
        val up = NappletProtocolJson.decodeRequest("""{"type":"upload.upload","request":{"dataBase64":"SGk"}}""") as NappletRequest.UploadBlob
        assertEquals("Hi", up.bytes.decodeToString())
    }

    @Test
    fun unknownTypeDecodesToNull() {
        assertNull(NappletProtocolJson.decodeRequest("""{"type":"inc.emit","id":"1"}"""))
        // keys.signEvent is not a real domain method (keys = keyboard actions, not signing).
        assertNull(NappletProtocolJson.decodeRequest("""{"type":"keys.signEvent","id":"1"}"""))
        assertNull(NappletProtocolJson.decodeRequest("""{"type":"identity.futureMethod","id":"1"}"""))
        assertNull(NappletProtocolJson.decodeRequest("""{"foo":"bar"}"""))
    }

    @Test
    fun malformedOrMissingFieldThrows() {
        assertThrowsAny { NappletProtocolJson.decodeRequest("not json") }
        assertThrowsAny { NappletProtocolJson.decodeRequest("""{"type":"relay.publish","template":{"content":"x"}}""") }
    }

    @Test
    fun readTypeReturnsTheDiscriminant() {
        assertEquals("relay.publish", NappletProtocolJson.readType("""{"type":"relay.publish","id":"9"}"""))
    }

    @Test
    fun readsSubIdFromASubscription() {
        assertEquals("s7", NappletProtocolJson.readSubId("""{"type":"relay.subscribe","id":"1","subId":"s7","filters":[{}]}"""))
        assertNull(NappletProtocolJson.readSubId("""{"type":"relay.query"}"""))
    }

    @Test
    fun encodesSubscriptionPushesKeyedBySubId() {
        val ev = json.parseToJsonElement(NappletProtocolJson.encodeRelayEvent("s1", sampleEvent())).jsonObject
        assertEquals("relay.event", ev["type"]?.jsonPrimitive?.content)
        assertEquals("s1", ev["subId"]?.jsonPrimitive?.content)
        assertEquals(
            "a".repeat(64),
            ev["result"]
                ?.jsonObject
                ?.get("event")
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.content,
        )

        val eose = json.parseToJsonElement(NappletProtocolJson.encodeRelayEose("s1")).jsonObject
        assertEquals("relay.eose", eose["type"]?.jsonPrimitive?.content)
        assertEquals("s1", eose["subId"]?.jsonPrimitive?.content)
    }

    // ---- encode responses (".result" envelope) ----

    @Test
    fun encodesSuccessWithResultTypeAndOkTrue() {
        val o = json.parseToJsonElement(NappletProtocolJson.encodeResponse("identity.getPublicKey", NappletResponse.PublicKey("pk"))).jsonObject
        assertEquals("identity.getPublicKey.result", o["type"]?.jsonPrimitive?.content)
        assertTrue(o["ok"]!!.jsonPrimitive.boolean)
        assertEquals("pk", o["pubkey"]?.jsonPrimitive?.content)
    }

    @Test
    fun encodesPublishedEventAndEvents() {
        // relay.publish resolves to the signed event (matching upstream NostrEvent return).
        val published = json.parseToJsonElement(NappletProtocolJson.encodeResponse("relay.publish", NappletResponse.Published(sampleEvent(), listOf("wss://r")))).jsonObject
        assertEquals(
            "a".repeat(64),
            published["event"]
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals("a".repeat(64), published["eventId"]?.jsonPrimitive?.content)
        assertEquals(1, published["relays"]?.jsonArray?.size)

        val events = json.parseToJsonElement(NappletProtocolJson.encodeResponse("relay.query", NappletResponse.Events(listOf(sampleEvent())))).jsonObject
        assertEquals(1, events["events"]?.jsonArray?.size)
        assertEquals(
            "a".repeat(64),
            events["events"]
                ?.jsonArray
                ?.first()
                ?.jsonObject
                ?.get("event")
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun encodesStorageNullAsJsonNullAndKeysUnderKeysField() {
        val absent = json.parseToJsonElement(NappletProtocolJson.encodeResponse("storage.get", NappletResponse.StorageValue(null))).jsonObject
        assertEquals(JsonNull, absent["value"])

        // storage.keys returns its array under `keys`, per @napplet/nap.
        val keys = json.parseToJsonElement(NappletProtocolJson.encodeResponse("storage.keys", NappletResponse.Strings(listOf("a", "b")))).jsonObject
        assertEquals(2, keys["keys"]?.jsonArray?.size)
    }

    @Test
    fun encodesResourceInfoBulkItemsAndTypedErrors() {
        val info =
            json
                .parseToJsonElement(
                    NappletProtocolJson.encodeResponse(
                        "resource.info",
                        NappletResponse.ResourceInfo(listOf("https"), 10L * 1024L * 1024L, 16),
                    ),
                ).jsonObject
        assertEquals(
            "https",
            info["info"]
                ?.jsonObject
                ?.get("schemes")
                ?.jsonArray
                ?.first()
                ?.jsonObject
                ?.get("scheme")
                ?.jsonPrimitive
                ?.content,
        )

        val items =
            json
                .parseToJsonElement(
                    NappletProtocolJson.encodeResponse(
                        "resource.bytesMany",
                        NappletResponse.ResourceItems(
                            listOf(
                                NappletResponse.ResourceItem("https://x", NappletResponse.Bytes("Hi".encodeToByteArray(), "text/plain")),
                                NappletResponse.ResourceItem("https://y", error = "not-found"),
                            ),
                        ),
                    ),
                ).jsonObject["items"]
                ?.jsonArray
        assertEquals(
            "SGk=",
            items
                ?.first()
                ?.jsonObject
                ?.get("blob")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            "not-found",
            items
                ?.get(1)
                ?.jsonObject
                ?.get("error")
                ?.jsonPrimitive
                ?.content,
        )

        val failure =
            json
                .parseToJsonElement(
                    NappletProtocolJson.encodeResponse("resource.bytes", NappletResponse.ResourceFailure("blocked-by-policy", "private target")),
                ).jsonObject
        assertEquals("resource.bytes.error", failure["type"]?.jsonPrimitive?.content)
        assertEquals("blocked-by-policy", failure["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun encodesBytesAsBase64WithMime() {
        val o = json.parseToJsonElement(NappletProtocolJson.encodeResponse("resource.bytes", NappletResponse.Bytes("Hi".encodeToByteArray(), "text/plain"))).jsonObject
        assertEquals("SGk=", o["blob"]?.jsonPrimitive?.content)
        assertEquals("text/plain", o["mime"]?.jsonPrimitive?.content)
    }

    @Test
    fun encodesErrorsWithOkFalse() {
        val denied = json.parseToJsonElement(NappletProtocolJson.encodeResponse("relay.publish", NappletResponse.Denied(NappletCapability.RELAY, "no"))).jsonObject
        assertFalse(denied["ok"]!!.jsonPrimitive.boolean)
        assertEquals("denied", denied["error"]?.jsonPrimitive?.content)
        assertEquals("RELAY", denied["capability"]?.jsonPrimitive?.content)

        val unsupported = json.parseToJsonElement(NappletProtocolJson.encodeResponse("upload", NappletResponse.Unsupported("upload"))).jsonObject
        assertFalse(unsupported["ok"]!!.jsonPrimitive.boolean)
        assertEquals("unsupported", unsupported["error"]?.jsonPrimitive?.content)

        val failed = json.parseToJsonElement(NappletProtocolJson.encodeResponse("relay.publish", NappletResponse.Failed("boom"))).jsonObject
        assertEquals("boom", failed["reason"]?.jsonPrimitive?.content)
    }

    @Test
    fun decodesNip44EncryptAndDecrypt() {
        // The envelope the injected window.nostr.nip44 shim posts. Field names are the contract
        // between shim.js and this decoder — renaming either side silently breaks NIP-17 in nSites.
        assertEquals(
            NappletRequest.Nip44Encrypt("pk", "gm"),
            NappletProtocolJson.decodeRequest("""{"type":"nostr.nip44Encrypt","id":"1","peer":"pk","plaintext":"gm"}"""),
        )
        assertEquals(
            NappletRequest.Nip44Decrypt("pk", "cipher"),
            NappletProtocolJson.decodeRequest("""{"type":"nostr.nip44Decrypt","id":"1","peer":"pk","ciphertext":"cipher"}"""),
        )
    }

    @Test
    fun nip44WithoutAPeerIsRejectedRatherThanDefaultedToSomeKey() {
        // `peer` is required: silently substituting a default would encrypt to the wrong party.
        assertThrowsAny { NappletProtocolJson.decodeRequest("""{"type":"nostr.nip44Encrypt","id":"1","plaintext":"gm"}""") }
        assertThrowsAny { NappletProtocolJson.decodeRequest("""{"type":"nostr.nip44Decrypt","id":"1","ciphertext":"c"}""") }
    }

    @Test
    fun encodesTextResultsUnderValue() {
        val result = json.parseToJsonElement(NappletProtocolJson.encodeResponse("nostr.nip44Encrypt", NappletResponse.Text("CIPHER"))).jsonObject
        assertEquals("nostr.nip44Encrypt.result", result["type"]?.jsonPrimitive?.content)
        assertTrue(result["ok"]!!.jsonPrimitive.boolean)
        assertEquals("CIPHER", result["value"]?.jsonPrimitive?.content)
    }
}
