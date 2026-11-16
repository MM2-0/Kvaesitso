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
package com.vitorpamplona.amethyst.commons.napplet.gateways

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.utils.BigDecimal
import com.vitorpamplona.quartz.utils.toDoubleValue
import com.vitorpamplona.quartz.utils.toLongValue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Reads non-key identity data from an account as JSON value strings, for the napplet `identity.*`
 * domain. Everything here is **public** profile/list data — never key material. Returns the literal
 * `"null"` for an absent value, or `null` for a method this shell does not implement yet (the broker
 * then answers `Unsupported`). Shapes match `@napplet/nap` (e.g. `displayName`, not `display_name`).
 *
 * List/zap/badge reads draw from what Amethyst already has locally ([Account.cache]) — the same
 * "what we know now" semantics as `getFollows`/`getMutes` — so they never block on a relay round
 * trip. Private (encrypted) list entries are not included; only the public tag values.
 */
class AccountIdentityReader(
    private val account: Account,
) {
    private val cache get() = account.cache
    private val pubkey get() = account.signer.pubKey

    fun read(
        method: String,
        argument: String?,
    ): String? =
        when (method) {
            "getProfile" -> profileJson()
            "getFollows" -> jsonStringArray(account.kind3FollowList.flow.value.authors)
            "getMutes" ->
                jsonStringArray(
                    // PUBLIC mutes only — the decrypted flow (muteList.flow) also holds the user's
                    // private mutes, which a napplet must never see. Read the event's plaintext tags.
                    account.muteList
                        .getMuteList()
                        ?.publicMutes()
                        ?.filterIsInstance<UserTag>()
                        ?.map { it.pubKey }
                        .orEmpty(),
                )
            "getBlocked" ->
                jsonStringArray(
                    // PUBLIC blocks only, for the same reason as getMutes.
                    account.blockPeopleList
                        .getBlockList()
                        ?.publicUsersIdSet()
                        .orEmpty(),
                )
            "getRelays" -> relaysJson()
            "getList" -> listJson(argument)
            "getZaps" -> zapsJson()
            "getBadges" -> badgesJson()
            else -> null
        }

    private fun jsonStringArray(items: Iterable<String>): String = buildJsonArray { items.forEach { add(it) } }.toString()

    /** Builds a `@napplet/nap` `ProfileData` object (note `displayName`, not `display_name`) from kind-0. */
    private fun profileJson(): String {
        val md = account.userMetadata.getUserMetadataEvent()?.contactMetaData() ?: return "null"
        return buildJsonObject {
            md.name?.let { put("name", it) }
            md.displayName?.let { put("displayName", it) }
            md.about?.let { put("about", it) }
            md.picture?.let { put("picture", it) }
            md.banner?.let { put("banner", it) }
            md.nip05?.let { put("nip05", it) }
            md.lud16?.let { put("lud16", it) }
            md.website?.let { put("website", it) }
        }.toString()
    }

    /** Builds `{ "<relay url>": { "read": bool, "write": bool }, ... }` from the user's NIP-65 list. */
    private fun relaysJson(): String {
        val relays = account.nip65RelayList.getNIP65RelayList()?.relays() ?: return "null"
        return buildJsonObject {
            relays.forEach { info ->
                putJsonObject(info.relayUrl.url) {
                    put("read", info.type.isRead())
                    put("write", info.type.isWrite())
                }
            }
        }.toString()
    }

    /**
     * `identity.getList(listType)` → the public tag values of the user's NIP-51 list of that type
     * (e.g. the e/a entries of "bookmarks", the t hashtags of "interests"). Unknown list types → null.
     */
    private fun listJson(listType: String?): String? {
        val kind = listKinds[listType?.trim()?.lowercase()] ?: return null
        val event =
            cache
                .filter(Filter(kinds = listOf(kind), authors = listOf(pubkey)))
                .mapNotNull { it.event }
                .maxByOrNull { it.createdAt }
        val entries =
            event
                ?.tags
                ?.filter { it.size >= 2 && it[0] in ENTRY_TAGS }
                ?.map { it[1] }
                .orEmpty()
        return buildJsonArray { entries.forEach { add(it) } }.toString()
    }

    /** `identity.getZaps` → `ZapReceipt[]` ({eventId, sender, amount, content?}) the user received, from cache. */
    private fun zapsJson(): String {
        val receipts =
            cache
                .filter(Filter(kinds = listOf(9735), tags = mapOf("p" to listOf(pubkey))))
                .mapNotNull { it.event as? ZapReceiptEvent }
        return buildJsonArray {
            receipts.forEach { zap ->
                addJsonObject {
                    put("eventId", zap.zappedPost().firstOrNull() ?: "")
                    put("sender", zap.zappedRequestAuthor() ?: "")
                    put("amount", zap.amount()?.let(::amountJson) ?: JsonPrimitive(0L))
                    zap.zapRequest
                        ?.content
                        ?.takeIf { it.isNotBlank() }
                        ?.let { put("content", it) }
                }
            }
        }.toString()
    }

    /** `identity.getBadges` → `Badge[]` ({id, name?, description?, image?, thumbs?, awardedBy}) awarded to the user, from cache. */
    private fun badgesJson(): String {
        val awards =
            cache
                .filter(Filter(kinds = listOf(8), tags = mapOf("p" to listOf(pubkey))))
                .mapNotNull { it.event as? BadgeAwardEvent }
        return buildJsonArray {
            awards.forEach { award ->
                val address = award.awardDefinition().firstOrNull() ?: return@forEach
                val definition = cache.getAddressableNoteIfExists(address)?.event as? BadgeDefinitionEvent
                addJsonObject {
                    put("id", definition?.dTag() ?: address.dTag)
                    definition?.name()?.let { put("name", it) }
                    definition?.description()?.let { put("description", it) }
                    definition?.image()?.let { put("image", it) }
                    definition?.thumb()?.let { thumb -> put("thumbs", buildJsonArray { add(thumb) }) }
                    put("awardedBy", award.pubKey)
                }
            }
        }.toString()
    }

    companion object {
        /** NIP-51 replaceable list kinds keyed by the napplet list-type string. */
        private val listKinds =
            mapOf(
                "mute" to 10000,
                "mutes" to 10000,
                "pins" to 10001,
                "pin" to 10001,
                "bookmarks" to 10003,
                "communities" to 10004,
                "channels" to 10005,
                "interests" to 10015,
                "emojis" to 10030,
            )

        /** Tag names whose value is a list entry (vs metadata like d/title/description). */
        private val ENTRY_TAGS = setOf("e", "a", "p", "t", "word", "r", "emoji")
    }
}

/** A zap amount as a JSON number: whole sats as an integer, a millisat fraction as a decimal. */
private fun amountJson(sats: BigDecimal): JsonPrimitive {
    val whole = sats.toLongValue()
    val exact = sats.toDoubleValue()
    return if (exact == whole.toDouble()) JsonPrimitive(whole) else JsonPrimitive(exact)
}
