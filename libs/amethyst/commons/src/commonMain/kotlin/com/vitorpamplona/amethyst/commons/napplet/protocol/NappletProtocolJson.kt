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
package com.vitorpamplona.amethyst.commons.napplet.protocol

import com.vitorpamplona.amethyst.commons.napplet.OutboxEvent
import com.vitorpamplona.amethyst.commons.napplet.OutboxRelayPlan
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsAccept
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsError
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsPickOptions
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64

/**
 * Marshals the KMP-pure [NappletRequest] / [NappletResponse] types to and from the wire the
 * applet exchanges over `window.napplet.*`. The envelope matches the upstream napplet SDK
 * (`@napplet/shim` / `@napplet/nap`): requests are `{ "type": "<domain>.<action>", "id", ...fields }`
 * and replies are `{ "type": "<domain>.<action>.result", "id", ...fields }`.
 *
 * Lives in `commons/commonMain` (not in any single front end) because the wire contract is identical
 * across hosts: the Android `:napplet` WebView host and a future desktop host both marshal through
 * here. It depends only on `quartz` (Event/Filter), kotlinx.serialization and `kotlin.io.encoding`
 * — no platform-UI or process APIs.
 *
 * This is the only place the boundary parses untrusted applet input, so it is deliberately
 * strict: an unrecognized `type` decodes to `null` (the router silently ignores it) and a malformed/short
 * body throws (the host wraps it into a `Failed`). Uses kotlinx.serialization so it is
 * unit-testable off-device.
 */
object NappletProtocolJson {
    fun encodeThemeChanged(
        background: String,
        text: String,
        primary: String,
    ): String =
        buildJsonObject {
            put("type", "theme.changed")
            putJsonObject("theme") {
                putJsonObject("colors") {
                    put("background", background)
                    put("text", text)
                    put("primary", primary)
                }
            }
        }.toString()

    private val json = Json { ignoreUnknownKeys = true }

    // Accepts unpadded input, as java.util.Base64's decoder did before this moved to commonMain.
    // One difference remains: non-zero pad bits ("SGl=") are rejected where Java ignored them.
    // Browsers' btoa/readAsDataURL never produce those, so only a hand-crafted payload hits it.
    private val lenientBase64 = Base64.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

    /** The `type` discriminant of a request envelope, used to build the matching `.result` type. */
    fun readType(envelopeJson: String): String? = json.parseToJsonElement(envelopeJson).jsonObject.str("type")

    /** The request `id`, when the envelope expects a correlated reply. */
    fun readId(envelopeJson: String): String? = json.parseToJsonElement(envelopeJson).jsonObject.str("id")

    /** The `subId` of a subscription request, used to key the `relay.event`/`relay.eose` pushes back to it. */
    fun readSubId(envelopeJson: String): String? = json.parseToJsonElement(envelopeJson).jsonObject.str("subId")

    /** A `relay.event` push carrying the NAP-RELAY `RelayEventResult` wrapper. */
    fun encodeRelayEvent(
        subId: String,
        event: Event,
    ): String =
        buildJsonObject {
            put("type", "relay.event")
            put("subId", subId)
            putJsonObject("result") {
                put("event", json.parseToJsonElement(event.toJson()))
            }
        }.toString()

    /** An `outbox.event` push: the NAP-RELAY `RelayEventResult`, with the relays the event came from. */
    fun encodeOutboxEvent(
        subId: String,
        event: Event,
        relayHints: List<String>,
    ): String =
        buildJsonObject {
            put("type", "outbox.event")
            put("subId", subId)
            put("result", relayEventResult(OutboxEvent(event, relayHints)))
        }.toString()

    /** An `outbox.closed` push: the shell ended the outbox subscription [subId]. */
    fun encodeOutboxClosed(
        subId: String,
        reason: String?,
    ): String =
        buildJsonObject {
            put("type", "outbox.closed")
            put("subId", subId)
            reason?.let { put("reason", it) }
        }.toString()

    /** NAP-RELAY's `RelayEventResult`: the event, and a sidecar naming where it was seen. */
    private fun relayEventResult(found: OutboxEvent) =
        buildJsonObject {
            put("event", json.parseToJsonElement(found.event.toJson()))
            if (found.relayHints.isNotEmpty()) {
                putJsonObject("sidecar") { putJsonArray("relayHints") { found.relayHints.forEach { add(it) } } }
            }
        }

    /** A `relay.eose` push: signals end-of-stored-events for the subscription [subId]. */
    fun encodeRelayEose(subId: String): String =
        buildJsonObject {
            put("type", "relay.eose")
            put("subId", subId)
        }.toString()

    /**
     * The NAP-INC request in [envelopeJson], or null when its type is not an `inc.*` operation or a
     * field it needs is missing or not text. A `payload` stays raw JSON text, null when absent.
     */
    fun decodeIncRequest(envelopeJson: String): NappletIncRequest? {
        val o = json.parseToJsonElement(envelopeJson) as? JsonObject ?: return null
        val payload = o["payload"]?.toString()
        return when (o.str("type")) {
            // NAP-INC gives a subscribe an id for its result; one sent without still subscribes.
            "inc.subscribe" -> NappletIncRequest.Subscribe(o.textOrNull("id").orEmpty(), o.textOrNull("topic") ?: return null)
            "inc.unsubscribe" -> NappletIncRequest.Unsubscribe(o.textOrNull("topic") ?: return null)
            "inc.emit" -> NappletIncRequest.Emit(o.textOrNull("topic") ?: return null, payload)
            "inc.channel.open" -> NappletIncRequest.ChannelOpen(o.textOrNull("id") ?: return null, o.textOrNull("target") ?: return null)
            "inc.channel.emit" -> NappletIncRequest.ChannelEmit(o.textOrNull("channelId") ?: return null, payload)
            "inc.channel.broadcast" -> NappletIncRequest.ChannelBroadcast(payload)
            "inc.channel.list" -> NappletIncRequest.ChannelList(o.textOrNull("id") ?: return null)
            "inc.channel.close" -> NappletIncRequest.ChannelClose(o.textOrNull("channelId") ?: return null)
            else -> null
        }
    }

    /** The `intent.*` request types [decodeIntentRequest] knows. */
    val INTENT_REQUEST_TYPES = setOf("intent.invoke", "intent.available", "intent.handlers", "intent.ready")

    /**
     * The NAP-INTENT request in [envelopeJson], or null when it is not one or a field it needs is
     * missing. The invoke's `request.payload` stays raw JSON.
     */
    fun decodeIntentRequest(envelopeJson: String): NappletIntentRequest? {
        val o = json.parseToJsonElement(envelopeJson) as? JsonObject ?: return null
        return when (o.str("type")) {
            "intent.invoke" -> {
                val request = o["request"] as? JsonObject ?: return null
                val hint = request["handlerHint"] as? JsonObject
                NappletIntentRequest.Invoke(
                    id = o.textOrNull("id") ?: return null,
                    archetype = request.textOrNull("archetype") ?: return null,
                    action = request.textOrNull("action") ?: return null,
                    convention = request.textOrNull("convention") ?: return null,
                    payload = request["payload"]?.toString(),
                    handler = request.textOrNull("handler"),
                    hintAddress = hint?.textOrNull("address"),
                )
            }
            "intent.available" -> NappletIntentRequest.Available(o.textOrNull("id") ?: return null, o.textOrNull("archetype") ?: return null)
            "intent.handlers" -> NappletIntentRequest.Handlers(o.textOrNull("id") ?: return null)
            "intent.ready" -> NappletIntentRequest.Ready
            else -> null
        }
    }

    /** An `intent.invoke.result` (the host adds the `id`): accepted for [handler], or refused with [error]. */
    fun encodeIntentInvokeResult(
        request: NappletIntentRequest.Invoke,
        handler: String?,
        error: String? = null,
    ): String =
        buildJsonObject {
            put("type", "intent.invoke.result")
            putJsonObject("result") {
                if (error != null || handler == null) {
                    put("ok", false)
                    put("error", error ?: "no handler")
                } else {
                    put("ok", true)
                    put("archetype", request.archetype)
                    put("action", request.action)
                    put("convention", request.convention)
                    put("handler", handler)
                }
            }
        }.toString()

    /** An `intent.available.result` (the host adds the `id`) carrying one [availability]. */
    fun encodeIntentAvailableResult(availability: IntentAvailability): String =
        buildJsonObject {
            put("type", "intent.available.result")
            put("availability", availabilityJson(availability))
        }.toString()

    /** An `intent.handlers.result` (the host adds the `id`) carrying every archetype's availability. */
    fun encodeIntentHandlersResult(handlers: List<IntentAvailability>): String =
        buildJsonObject {
            put("type", "intent.handlers.result")
            putJsonArray("handlers") { handlers.forEach { add(availabilityJson(it)) } }
        }.toString()

    /** The `<requestType>.result` for an intent request refused with [error] (no `intent` declared, malformed). */
    fun encodeIntentFailure(
        requestType: String,
        error: String,
    ): String =
        buildJsonObject {
            put("type", "$requestType.result")
            when (requestType) {
                "intent.invoke" ->
                    putJsonObject("result") {
                        put("ok", false)
                        put("error", error)
                    }
                else -> put("error", error)
            }
        }.toString()

    /** An `intent.deliver` push to the target: the convention, its payload, and the runtime-attested [sender]. */
    fun encodeIntentDeliver(
        sender: String,
        archetype: String,
        action: String,
        convention: String,
        payload: String?,
    ): String =
        buildJsonObject {
            put("type", "intent.deliver")
            putJsonObject("delivery") {
                put("sender", sender)
                put("archetype", archetype)
                put("action", action)
                put("convention", convention)
                putRawPayload(payload)
            }
        }.toString()

    private fun availabilityJson(availability: IntentAvailability) =
        buildJsonObject {
            put("archetype", availability.archetype)
            put("available", availability.candidates.isNotEmpty())
            putJsonArray("candidates") {
                availability.candidates.forEach { candidate ->
                    addJsonObject {
                        put("id", candidate.id)
                        candidate.title?.let { put("title", it) }
                        putJsonArray("actions") {
                            candidate.contracts
                                .map { it.action }
                                .distinct()
                                .forEach { add(it) }
                        }
                        putJsonArray("conventions") {
                            candidate.contracts
                                .map { it.convention }
                                .distinct()
                                .forEach { add(it) }
                        }
                        putJsonArray("contracts") {
                            candidate.contracts.forEach { contract ->
                                addJsonObject {
                                    put("convention", contract.convention)
                                    putJsonArray("params") { contract.params.forEach { add(it) } }
                                }
                            }
                        }
                        put("isDefault", candidate.isDefault)
                    }
                }
            }
            put("hasDefault", availability.candidates.any { it.isDefault })
        }

    /** The `inc.*` request types [decodeIncRequest] knows. */
    val INC_REQUEST_TYPES =
        setOf(
            "inc.subscribe",
            "inc.unsubscribe",
            "inc.emit",
            "inc.channel.open",
            "inc.channel.emit",
            "inc.channel.broadcast",
            "inc.channel.list",
            "inc.channel.close",
        )

    /** The `<requestType>.result` (the host adds the `id`) for an `inc.*` request refused with [error]. */
    fun encodeIncFailure(
        requestType: String,
        error: String,
    ): String =
        buildJsonObject {
            put("type", "$requestType.result")
            put("error", error)
        }.toString()

    /** An `inc.subscribe.result` (the host adds the `id`); an [error] means the subscription was not registered. */
    fun encodeIncSubscribeResult(error: String? = null): String =
        buildJsonObject {
            put("type", "inc.subscribe.result")
            error?.let { put("error", it) }
        }.toString()

    /**
     * An `inc.event` push: [topic]'s [payload] (raw JSON, omitted when null) for a subscriber, from
     * the runtime-assigned endpoint identifier [sender] of the napplet that emitted it.
     */
    fun encodeIncEvent(
        topic: String,
        payload: String?,
        sender: String,
    ): String =
        buildJsonObject {
            put("type", "inc.event")
            put("topic", topic)
            put("sender", sender)
            putRawPayload(payload)
        }.toString()

    /** An `inc.channel.open.result` (the host adds the `id`): the channel and its peer, or the [error] that kept it closed. */
    fun encodeIncChannelOpenResult(
        channelId: String?,
        peer: String?,
        error: String? = null,
    ): String =
        buildJsonObject {
            put("type", "inc.channel.open.result")
            channelId?.let { put("channelId", it) }
            peer?.let { put("peer", it) }
            error?.let { put("error", it) }
        }.toString()

    /** An `inc.channel.opened` push to the target of a new channel; [peer] is the opener's endpoint identifier. */
    fun encodeIncChannelOpened(
        channelId: String,
        peer: String,
    ): String =
        buildJsonObject {
            put("type", "inc.channel.opened")
            put("channelId", channelId)
            put("peer", peer)
        }.toString()

    /** An `inc.channel.event` push: [payload] (raw JSON, omitted when null) from the channel peer [sender]. */
    fun encodeIncChannelEvent(
        channelId: String,
        sender: String,
        payload: String?,
    ): String =
        buildJsonObject {
            put("type", "inc.channel.event")
            put("channelId", channelId)
            put("sender", sender)
            putRawPayload(payload)
        }.toString()

    /** An `inc.channel.list.result` (the host adds the `id`): each open channel as `{id, peer}`. */
    fun encodeIncChannelListResult(channels: List<Pair<String, String>>): String =
        buildJsonObject {
            put("type", "inc.channel.list.result")
            putJsonArray("channels") {
                channels.forEach { (id, peer) ->
                    addJsonObject {
                        put("id", id)
                        put("peer", peer)
                    }
                }
            }
        }.toString()

    /** An `inc.channel.closed` push: [channelId] is over, for the [reason] given (if any). */
    fun encodeIncChannelClosed(
        channelId: String,
        reason: String? = null,
    ): String =
        buildJsonObject {
            put("type", "inc.channel.closed")
            put("channelId", channelId)
            reason?.let { put("reason", it) }
        }.toString()

    // The payload came in as JSON the napplet sent, and goes out as the same value.
    private fun JsonObjectBuilder.putRawPayload(payload: String?) {
        if (payload == null) return
        put("payload", runCatching { json.parseToJsonElement(payload) }.getOrDefault(JsonNull))
    }

    // A text field, or null when it is absent or not a JSON string.
    private fun JsonObject.textOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** A `keys.action` push: the user triggered the registered keyboard/command action [actionId]. */
    fun encodeKeysAction(actionId: String): String =
        buildJsonObject {
            put("type", "keys.action")
            put("actionId", actionId)
        }.toString()

    /**
     * An `identity.changed` push: the active user's public key changed (account switch / connect /
     * disconnect). [pubkey] is the new hex key, or `""` when no account is signed in.
     */
    fun encodeIdentityChanged(pubkey: String): String =
        buildJsonObject {
            put("type", "identity.changed")
            put("pubkey", pubkey)
        }.toString()

    /** A `relay.closed` push: a relay (or the shell) ended the subscription [subId]. */
    fun encodeRelayClosed(
        subId: String,
        reason: String,
    ): String =
        buildJsonObject {
            put("type", "relay.closed")
            put("subId", subId)
            put("reason", reason)
        }.toString()

    /** Parses a request envelope. Returns `null` for an unrecognized `type` so the router can ignore it. */
    fun decodeRequest(envelopeJson: String): NappletRequest? {
        val o = json.parseToJsonElement(envelopeJson).jsonObject
        return when (o.str("type")) {
            "theme.get" -> NappletRequest.ThemeGet
            "identity.getPublicKey" -> NappletRequest.GetPublicKey
            "relay.publish" -> {
                val t = o.eventTemplate()
                NappletRequest.Publish(kind = t.kindOf(), tags = decodeTags(t), content = t.str("content") ?: "")
            }
            "relay.publishEncrypted" -> {
                val t = o.eventTemplate()
                NappletRequest.PublishEncrypted(
                    kind = t.kindOf(),
                    tags = decodeTags(t),
                    content = t.str("content") ?: "",
                    recipient = o.req("recipient"),
                    encryption = o.str("encryption") ?: "nip44",
                )
            }
            "relay.query" -> NappletRequest.QueryEvents(decodeFilterList(o))
            "outbox.getEvent" -> {
                val options = o.options()
                NappletRequest.OutboxGetEvent(o.req("eventId"), options?.str("author"), options?.strList("relays").orEmpty(), options?.long("timeoutMs"))
            }
            "outbox.query" -> {
                val options = o.options()
                NappletRequest.OutboxQuery(
                    decodeFilterList(o),
                    options?.strList("authors").orEmpty(),
                    options?.strList("relays").orEmpty(),
                    options?.get("limit")?.jsonPrimitive?.int,
                    options?.long("timeoutMs"),
                )
            }
            "outbox.subscribe" -> {
                val options = o.options()
                NappletRequest.OutboxSubscribe(decodeFilterList(o), options?.strList("authors").orEmpty(), options?.strList("relays").orEmpty())
            }
            "outbox.publish" -> {
                val t = o.eventTemplate()
                val options = o.options()
                NappletRequest.OutboxPublish(
                    NappletRequest.Publish(kind = t.kindOf(), tags = decodeTags(t), content = t.str("content") ?: ""),
                    toOutbox = options?.get("toOutbox")?.jsonPrimitive?.booleanOrNull ?: true,
                    toInboxes = options?.strList("toInboxes").orEmpty(),
                    relays = options?.strList("relays").orEmpty(),
                )
            }
            "outbox.resolveRelays" -> {
                val target = o["target"]?.jsonObject
                val authors = (target?.strList("authors").orEmpty() + listOfNotNull(target?.str("pubkey"))).distinct()
                // An author's own relays are where they write; "read" asks for their inboxes.
                NappletRequest.OutboxResolveRelays(authors, write = target?.str("direction") != "read")
            }
            "relay.subscribe" -> NappletRequest.Subscribe(decodeFilterList(o))
            "nostr.signEvent" -> {
                // NIP-07 signEvent: sign-only, honoring the app-supplied created_at (fallback: now).
                val t = o.eventTemplate()
                NappletRequest.SignEvent(
                    kind = t.kindOf(),
                    tags = decodeTags(t),
                    content = t.str("content") ?: "",
                    createdAt = t["created_at"]?.jsonPrimitive?.long ?: TimeUtils.now(),
                )
            }
            // NIP-07 nip44.encrypt/decrypt: crypto only, no publish. `peer` is the counterparty
            // pubkey the NIP-07 call names as its first argument.
            "nostr.nip44Encrypt" -> NappletRequest.Nip44Encrypt(peer = o.req("peer"), plaintext = o.str("plaintext") ?: "")
            "nostr.nip44Decrypt" -> NappletRequest.Nip44Decrypt(peer = o.req("peer"), ciphertext = o.str("ciphertext") ?: "")
            "storage.get" -> NappletRequest.StorageGet(o.req("key"), o.storageScope())
            "storage.set" -> NappletRequest.StorageSet(o.req("key"), o.req("value"), o.storageScope())
            "storage.remove" -> NappletRequest.StorageRemove(o.req("key"), o.storageScope())
            "storage.keys" -> NappletRequest.StorageKeys(o.storageScope())
            "notify.create" -> NappletRequest.NotifyCreate(o.str("title") ?: "", o.str("body") ?: "")
            "notify.list" -> NappletRequest.NotifyList
            "notify.dismiss" -> NappletRequest.NotifyDismiss(o.str("notificationId") ?: o.str("id") ?: "")
            "keys.registerAction" -> {
                val action = o.getValue("action").jsonObject
                NappletRequest.RegisterAction(action.req("id"), action.str("label") ?: "", action.str("defaultKey"))
            }
            "keys.unregisterAction" -> NappletRequest.UnregisterAction(o.req("actionId"))
            "value.payInvoice" -> NappletRequest.PayInvoice(o.req("invoice"))
            "resource.info" -> NappletRequest.ResourceInfo
            "resource.bytes" ->
                NappletRequest.ResourceBytes(
                    o.getValue("url").jsonPrimitive.let {
                        require(it.isString)
                        it.content
                    },
                )
            "resource.bytesMany" ->
                NappletRequest.ResourceBytesMany(
                    o.getValue("urls").jsonArray.map { value ->
                        value.jsonPrimitive.let {
                            require(it.isString)
                            it.content
                        }
                    },
                )
            "fs.info" -> NappletRequest.FsInfo
            "fs.pickFile" -> NappletRequest.FsPick(NappletRequest.FsPickKind.FILE, o.fsPickOptions())
            "fs.pickFiles" -> NappletRequest.FsPick(NappletRequest.FsPickKind.FILES, o.fsPickOptions())
            "fs.pickDirectory" -> NappletRequest.FsPick(NappletRequest.FsPickKind.DIRECTORY, o.fsPickOptions())
            "fs.pickSaveFile" -> NappletRequest.FsPick(NappletRequest.FsPickKind.SAVE_FILE, o.fsPickOptions())
            "fs.stat" -> NappletRequest.FsStat(o.text("path"))
            "fs.list" -> NappletRequest.FsList(o.text("path"))
            "fs.read" -> {
                val options = o.options()
                NappletRequest.FsRead(o.text("path"), options?.integer("offset"), options?.integer("length"))
            }
            "fs.write" -> {
                val options = o.options()
                NappletRequest.FsWrite(
                    path = o.text("path"),
                    data = o.text("data"),
                    mode = options?.optionalText("mode"),
                    offset = options?.integer("offset"),
                    ifRevision = options?.optionalText("ifRevision"),
                    ifAbsent = options?.get("ifAbsent")?.let { (it as JsonPrimitive).booleanOrNull ?: throw IllegalArgumentException("ifAbsent") },
                )
            }
            "fs.mkdir" -> NappletRequest.FsMkdir(o.text("path"))
            "fs.remove" -> NappletRequest.FsRemove(o.text("path"))
            "fs.move" -> NappletRequest.FsMove(o.text("fromPath"), o.text("toPath"))
            "fs.watch" -> NappletRequest.FsWatch(o.text("path"))
            "fs.unwatch" -> NappletRequest.FsUnwatch(o.text("watchId"))
            "upload.upload" -> {
                // UploadUploadMessage: { type, id, request: { data, rail?, mimeType?, filename?, ... } }.
                // The binding sends the Blob in `request.data` as base64 `request.dataBase64`.
                val request = o.getValue("request").jsonObject
                NappletRequest.UploadBlob(
                    bytes = lenientBase64.decode(request.req("dataBase64")),
                    contentType = request.str("mimeType") ?: "application/octet-stream",
                    filename = request.str("filename"),
                    rail = request.str("rail"),
                )
            }
            "common.encodeNip19" -> {
                val input = o.getValue("input").jsonObject
                NappletRequest.CommonEncodeNip19(
                    type = input.text("type"),
                    hex = input.optionalText("hex"),
                    pubkey = input.optionalText("pubkey"),
                    eventId = input.optionalText("eventId"),
                    identifier = input.optionalText("identifier"),
                    kind = input.integer("kind")?.toInt(),
                    author = input.optionalText("author"),
                    relay = input.optionalText("relay"),
                    relays = input.strList("relays") ?: emptyList(),
                )
            }
            "common.decodeNip19" -> NappletRequest.CommonDecodeNip19(o.text("value"))
            "common.getProfile" -> NappletRequest.CommonGetProfile(o.text("target"))
            "common.follows" -> NappletRequest.CommonFollows
            "common.follow", "common.unfollow" ->
                NappletRequest.CommonFollow(
                    // One npub or several.
                    pubkeys = o.strList("pubkeys") ?: listOfNotNull(o.optionalText("pubkeys")),
                    follow = o.str("type") == "common.follow",
                )
            "common.react" -> NappletRequest.CommonReact(o.text("targetEventId"), o.text("reaction"), o.optionalText("customEmojiHref"))
            "common.report" -> {
                val target = o.getValue("target").jsonObject
                when (target.str("type")) {
                    "event" -> NappletRequest.CommonReport(target.text("id"), target.optionalText("pubkey"), target.optionalText("relay"), o.text("reason"), o.optionalText("text") ?: "")
                    "pubkey" -> NappletRequest.CommonReport(null, target.text("pubkey"), target.optionalText("relay"), o.text("reason"), o.optionalText("text") ?: "")
                    else -> throw IllegalArgumentException("target.type is not event or pubkey")
                }
            }
            "upload.info" -> NappletRequest.UploadInfo
            "upload.status" -> NappletRequest.UploadStatus(o.text("uploadId"))
            else -> {
                // Any other identity.* read (getProfile/getRelays/getFollows/getList/...) routes through
                // a generic IdentityRead; the broker/gateway decides which are implemented.
                val type = o.str("type")
                if (type != null && type in IDENTITY_READ_TYPES) {
                    NappletRequest.IdentityRead(type.removePrefix("identity."), o.str("listType") ?: o.str("argument"))
                } else {
                    null
                }
            }
        }
    }

    /** Builds the `{type:"<requestType>.result", ok, ...}` reply (the host adds the `id`). */
    fun encodeResponse(
        requestType: String,
        response: NappletResponse,
    ): String {
        if (requestType.startsWith("outbox.")) outboxFailure(requestType, response)?.let { return it }
        if (requestType.startsWith(FS_PREFIX)) return encodeFsResponse(requestType, response)
        if (requestType.startsWith(UPLOAD_PREFIX)) encodeUploadResponse(requestType, response)?.let { return it }
        if (requestType.startsWith(COMMON_PREFIX)) return encodeCommonResponse(requestType, response)
        if (requestType in setOf("resource.info", "resource.bytes", "resource.bytesMany")) {
            val failure =
                when (response) {
                    is NappletResponse.Denied -> NappletResponse.ResourceFailure("blocked-by-policy", response.reason)
                    is NappletResponse.Unsupported -> NappletResponse.ResourceFailure("blocked-by-policy", "Resource operation unavailable.")
                    is NappletResponse.Failed -> NappletResponse.ResourceFailure("network-error", response.reason)
                    else -> null
                }
            if (failure != null) return encodeResponse(requestType, failure)
        }
        return buildJsonObject {
            // Most replies are `<requestType>.result`; a few NAP domains use a bespoke past-tense
            // reply type the client listens for instead (e.g. notify.create → notify.created).
            val responseType =
                when (response) {
                    is NappletResponse.NotifyCreated -> "notify.created"
                    is NappletResponse.NotifyListed -> "notify.listed"
                    is NappletResponse.ResourceFailure -> "$requestType.error"
                    else -> "$requestType.result"
                }
            put("type", responseType)
            when (response) {
                is NappletResponse.PublicKey -> {
                    put("ok", true)
                    put("pubkey", response.pubkey)
                }
                is NappletResponse.Published -> {
                    // Upstream resolves publish() to the signed NostrEvent; relays are an extra.
                    put("ok", true)
                    put("event", json.parseToJsonElement(response.event.toJson()))
                    put("eventId", response.event.id)
                    put("relays", buildJsonArray { response.relays.forEach { add(it) } })
                }
                is NappletResponse.Events -> {
                    put("ok", true)
                    putJsonArray("events") {
                        response.events.forEach { event ->
                            addJsonObject { put("event", json.parseToJsonElement(event.toJson())) }
                        }
                    }
                }
                is NappletResponse.ActionRegistered -> {
                    put("ok", true)
                    put("actionId", response.actionId)
                    response.binding?.let { put("binding", it) }
                }
                is NappletResponse.StorageValue -> {
                    put("ok", true)
                    put("value", response.value)
                }
                is NappletResponse.Text -> {
                    put("ok", true)
                    put("value", response.value)
                }
                is NappletResponse.Strings -> {
                    put("ok", true)
                    // storage.keys returns `keys`; other string-list reads use `values`.
                    val field = if (requestType == "storage.keys") "keys" else "values"
                    put(field, buildJsonArray { response.values.forEach { add(it) } })
                }
                is NappletResponse.Json -> {
                    put("ok", true)
                    // identity.* reads return method-specific fields (profile/relays/pubkeys/entries/...).
                    // The host already serialized the value; embed it (fall back to null if malformed).
                    put(identityResultField(requestType), runCatching { json.parseToJsonElement(response.raw) }.getOrDefault(JsonNull))
                }
                is NappletResponse.Bytes -> {
                    put("ok", true)
                    put("blob", Base64.encode(response.bytes))
                    put("mime", response.contentType)
                }
                is NappletResponse.ResourceInfo -> {
                    put("ok", true)
                    putJsonObject("info") {
                        putJsonArray("schemes") {
                            response.schemes.forEach { scheme ->
                                addJsonObject {
                                    put("scheme", scheme)
                                    put("enabled", true)
                                }
                            }
                        }
                        put("maxBytes", response.maxBytes)
                        put("maxUrls", response.maxUrls)
                    }
                }
                is NappletResponse.ResourceItems -> {
                    put("ok", true)
                    putJsonArray("items") {
                        response.items.forEach { item ->
                            addJsonObject {
                                put("url", item.url)
                                put("ok", item.resource != null)
                                item.resource?.let {
                                    put("blob", Base64.encode(it.bytes))
                                    put("mime", it.contentType)
                                }
                                item.error?.let { put("error", it) }
                                item.message?.let { put("message", it) }
                            }
                        }
                    }
                }
                is NappletResponse.ResourceFailure -> {
                    put("error", response.error)
                    response.message?.let { put("message", it) }
                }
                // Shaped by encodeUploadResponse / encodeCommonResponse for their domains; nothing else answers with these.
                is NappletResponse.Uploaded, is NappletResponse.UploadInfo,
                is NappletResponse.CommonEncoded, is NappletResponse.CommonDecoded, is NappletResponse.CommonProfile,
                is NappletResponse.CommonFollowList, is NappletResponse.CommonUnchanged,
                -> put("ok", true)
                is NappletResponse.CommonFailure -> {
                    put("ok", false)
                    put("error", response.error)
                }
                is NappletResponse.Paid -> {
                    put("ok", true)
                    put("preimage", response.preimage)
                }
                is NappletResponse.Theme -> {
                    put("ok", true)
                    putJsonObject("theme") {
                        putJsonObject("colors") {
                            put("background", response.background)
                            put("text", response.text)
                            put("primary", response.primary)
                        }
                    }
                }
                is NappletResponse.NotifyCreated -> {
                    put("ok", true)
                    put("id", response.id)
                }
                is NappletResponse.NotifyListed -> {
                    put("ok", true)
                    put(
                        "notifications",
                        buildJsonArray {
                            response.notifications.forEach {
                                add(
                                    buildJsonObject {
                                        put("id", it.id)
                                        put("title", it.title)
                                        put("body", it.body)
                                    },
                                )
                            }
                        },
                    )
                }
                is NappletResponse.Done -> {
                    put("ok", true)
                }
                // Subscribe is acknowledged here, but the host streams pushes instead of sending this.
                is NappletResponse.Subscribed -> {
                    put("ok", true)
                }
                is NappletResponse.OutboxSubscribed -> {
                    put("ok", true)
                }
                is NappletResponse.OutboxFound -> {
                    response.found?.let { put("result", relayEventResult(it)) }
                    if (response.incomplete) put("incomplete", true)
                    response.error?.let { put("error", it) }
                }
                is NappletResponse.OutboxEvents -> {
                    putJsonArray("events") { response.events.forEach { add(relayEventResult(it)) } }
                    if (response.incomplete) put("incomplete", true)
                }
                is NappletResponse.OutboxPublished -> {
                    put("ok", response.ok)
                    response.event?.let {
                        put("event", json.parseToJsonElement(it.toJson()))
                        put("eventId", it.id)
                    }
                    putJsonObject("relays") { response.relays.forEach { (url, accepted) -> put(url, accepted) } }
                    response.error?.let { put("error", it) }
                }
                is NappletResponse.OutboxPlan -> {
                    putJsonObject("plan") {
                        putJsonArray("relays") { response.plan.relays.forEach { add(it) } }
                        put("source", response.plan.source.wire)
                        if (response.plan.missingAuthors.isNotEmpty()) putJsonArray("missingAuthors") { response.plan.missingAuthors.forEach { add(it) } }
                    }
                }
                // Only an fs.* request gets one, and those go out through encodeFsResponse.
                is NappletResponse.Fs -> {
                    put("ok", false)
                    put("error", "unsupported")
                }
                is NappletResponse.Denied -> {
                    put("ok", false)
                    put("error", "denied")
                    put("capability", response.capability.name)
                    put("reason", response.reason)
                }
                is NappletResponse.Unsupported -> {
                    put("ok", false)
                    put("error", "unsupported")
                    put("operation", response.operation)
                }
                is NappletResponse.Failed -> {
                    put("ok", false)
                    put("error", "failed")
                    put("reason", response.reason)
                }
            }
        }.toString()
    }

    /**
     * An outbox request refused or failed, in NAP-OUTBOX's own result shape (`error` plus the fields
     * the result always has), or null for any other response.
     */
    private fun outboxFailure(
        requestType: String,
        response: NappletResponse,
    ): String? {
        val error =
            when (response) {
                // NAP-OUTBOX's own word for a refused publish.
                is NappletResponse.Denied -> if (requestType == "outbox.publish") "publish denied" else "policy denied"
                is NappletResponse.Unsupported -> "policy denied"
                is NappletResponse.Failed -> response.reason
                else -> return null
            }
        return buildJsonObject {
            put("type", "$requestType.result")
            when (requestType) {
                "outbox.query" -> putJsonArray("events") {}
                "outbox.publish" -> put("ok", false)
                "outbox.resolveRelays" ->
                    putJsonObject("plan") {
                        putJsonArray("relays") {}
                        put("source", OutboxRelayPlan.Source.POLICY.wire)
                    }
            }
            put("error", error)
        }.toString()
    }

    /**
     * NAP-COMMON replies: every one carries `ok`, and `error` (one of its codes) when it failed. An
     * action answers with the event it published (`eventId`, `event`).
     */
    private fun encodeCommonResponse(
        requestType: String,
        response: NappletResponse,
    ): String =
        buildJsonObject {
            put("type", "$requestType.result")
            when (response) {
                is NappletResponse.CommonEncoded -> {
                    put("ok", true)
                    put("value", response.value)
                    put("nip19Type", response.nip19Type)
                }
                is NappletResponse.CommonDecoded -> {
                    put("ok", true)
                    put("nip19Type", response.nip19Type)
                    response.text.forEach { (key, value) -> put(key, value) }
                    response.kind?.let { put("kind", it) }
                    if (response.relays.isNotEmpty()) putJsonArray("relays") { response.relays.forEach { add(it) } }
                }
                is NappletResponse.CommonProfile -> {
                    put("ok", true)
                    put("pubkey", response.pubkey)
                    val event = response.event
                    // The profile as the metadata JSON says it; content that isn't an object is no profile.
                    put("profile", event?.let { runCatching { json.parseToJsonElement(it.content) as? JsonObject }.getOrNull() } ?: JsonNull)
                    if (event != null) put("result", relayEventResult(OutboxEvent(event, emptyList())))
                }
                is NappletResponse.CommonFollowList -> {
                    put("ok", true)
                    putJsonArray("pubkeys") { response.pubkeys.forEach { add(it) } }
                }
                is NappletResponse.Published -> {
                    put("ok", true)
                    put("eventId", response.event.id)
                    put("event", json.parseToJsonElement(response.event.toJson()))
                }
                is NappletResponse.CommonUnchanged -> {
                    put("ok", true)
                    response.event?.let {
                        put("eventId", it.id)
                        put("event", json.parseToJsonElement(it.toJson()))
                    }
                }
                is NappletResponse.CommonFailure -> {
                    put("ok", false)
                    put("error", response.error)
                }
                is NappletResponse.Denied -> {
                    put("ok", false)
                    put("error", "user-denied")
                }
                is NappletResponse.Unsupported -> {
                    put("ok", false)
                    put("error", "unsupported")
                }
                else -> {
                    put("ok", false)
                    put("error", if (requestType in COMMON_ACTIONS) "publish-failed" else "unsupported")
                }
            }
        }.toString()

    /**
     * NAP-UPLOAD replies: `upload.upload.result` carries `result` (an `UploadResult`),
     * `upload.status.result` carries `status` (an `UploadStatus`), `upload.info.result` carries `info`.
     * A request that created no upload is answered with a top-level `error` only. Null for a response
     * this domain does not shape (the generic encoder handles it).
     */
    private fun encodeUploadResponse(
        requestType: String,
        response: NappletResponse,
    ): String? {
        val body: JsonObject =
            when (response) {
                is NappletResponse.Uploaded ->
                    buildJsonObject {
                        put(if (requestType == "upload.status") "status" else "result", uploadRecord(response, withUpdatedAt = requestType == "upload.status"))
                    }
                is NappletResponse.UploadInfo ->
                    buildJsonObject {
                        putJsonObject("info") {
                            putJsonArray("rails") {
                                addJsonObject {
                                    put("rail", UPLOAD_RAIL)
                                    put("enabled", response.enabled)
                                    putJsonArray("returns") { add("https") }
                                }
                            }
                            put("maxBytes", response.maxBytes)
                        }
                    }
                is NappletResponse.Denied -> buildJsonObject { put("error", "policy denied") }
                is NappletResponse.Unsupported -> buildJsonObject { put("error", "unsupported rail") }
                is NappletResponse.Failed -> buildJsonObject { put("error", response.reason) }
                else -> return null
            }
        return buildJsonObject {
            put("type", "$requestType.result")
            body.forEach { (key, value) -> put(key, value) }
        }.toString()
    }

    // NAP-UPLOAD's UploadResult / UploadStatus for a completed blossom upload, with its NIP-94 tags.
    private fun uploadRecord(
        upload: NappletResponse.Uploaded,
        withUpdatedAt: Boolean,
    ) = buildJsonObject {
        put("ok", true)
        put("uploadId", upload.uploadId)
        put("status", "complete")
        put("rail", UPLOAD_RAIL)
        put("url", upload.url)
        upload.sha256?.let { put("sha256", it) }
        upload.size?.let { put("size", it) }
        upload.mimeType?.let { put("mimeType", it) }
        putJsonArray("nip94") {
            add(
                buildJsonArray {
                    add("url")
                    add(upload.url)
                },
            )
            upload.mimeType?.let { mime ->
                add(
                    buildJsonArray {
                        add("m")
                        add(mime)
                    },
                )
            }
            upload.sha256?.let { hash ->
                add(
                    buildJsonArray {
                        add("x")
                        add(hash)
                    },
                )
            }
            upload.size?.let { size ->
                add(
                    buildJsonArray {
                        add("size")
                        add(size.toString())
                    },
                )
            }
        }
        if (withUpdatedAt) put("updatedAt", upload.updatedAt)
    }

    /**
     * A NAP-FS result: on success the operation's own field (`info`, `result`, `metadata`, …) and no
     * `error`; on failure only `error`, one of [NappletFsError]'s codes. Refusals and failures the
     * broker reports in its generic shapes are put in those terms.
     */
    private fun encodeFsResponse(
        requestType: String,
        response: NappletResponse,
    ): String =
        buildJsonObject {
            put("type", "$requestType.result")
            when (response) {
                is NappletResponse.FsInfoResult ->
                    putJsonObject("info") {
                        putJsonArray("roots") {
                            response.roots.forEach { root ->
                                addJsonObject {
                                    put("path", root.path)
                                    put("name", root.name)
                                    putJsonArray("permissions") { root.permissions.forEach { add(it) } }
                                }
                            }
                        }
                        putJsonObject("limits") {
                            put("maxReadBytes", response.maxReadBytes)
                            put("maxWriteBytes", response.maxWriteBytes)
                        }
                    }
                is NappletResponse.FsPicked ->
                    putJsonObject("result") {
                        putJsonArray("entries") { response.entries.forEach { add(fsEntry(it)) } }
                    }
                is NappletResponse.FsMetadata -> put("metadata", fsEntry(response.entry, withName = false))
                is NappletResponse.FsReadResult ->
                    putJsonObject("result") {
                        put("data", Base64.encode(response.bytes))
                        put("offset", response.offset)
                        put("bytesRead", response.bytes.size)
                        put("eof", response.eof)
                        response.size?.let { put("size", it) }
                    }
                is NappletResponse.FsWritten ->
                    putJsonObject("result") {
                        put("bytesWritten", response.bytesWritten)
                        response.size?.let { put("size", it) }
                    }
                is NappletResponse.Done -> {}
                is NappletResponse.FsFailure -> put("error", response.error)
                is NappletResponse.Denied -> put("error", NappletFsError.PERMISSION_DENIED)
                is NappletResponse.Unsupported -> put("error", NappletFsError.UNSUPPORTED)
                else -> put("error", NappletFsError.IO_ERROR)
            }
        }.toString()

    private fun fsEntry(
        entry: NappletResponse.FsEntry,
        withName: Boolean = true,
    ) = buildJsonObject {
        put("path", entry.path)
        put("kind", entry.kind)
        if (withName) put("name", entry.name)
        putJsonArray("permissions") { entry.permissions.forEach { add(it) } }
        entry.size?.let { put("size", it) }
    }

    /** The reply to an `fs.*` request too malformed to decode: NAP-FS's result shape, never a generic failure. */
    fun encodeFsMalformed(requestType: String): String = encodeFsResponse(requestType, NappletResponse.FsFailure(NappletFsError.UNSUPPORTED))

    /** Parses every Nostr filter from a `filters` array (each entry) or a single `filter` object. */
    fun decodeFilterList(envelopeJson: String): List<Filter> = decodeFilterList(json.parseToJsonElement(envelopeJson).jsonObject)

    private fun decodeFilterList(o: JsonObject): List<Filter> {
        when (val filters = o["filters"]) {
            is JsonArray -> return filters.map { decodeFilterObject(it.jsonObject) }
            // NAP-OUTBOX takes one filter or a list in the same field.
            is JsonObject -> return listOf(decodeFilterObject(filters))
            else -> {}
        }
        o["filter"]?.jsonObject?.let { return listOf(decodeFilterObject(it)) }
        return emptyList()
    }

    private fun decodeFilterObject(f: JsonObject): Filter {
        val tags = mutableMapOf<String, List<String>>()
        for ((key, value) in f) {
            if (key.startsWith("#") && key.length == 2) {
                tags[key.substring(1)] = value.jsonArray.map { it.jsonPrimitive.content }
            }
        }

        return Filter(
            ids = f.strList("ids"),
            authors = f.strList("authors"),
            kinds = f["kinds"]?.jsonArray?.map { it.jsonPrimitive.int },
            tags = tags.ifEmpty { null },
            since = f["since"]?.jsonPrimitive?.long,
            until = f["until"]?.jsonPrimitive?.long,
            limit = f["limit"]?.jsonPrimitive?.int,
            search = f.str("search"),
        )
    }

    private fun decodeTags(o: JsonObject): Array<Array<String>> {
        val tags = o["tags"]?.jsonArray ?: return emptyArray()
        return tags
            .map { inner -> inner.jsonArray.map { it.jsonPrimitive.content }.toTypedArray() }
            .toTypedArray()
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

    /** A required JSON string field; a number or object in its place is malformed, not coerced. */
    private fun JsonObject.text(key: String): String = optionalText(key) ?: throw IllegalArgumentException("$key is missing")

    private fun JsonObject.optionalText(key: String): String? {
        val value = this[key] ?: return null
        if (value is JsonNull) return null
        val primitive = value as? JsonPrimitive
        require(primitive != null && primitive.isString) { "$key is not a string" }
        return primitive.content
    }

    /** An optional JSON integer; a fraction, a string or anything else is malformed. */
    private fun JsonObject.integer(key: String): Long? {
        val value = this[key] ?: return null
        if (value is JsonNull) return null
        val primitive = value as? JsonPrimitive
        require(primitive != null && !primitive.isString) { "$key is not a number" }
        return primitive.longOrNull ?: throw IllegalArgumentException("$key is not an integer")
    }

    private fun JsonObject.fsPickOptions(): NappletFsPickOptions {
        val options = options() ?: return NappletFsPickOptions()
        return NappletFsPickOptions(
            permissions = options["permissions"]?.takeIf { it !is JsonNull }?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content },
            accept =
                options["accept"]
                    ?.takeIf { it !is JsonNull }
                    ?.jsonArray
                    ?.mapNotNull { rule ->
                        when (rule) {
                            // A bare string is accepted as an extension (".png") or a MIME type ("image/png").
                            is JsonPrimitive -> rule.takeIf { it.isString }?.content?.let { if (it.contains('/')) NappletFsAccept(mime = it) else NappletFsAccept(extension = it) }
                            is JsonObject -> NappletFsAccept(rule.optionalText("mime"), rule.optionalText("extension")).takeIf { it.mime != null || it.extension != null }
                            else -> null
                        }
                    }.orEmpty(),
            suggestedName = options.optionalText("suggestedName"),
            description = options.optionalText("description"),
        )
    }

    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

    private fun JsonObject.options(): JsonObject? = this["options"] as? JsonObject

    private fun JsonObject.req(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.strList(key: String): List<String>? = this[key]?.jsonArray?.map { it.jsonPrimitive.content }

    /** The unsigned event template, carried in the `event` field (per `@napplet/shim`), else the envelope itself. */
    private fun JsonObject.eventTemplate(): JsonObject = this["event"]?.jsonObject ?: this

    private fun JsonObject.kindOf(): Int = getValue("kind").jsonPrimitive.int

    private fun JsonObject.storageScope(): NappletStorageScope = if (str("scope") == "instance") NappletStorageScope.INSTANCE else NappletStorageScope.SHARED

    /** The result field a given identity read returns, matching `@napplet/nap` identity message types. */
    private fun identityResultField(requestType: String): String =
        when (requestType) {
            "identity.getRelays" -> "relays"
            "identity.getFollows", "identity.getMutes", "identity.getBlocked" -> "pubkeys"
            "identity.getProfile" -> "profile"
            "identity.getList" -> "entries"
            "identity.getZaps" -> "zaps"
            "identity.getBadges" -> "badges"
            else -> "result"
        }

    private const val FS_PREFIX = "fs."
    private const val UPLOAD_PREFIX = "upload."
    private const val COMMON_PREFIX = "common."

    // The NAP-COMMON operations that publish an event.
    private val COMMON_ACTIONS = setOf("common.follow", "common.unfollow", "common.react", "common.report")

    // The one NAP-UPLOAD rail the shell offers.
    private const val UPLOAD_RAIL = "blossom"

    private val IDENTITY_READ_TYPES =
        setOf(
            "identity.getRelays",
            "identity.getProfile",
            "identity.getFollows",
            "identity.getList",
            "identity.getZaps",
            "identity.getMutes",
            "identity.getBlocked",
            "identity.getBadges",
        )
}
