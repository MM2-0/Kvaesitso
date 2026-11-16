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
package com.vitorpamplona.amethyst.commons.relayClient.auth

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurpose
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.commons.relayauth.toAuthPurposeKind
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent

/** Addressable venue kinds whose home relay may require auth: NIP-72 communities and NIP-53 live
 *  activities. Their `a` addresses are `kind:ownerPubkey:dTag`. */
private val VENUE_KINDS = setOf(CommunityDefinitionEvent.KIND, LiveActivitiesEvent.KIND)

/** The kinds a Concord plane wrap can be — the only ones worth asking `venueForPlaneAuthor` about. */
private val STREAM_WRAP_KINDS = setOf(ConcordStreamEnvelope.KIND_WRAP, ConcordStreamEnvelope.KIND_WRAP_EPHEMERAL)

/**
 * Marmot (MLS) carries the group id in an `h` tag exactly like NIP-29 does, but an MLS group is not a
 * room we can name: the id is an opaque MLS value with no metadata event and no channel object behind
 * it. Reading it as a venue would put that id in front of the user as a room name — and, on a
 * `POST_VENUE`, get-or-create a phantom public chat for it (the id is 64-hex). These keep their prior
 * reading: no `p` tags, so they land on the unattributed safety net.
 */
private val MLS_GROUP_KINDS = setOf(GroupEvent.KIND, WelcomeEvent.KIND)

/**
 * Infers *why* a relay wants NIP-42 auth from what Amethyst is currently doing with it — the
 * events pending delivery and the active subscription filters (both from the [INostrClient]).
 * Pure so it can be unit-tested without the relay client.
 *
 * Sends (from the outbox) are read from the events themselves:
 *
 * - a pending Concord plane wrap (recognized by [venueForPlaneAuthor], since the wrap is *signed* by
 *   the plane's stream key) => [AuthPurposeKind.POST_VENUE] for that community;
 * - a pending `h`-tagged event => [AuthPurposeKind.POST_VENUE] for that NIP-29 relay group (except
 *   the [MLS_GROUP_KINDS], whose `h` names something unnameable);
 * - a pending gift wrap (kind 1059) => sending a DM to its `p` recipient ([AuthPurposeKind.SEND_DM]);
 * - a pending channel/community/live post => [AuthPurposeKind.POST_VENUE] for that venue;
 * - any other pending event with `p` tags => delivering it to those users' inboxes ([AuthPurposeKind.NOTIFY_INBOX]).
 *
 * The two room rules come first because both would otherwise be read as something else entirely: a
 * Concord wrap is kind 1059 `p`-tagged to a *throwaway* pubkey, so the gift-wrap rule would explain a
 * community post as a DM to a stranger nobody can name, and a NIP-29 chat message mentioning someone
 * would be explained as a notification rather than as a message into the group.
 *
 * Reads prefer the purpose the subscription **declared**. Assemblers build every filter as an
 * [ExplainedFilter] carrying a [SubPurpose][com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose]
 * and the entity ids it serves, so we simply read it (see `toAuthPurposeKind`). That is the only way
 * to recognize the two states tag shape cannot express — reading your own inbox (`#p` = me, no
 * `authors`) and reading a thread's engagement (`#e` against note ids, shape-identical to a NIP-28
 * channel read). A filter that isn't an [ExplainedFilter], or whose purpose says nothing about
 * identity, falls back to the tag-shape rules:
 *
 * - a filter with `authors` => reading those authors' posts ([AuthPurposeKind.READ_OUTBOX]);
 * - a filter with `#e`/`#a` venue tags => reading a venue ([AuthPurposeKind.READ_VENUE]);
 * - anything else we're actively doing but can't attribute => [AuthPurposeKind.OTHER], so the relay
 *   is prompted about instead of silently failing.
 */
object RelayAuthPurposeDeriver {
    /**
     * @param venueForPlaneAuthor maps the pubkey that *signed* a pending event to the room it streams
     *   for — today, a Concord plane address to its community id. Returns null for anything else.
     */
    fun derive(
        pendingEvents: List<Event>,
        activeFilters: Map<String, List<Filter>>,
        venueForPlaneAuthor: (HexKey) -> String? = { null },
    ): List<AuthPurpose> {
        val dmRecipients = mutableSetOf<HexKey>()
        val notifyRecipients = mutableSetOf<HexKey>()
        val postVenues = mutableSetOf<String>()
        var unattributedWrite = false

        pendingEvents.forEach { event ->
            val pubkeys = event.tags.mapNotNull(PTag::parseKey)
            val venues = event.tags.mapNotNull(ATag::parseAddress).filter { it.kind in VENUE_KINDS }
            // Only a stream wrap can belong to a plane, so only a wrap pays for the lookup.
            val planeVenue = if (event.kind in STREAM_WRAP_KINDS) venueForPlaneAuthor(event.pubKey) else null
            val groupId = if (event.kind in MLS_GROUP_KINDS) null else event.tags.firstNotNullOfOrNull(GroupIdTag::parse)
            when {
                planeVenue != null -> postVenues.add(planeVenue)
                groupId != null -> postVenues.add(groupId)
                event.kind == GiftWrapEvent.KIND -> dmRecipients.addAll(pubkeys)
                event.kind == ChannelMessageEvent.KIND -> event.tags.channelRootId()?.let(postVenues::add)
                venues.isNotEmpty() -> venues.forEach { postVenues.add(it.toValue()) }
                pubkeys.isNotEmpty() -> notifyRecipients.addAll(pubkeys - event.pubKey)
                else -> unattributedWrite = true
            }
        }

        val readAuthors = mutableSetOf<HexKey>()
        val readVenues = mutableSetOf<String>()
        val readThreadNotes = mutableSetOf<String>()
        var readsMyInbox = false
        var readsThread = false
        var unattributedRead = false

        // Tag shape: who or what the filter names, for a filter that declares nothing more useful.
        fun inferFromShape(filter: Filter) {
            var matched = false
            filter.authors?.let {
                readAuthors.addAll(it)
                matched = true
            }
            filter.venueTags().takeIf { it.isNotEmpty() }?.let {
                readVenues.addAll(it)
                matched = true
            }
            if (!matched) unattributedRead = true
        }

        activeFilters.values.forEach { filters ->
            filters.forEach { filter ->
                val explained = filter as? ExplainedFilter
                when (explained?.purpose?.toAuthPurposeKind()) {
                    // Declared and self-contained: nobody else's identity is involved, so there is
                    // nothing to collect — the flag alone drives the wording.
                    AuthPurposeKind.MY_INBOX -> readsMyInbox = true
                    // The notes are what makes the sentence nameable. The assembler either declares
                    // them (entityIds) or, as ReactionsFilterAssembler does, only puts them in the
                    // `e` tags — take whichever we get so the prompt can say whose conversation.
                    AuthPurposeKind.THREAD -> {
                        readsThread = true
                        explained.entityIds?.let(readThreadNotes::addAll)
                        filter.tags?.get("e")?.let(readThreadNotes::addAll)
                    }
                    // Declared, but the *who*/*what* still comes from the filter. Prefer the entity
                    // ids the assembler named over sniffing tags, and fall back when it named none.
                    //
                    // A venue job can also read something that is not a venue — a Concord invite
                    // bundle by its link signer, a Buzz relay's `#p` = me warm-up. Naming nothing
                    // must not make it vanish from the prompt: that turns ASK into a silent DENY.
                    // It falls back to the tag shape, exactly as an untagged filter would.
                    AuthPurposeKind.READ_VENUE -> {
                        val declared = explained.entityIds.orEmpty().ifEmpty { filter.venueTags() }
                        if (declared.isNotEmpty()) readVenues.addAll(declared) else inferFromShape(filter)
                    }
                    AuthPurposeKind.READ_OUTBOX -> {
                        val declared = filter.authors.orEmpty()
                        if (declared.isNotEmpty()) readAuthors.addAll(declared) else unattributedRead = true
                    }
                    // No declared purpose (a plain Filter, or one whose purpose says nothing about
                    // identity): infer from tag shape exactly as before.
                    else -> inferFromShape(filter)
                }
            }
        }

        return buildList {
            if (dmRecipients.isNotEmpty()) add(AuthPurpose(AuthPurposeKind.SEND_DM, dmRecipients))
            if (notifyRecipients.isNotEmpty()) add(AuthPurpose(AuthPurposeKind.NOTIFY_INBOX, notifyRecipients))
            if (postVenues.isNotEmpty()) add(AuthPurpose(AuthPurposeKind.POST_VENUE, venues = postVenues))
            if (readAuthors.isNotEmpty()) add(AuthPurpose(AuthPurposeKind.READ_OUTBOX, readAuthors))
            if (readVenues.isNotEmpty()) add(AuthPurpose(AuthPurposeKind.READ_VENUE, venues = readVenues))
            if (readsMyInbox) add(AuthPurpose(AuthPurposeKind.MY_INBOX))
            if (readsThread) add(AuthPurpose(AuthPurposeKind.THREAD, notes = readThreadNotes))
            // Safety net: we're using this relay but couldn't say how — prompt rather than fail silently.
            if (isEmpty() && (unattributedWrite || unattributedRead)) add(AuthPurpose(AuthPurposeKind.OTHER))
        }
    }

    /**
     * Venue ids named by a filter's tags: every `#e` value, plus the `#a` addresses whose kind is a
     * venue. Only meaningful for a filter we have *no* declared purpose for — an `#e` list is just as
     * likely to be note ids on a thread as it is to be channel roots.
     */
    private fun Filter.venueTags(): Set<String> =
        buildSet {
            tags?.get("e")?.let(::addAll)
            tags
                ?.get("a")
                ?.mapNotNull { Address.parse(it) }
                ?.filter { it.kind in VENUE_KINDS }
                ?.forEach { add(it.toValue()) }
        }

    /** The venue a channel message posts into: the `e` tag marked "root", else the first `e` tag. */
    private fun Array<Array<String>>.channelRootId(): HexKey? = firstNotNullOfOrNull(MarkedETag::parseRoot)?.eventId ?: firstNotNullOfOrNull(ETag::parseId)
}
