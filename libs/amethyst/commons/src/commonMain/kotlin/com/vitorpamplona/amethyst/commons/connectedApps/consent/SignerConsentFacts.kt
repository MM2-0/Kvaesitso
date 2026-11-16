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
package com.vitorpamplona.amethyst.commons.connectedApps.consent

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.napplet.NappletRecentEncryptions
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.kite.KitePretty
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// What a signing request does, read off the request before any wording: the consent prompts phrase
// these facts in the platform's language (see the commonsUI consent builders).

/**
 * A kind-22242 that names no relay is a web app proving to its own server who you are (Brainstorm
 * signs `t=brainstorm_login`), not a NIP-42 relay login, and the prompt should say so.
 */
fun isAppLogin(
    kind: Int,
    tags: Array<Array<String>>,
): Boolean = kind == RelayAuthEvent.KIND && tags.none { it.size > 1 && it[0] == "relay" && it[1].isNotBlank() }

/** What a kind-13 seal carries, recovered from the encryption that produced its content. */
class SealContents(
    val recipient: HexKey,
    /** The rumor as an unsigned template, so the prompt can render the message itself. */
    val rumor: EventTemplate<Event>?,
    val rumorJson: String,
)

/**
 * The message inside a seal, when this broker encrypted it moments ago (see
 * [NappletRecentEncryptions]); null when it can't know, in which case the prompt says so instead of
 * showing ciphertext.
 */
fun sealContents(
    kind: Int,
    content: String,
    recent: NappletRecentEncryptions?,
): SealContents? {
    if (kind != SealEvent.KIND) return null
    val entry = recent?.lookup(content) ?: return null
    val rumor =
        runCatching {
            val node = Json.parseToJsonElement(entry.plaintext).jsonObject
            val tags =
                node["tags"]
                    ?.jsonArray
                    ?.map { tag -> tag.jsonArray.map { it.jsonPrimitive.content }.toTypedArray() }
                    ?.toTypedArray() ?: emptyArray()
            EventTemplate<Event>(
                createdAt = node["created_at"]?.jsonPrimitive?.longOrNull ?: 0L,
                kind = node["kind"]?.jsonPrimitive?.intOrNull ?: return@runCatching null,
                tags = tags,
                content = node["content"]?.jsonPrimitive?.content ?: "",
            )
        }.getOrNull()
    val pretty = runCatching { KitePretty.format(entry.plaintext) }.getOrDefault(entry.plaintext)
    return SealContents(entry.recipient, rumor, pretty)
}

/** A profile field the consent prompt can name. */
enum class ProfileField { NAME, DISPLAY_NAME, PICTURE, BANNER, ABOUT, WEBSITE, NIP05, LIGHTNING }

/** Which named fields a profile update changes or removes, and whether it drops any data at all. */
class ProfileFieldChanges(
    val changed: List<ProfileField>,
    val removed: List<ProfileField>,
    val removesData: Boolean,
)

/**
 * Compares a proposed kind 0 ([tags] + [content], unsigned) with [current]. Reads the content JSON,
 * as every Amethyst screen does, so the line matches what the profile will show once published.
 */
fun profileFieldChanges(
    current: MetadataEvent,
    tags: Array<Array<String>>,
    content: String,
): ProfileFieldChanges? {
    val proposed = MetadataEvent("0".repeat(64), current.pubKey, current.createdAt + 1, tags, content, "0".repeat(128))
    val diff = proposed.diffFrom(current) ?: return null
    val fields =
        listOfNotNull(
            diff.name?.let { ProfileField.NAME to it.isRemoval() },
            diff.displayName?.let { ProfileField.DISPLAY_NAME to it.isRemoval() },
            diff.picture?.let { ProfileField.PICTURE to it.isRemoval() },
            diff.banner?.let { ProfileField.BANNER to it.isRemoval() },
            diff.about?.let { ProfileField.ABOUT to it.isRemoval() },
            diff.website?.let { ProfileField.WEBSITE to it.isRemoval() },
            diff.nip05?.let { ProfileField.NIP05 to it.isRemoval() },
            diff.lud16?.let { ProfileField.LIGHTNING to it.isRemoval() },
            diff.lud06?.let { ProfileField.LIGHTNING to it.isRemoval() },
        ).distinctBy { it.first }
    return ProfileFieldChanges(
        changed = fields.filterNot { it.second }.map { it.first },
        removed = fields.filter { it.second }.map { it.first },
        removesData = diff.removesData(),
    )
}

/**
 * A person's display name for a consent prompt: their profile name when we have it cached, otherwise
 * a shortened npub. Never empty — "read your private messages with <nothing>" would be worse than the
 * broad wording it replaces.
 */
fun counterpartyLabel(pubKeyHex: HexKey): String {
    LocalCache
        .getUserIfExists(pubKeyHex)
        ?.toBestDisplayName()
        ?.ifBlank { null }
        ?.let { return it }
    val npub = runCatching { NPub.create(pubKeyHex) }.getOrNull()
    return if (npub != null) npub.take(12) + "…" else pubKeyHex.take(12) + "…"
}
