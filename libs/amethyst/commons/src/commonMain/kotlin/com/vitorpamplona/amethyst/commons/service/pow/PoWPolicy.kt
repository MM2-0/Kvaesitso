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
package com.vitorpamplona.amethyst.commons.service.pow

import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.isEphemeral
import com.vitorpamplona.quartz.nip01Core.core.isReplaceable
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent

/**
 * User-facing kind groups for the NIP-13 proof-of-work settings checklist.
 * Users toggle categories, never raw kind numbers.
 */
enum class PoWCategory(
    val id: String,
    val defaultEnabled: Boolean,
) {
    SHORT_NOTES("short_notes", true),
    COMMENTS("comments", true),
    REPORTS("reports", true),
    LONG_FORM("long_form", true),
    VOICE("voice", true),
    REPOSTS("reposts", false),
    REACTIONS("reactions", false),
    PUBLIC_CHAT("public_chat", false),
    GIFT_WRAPS("gift_wraps", false),
    OTHER_PUBLIC("other_public", false),
    ;

    companion object {
        val DEFAULT_ENABLED = entries.filter { it.defaultEnabled }.toSet()

        fun fromIds(ids: Collection<String>): Set<PoWCategory> = entries.filter { it.id in ids }.toSet()
    }
}

/**
 * Decides which events get a NIP-13 proof of work mined into them before signing.
 *
 * The NEVER rules are hardcoded on purpose (not user preferences) so no caller
 * can accidentally mine a relay AUTH challenge, a zap request that blocks an
 * invoice fetch, an NWC/bunker RPC, or the drafts that are re-signed on every
 * keystroke debounce.
 */
object PoWPolicy {
    /**
     * Practical UI ceiling for the difficulty setting: above ~40 bits a phone
     * would mine for days; treat anything larger (including values arriving
     * from a synced NIP-78 settings event) as a config error and clamp.
     */
    const val MAX_DIFFICULTY = 40

    /**
     * How many parallel miner workers one mining job should use on a device
     * with [availableProcessors] cores: half of them, so a running miner never
     * starves the UI/render threads or the relay client. At least 1.
     */
    fun minerWorkers(availableProcessors: Int): Int = (availableProcessors / 2).coerceAtLeast(1)

    /**
     * The desktop's worker count: every core but two. A desktop has the cores to spare, and the two
     * left free keep the window and the relay client responsive while a post mines. At least 1.
     */
    fun desktopMinerWorkers(availableProcessors: Int): Int = (availableProcessors - 2).coerceAtLeast(1)

    private const val LONG_FORM_DRAFT_KIND = 30024

    /** NIP-51 sets and other settings-like addressable kinds. */
    private val NEVER_ADDRESSABLE =
        setOf(
            30000, // follow sets
            30001, // deprecated generic lists
            30002, // relay sets
            30003, // bookmark sets
            30004, // curation sets (articles)
            30005, // curation sets (videos)
            30007, // kind mute sets
            30015, // interest sets
            30030, // emoji sets
            30063, // release artifact sets
            LONG_FORM_DRAFT_KIND,
            AppSpecificDataEvent.KIND,
        )

    private val NEVER_EXPLICIT =
        setOf(
            MetadataEvent.KIND,
            ContactListEvent.KIND,
            ZapRequestEvent.KIND, // blocks the invoice fetch
            OtsEvent.KIND, // machine-generated companion events
            DraftWrapEvent.KIND, // re-signed on a 1s debounce while typing
        )

    /**
     * Kinds that must never be mined regardless of user settings.
     *
     * The replaceable range covers relay lists, NIP-51 standard lists, NWC
     * info and other settings sync; the ephemeral range covers relay AUTH
     * (22242), NWC RPC (23194..23196), NIP-46 bunker messages (24133),
     * Blossom auth (24242) and HTTP auth (27235) — all time-critical
     * request/response events where mining only adds latency.
     */
    fun neverMine(kind: Kind): Boolean =
        kind in NEVER_EXPLICIT ||
            kind.isReplaceable() ||
            kind.isEphemeral() ||
            kind in NEVER_ADDRESSABLE

    fun categoryOf(kind: Int): PoWCategory =
        when (kind) {
            TextNoteEvent.KIND -> PoWCategory.SHORT_NOTES
            CommentEvent.KIND -> PoWCategory.COMMENTS
            ReportEvent.KIND -> PoWCategory.REPORTS
            LongFormContentEvent.KIND, HighlightEvent.KIND -> PoWCategory.LONG_FORM
            VoiceEvent.KIND, VoiceReplyEvent.KIND -> PoWCategory.VOICE
            RepostEvent.KIND, GenericRepostEvent.KIND -> PoWCategory.REPOSTS
            ReactionEvent.KIND -> PoWCategory.REACTIONS
            ChannelMessageEvent.KIND, LiveActivitiesChatMessageEvent.KIND -> PoWCategory.PUBLIC_CHAT
            GiftWrapEvent.KIND -> PoWCategory.GIFT_WRAPS
            else -> PoWCategory.OTHER_PUBLIC
        }

    /**
     * Returns the difficulty to mine [kind] at, or null when the event should
     * be published without proof of work. The difficulty is clamped to
     * [MAX_DIFFICULTY] as defense in depth against out-of-range values synced
     * from other clients — an unclamped 300 would crash the miner and 41+
     * would mine effectively forever.
     */
    fun shouldMine(
        kind: Int,
        difficulty: Int,
        enabledCategories: Set<PoWCategory>,
    ): Int? {
        if (difficulty <= 0) return null
        if (neverMine(kind)) return null
        if (categoryOf(kind) !in enabledCategories) return null
        return difficulty.coerceAtMost(MAX_DIFFICULTY)
    }
}
