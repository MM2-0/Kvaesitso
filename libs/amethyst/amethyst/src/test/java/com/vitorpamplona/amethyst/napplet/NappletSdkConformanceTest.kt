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
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletIncRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletStorageScope
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-contract conformance against `@napplet/nap@0.15.0` + `@napplet/core@0.15.0`. Each test
 * pins [NappletProtocolJson] to the SDK's *exact* request envelope and result field names, so a
 * regression that drifts from the published message types fails CI. The "gap guard" tests document
 * the known non-conformant surface (see `plans/2026-06-21-napplet-sdk-conformance-audit.md`): when
 * one of those is fixed, its guard flips intentionally and is updated alongside the fix.
 *
 * Field names are quoted from the canonical `<domain>/types.d.ts` message interfaces.
 */
class NappletSdkConformanceTest {
    private val json = Json

    private fun result(
        requestType: String,
        response: NappletResponse,
    ) = json.parseToJsonElement(NappletProtocolJson.encodeResponse(requestType, response)).jsonObject

    private fun sampleEvent() =
        Event(
            id = "a".repeat(64),
            pubKey = "b".repeat(64),
            createdAt = 1_700_000_000L,
            kind = 1,
            tags = arrayOf(arrayOf("t", "x")),
            content = "gm",
            sig = "c".repeat(128),
        )

    // ---------- relay (Relay*Message) ----------

    @Test
    fun relayPublishRequestCarriesTheTemplateInTheEventField() {
        // RelayPublishMessage: { type:'relay.publish', id, event }
        val req = NappletProtocolJson.decodeRequest("""{"type":"relay.publish","id":"1","event":{"kind":1,"tags":[["t","x"]],"content":"gm"}}""")
        assertEquals(NappletRequest.Publish(1, arrayOf(arrayOf("t", "x")), "gm"), req)
    }

    @Test
    fun relayPublishEncryptedRequestMatches() {
        // RelayPublishEncryptedMessage: { type:'relay.publishEncrypted', id, event, recipient, encryption? }
        val req = NappletProtocolJson.decodeRequest("""{"type":"relay.publishEncrypted","id":"1","event":{"kind":4,"tags":[],"content":"hi"},"recipient":"pk","encryption":"nip04"}""")
        assertEquals(NappletRequest.PublishEncrypted(4, emptyArray(), "hi", "pk", "nip04"), req)
    }

    @Test
    fun relayQueryAndSubscribeReadTheFiltersArray() {
        // RelayQueryMessage: { type:'relay.query', id, filters: NostrFilter[] } — all filters honored.
        val q = NappletProtocolJson.decodeRequest("""{"type":"relay.query","id":"1","filters":[{"kinds":[1]},{"authors":["aa"]}]}""") as NappletRequest.QueryEvents
        assertEquals(2, q.filters.size)
        assertEquals(listOf(1), q.filters[0].kinds)
        assertEquals(listOf("aa"), q.filters[1].authors)

        // RelaySubscribeMessage: { type:'relay.subscribe', id, subId, filters, relay? }
        val s = NappletProtocolJson.decodeRequest("""{"type":"relay.subscribe","id":"1","subId":"s1","filters":[{"kinds":[7]}]}""") as NappletRequest.Subscribe
        assertEquals(listOf(7), s.filters.first().kinds)
        // The subId the pushes are keyed by is read from the raw envelope by the service.
        assertEquals("s1", NappletProtocolJson.readSubId("""{"type":"relay.subscribe","id":"1","subId":"s1","filters":[{}]}"""))
    }

    @Test
    fun relayPublishResultCarriesEventAndEventId() {
        // RelayPublishResultMessage: { type:'relay.publish.result', id, ok, event?, eventId?, error? }
        val o = result("relay.publish", NappletResponse.Published(sampleEvent(), listOf("wss://r")))
        assertEquals("relay.publish.result", o["type"]?.jsonPrimitive?.content)
        assertEquals(
            "a".repeat(64),
            o["event"]
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals("a".repeat(64), o["eventId"]?.jsonPrimitive?.content)
    }

    @Test
    fun relayQueryResultCarriesEvents() {
        // RelayQueryResultMessage: { type:'relay.query.result', id, events, error? }
        val o = result("relay.query", NappletResponse.Events(listOf(sampleEvent())))
        assertEquals(1, o["events"]?.jsonArray?.size)
        assertEquals(
            "a".repeat(64),
            o["events"]
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
    fun relayEventAndEosePushesMatchTheSdk() {
        // RelayEventMessage (PUSH): { type:'relay.event', subId, result:{event, sidecar?} }
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

        // RelayEoseMessage (PUSH): { type:'relay.eose', subId }
        val eose = json.parseToJsonElement(NappletProtocolJson.encodeRelayEose("s1")).jsonObject
        assertEquals("relay.eose", eose["type"]?.jsonPrimitive?.content)
        assertEquals("s1", eose["subId"]?.jsonPrimitive?.content)

        // RelayClosedMessage (PUSH): { type:'relay.closed', subId, reason }
        val closed = json.parseToJsonElement(NappletProtocolJson.encodeRelayClosed("s1", "done")).jsonObject
        assertEquals("relay.closed", closed["type"]?.jsonPrimitive?.content)
        assertEquals("s1", closed["subId"]?.jsonPrimitive?.content)
        assertEquals("done", closed["reason"]?.jsonPrimitive?.content)
    }

    // ---------- identity (Identity*Message) ----------

    @Test
    fun identityGetPublicKeyKeepsItsOwnRequestAndPubkeyField() {
        assertEquals(NappletRequest.GetPublicKey, NappletProtocolJson.decodeRequest("""{"type":"identity.getPublicKey","id":"1"}"""))
        assertEquals("pk", result("identity.getPublicKey", NappletResponse.PublicKey("pk"))["pubkey"]?.jsonPrimitive?.content)
    }

    @Test
    fun identityReadsDecodeGenericallyAndEncodeMethodSpecificFields() {
        // identity.getProfile/getRelays/getFollows/getMutes/getBlocked/getList → IdentityRead
        assertEquals(NappletRequest.IdentityRead("getProfile"), NappletProtocolJson.decodeRequest("""{"type":"identity.getProfile","id":"1"}"""))
        assertEquals(NappletRequest.IdentityRead("getList", "bookmarks"), NappletProtocolJson.decodeRequest("""{"type":"identity.getList","id":"1","listType":"bookmarks"}"""))

        // Result field per @napplet/nap: getProfile→profile, getRelays→relays,
        // getFollows/getMutes/getBlocked→pubkeys, getList→entries.
        assertTrue(result("identity.getProfile", NappletResponse.Json("""{"name":"x"}""")).containsKey("profile"))
        assertTrue(result("identity.getRelays", NappletResponse.Json("""{"wss://r":{"read":true,"write":true}}""")).containsKey("relays"))
        assertTrue(result("identity.getFollows", NappletResponse.Json("""["aa"]""")).containsKey("pubkeys"))
        assertTrue(result("identity.getMutes", NappletResponse.Json("""["aa"]""")).containsKey("pubkeys"))
        assertTrue(result("identity.getBlocked", NappletResponse.Json("""["aa"]""")).containsKey("pubkeys"))
        assertTrue(result("identity.getList", NappletResponse.Json("""["aa"]""")).containsKey("entries"))
    }

    // ---------- storage (Storage*Message) ----------

    @Test
    fun storageWireTypesAndFieldsMatch() {
        // Wire types are storage.get/set/remove/keys (SDK functions getItem/setItem/removeItem/keys).
        assertEquals(NappletRequest.StorageGet("k"), NappletProtocolJson.decodeRequest("""{"type":"storage.get","id":"1","key":"k"}"""))
        assertEquals(NappletRequest.StorageSet("k", "v"), NappletProtocolJson.decodeRequest("""{"type":"storage.set","id":"1","key":"k","value":"v"}"""))
        assertEquals(NappletRequest.StorageRemove("k"), NappletProtocolJson.decodeRequest("""{"type":"storage.remove","id":"1","key":"k"}"""))
        assertEquals(NappletRequest.StorageKeys(), NappletProtocolJson.decodeRequest("""{"type":"storage.keys","id":"1"}"""))
        assertEquals(
            NappletRequest.StorageGet("k", NappletStorageScope.INSTANCE),
            NappletProtocolJson.decodeRequest("""{"type":"storage.get","id":"1","key":"k","scope":"instance"}"""),
        )

        // StorageGetResultMessage.value, StorageKeysResultMessage.keys
        assertTrue(result("storage.get", NappletResponse.StorageValue("v")).containsKey("value"))
        assertEquals(2, result("storage.keys", NappletResponse.Strings(listOf("a", "b")))["keys"]?.jsonArray?.size)
    }

    // ---------- resource (Resource*Message) ----------

    @Test
    fun resourceBytesRequestAndResultMatch() {
        assertEquals(NappletRequest.ResourceInfo, NappletProtocolJson.decodeRequest("""{"type":"resource.info","id":"0"}"""))
        assertEquals(NappletRequest.ResourceBytes("https://x"), NappletProtocolJson.decodeRequest("""{"type":"resource.bytes","id":"1","url":"https://x"}"""))
        assertEquals(
            NappletRequest.ResourceBytesMany(listOf("https://x", "data:text/plain,hi")),
            NappletProtocolJson.decodeRequest("""{"type":"resource.bytesMany","id":"2","urls":["https://x","data:text/plain,hi"]}"""),
        )
        // The wire carries canonical base64; the web binding rebuilds the Blob.
        val o = result("resource.bytes", NappletResponse.Bytes("Hi".encodeToByteArray(), "text/plain"))
        assertEquals("SGk=", o["blob"]?.jsonPrimitive?.content)
        assertEquals("text/plain", o["mime"]?.jsonPrimitive?.content)
    }

    // ---------- error convention ----------

    @Test
    fun failuresExposeAnErrorFieldTheSdkShimRejectsOn() {
        // The SDK shim rejects when `error` is present, regardless of domain.
        assertTrue(result("relay.publish", NappletResponse.Denied(NappletCapability.RELAY, "no")).containsKey("error"))
        assertTrue(result("identity.getProfile", NappletResponse.Unsupported("identity.getProfile")).containsKey("error"))
        assertTrue(result("relay.query", NappletResponse.Failed("boom")).containsKey("error"))
    }

    // ---------- keys (keyboard/command actions) ----------

    @Test
    fun keysRegisterAndUnregisterActionsConform() {
        // keys.registerAction{action:{id,label,defaultKey?}} → RegisterAction; result carries actionId + binding.
        assertEquals(
            NappletRequest.RegisterAction("save", "Save"),
            NappletProtocolJson.decodeRequest("""{"type":"keys.registerAction","id":"1","action":{"id":"save","label":"Save"}}"""),
        )
        assertEquals(
            NappletRequest.RegisterAction("save", "Save", "Ctrl+S"),
            NappletProtocolJson.decodeRequest("""{"type":"keys.registerAction","id":"1","action":{"id":"save","label":"Save","defaultKey":"Ctrl+S"}}"""),
        )
        assertEquals(
            NappletRequest.UnregisterAction("save"),
            NappletProtocolJson.decodeRequest("""{"type":"keys.unregisterAction","actionId":"save"}"""),
        )
        val registered = result("keys.registerAction", NappletResponse.ActionRegistered("save", "Ctrl+S"))
        assertEquals("save", registered["actionId"]?.jsonPrimitive?.content)
        assertEquals("Ctrl+S", registered["binding"]?.jsonPrimitive?.content)
    }

    // ---------- upload (UploadUploadMessage / UploadResult) ----------

    @Test
    fun uploadUploadWireConforms() {
        // upload.upload{request:{data,mimeType,filename}} — shell.html inlines the Blob as dataBase64.
        val up = NappletProtocolJson.decodeRequest("""{"type":"upload.upload","id":"1","request":{"dataBase64":"SGk=","mimeType":"image/png","filename":"a.png"}}""") as NappletRequest.UploadBlob
        assertEquals("image/png", up.contentType)
        assertEquals("a.png", up.filename)

        // NAP-UPLOAD: upload.upload.result{ result: UploadResult{ ok, uploadId, status, rail, url, sha256, nip94 } }
        val o = result("upload.upload", NappletResponse.Uploaded("https://b/abc", "abc", 2L, "image/png"))["result"]!!.jsonObject
        assertEquals("https://b/abc", o["url"]?.jsonPrimitive?.content)
        assertEquals("abc", o["sha256"]?.jsonPrimitive?.content)
        assertEquals("abc", o["uploadId"]?.jsonPrimitive?.content)
        assertEquals("complete", o["status"]?.jsonPrimitive?.content)
        assertEquals("blossom", o["rail"]?.jsonPrimitive?.content)
        assertTrue(o["nip94"]!!.jsonArray.any { it.jsonArray[0].jsonPrimitive.content == "x" && it.jsonArray[1].jsonPrimitive.content == "abc" })

        // upload.status.result carries the same record as `status`, with updatedAt.
        val status = result("upload.status", NappletResponse.Uploaded("https://b/abc", "abc", updatedAt = 7))["status"]!!.jsonObject
        assertEquals(7L, status["updatedAt"]?.jsonPrimitive?.content?.toLong())

        // upload.info.result{ info: { rails: [{ rail, enabled, returns }], maxBytes } }
        val info = result("upload.info", NappletResponse.UploadInfo(enabled = true, maxBytes = 10))["info"]!!.jsonObject
        assertEquals(
            "blossom",
            info["rails"]!!
                .jsonArray[0]
                .jsonObject["rail"]
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(10L, info["maxBytes"]?.jsonPrimitive?.content?.toLong())

        // No upload created: a top-level error string only.
        val tooLarge = result("upload.upload", NappletResponse.Failed("file too large"))
        assertEquals("file too large", tooLarge["error"]?.jsonPrimitive?.content)
        assertFalse(tooLarge.containsKey("result"))
    }

    // ---------- still-open gaps ----------

    @Test
    fun incIsTheBusNotTheBroker() {
        // NAP-INC goes to the host's inc bus, never to the broker: the broker decodes no inc.* request.
        assertNull(NappletProtocolJson.decodeRequest("""{"type":"inc.emit","topic":"t"}"""))
        assertEquals(NappletIncRequest.Emit("t", null), NappletProtocolJson.decodeIncRequest("""{"type":"inc.emit","topic":"t"}"""))
    }
}
