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
package com.vitorpamplona.amethyst.commons.model.nip28PublicChats

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHintOptional
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.ChannelDataNorm

@Stable
class PublicChatChannel(
    val idHex: String,
) : Channel() {
    var creator: User? = null
    var event: ChannelCreateEvent? = null

    // Important to keep this long-term reference because LocalCache uses WeakReferences.
    var creationEventNote: Note? = null
    var updateEventNote: Note? = null

    var info = ChannelDataNorm(null, null, null, null)
    var infoTags = EmptyTagList
    var updatedMetadataAt: Long = 0

    // An empty declared-relay list must behave like "no declared relays" and fall
    // back to the relays the channel was actually observed on. Without ifEmpty,
    // `emptyList()?.toSet()` short-circuits the elvis to an empty set, so the
    // channel reports zero relays and messages get published to nowhere.
    override fun relays() = info.relays?.ifEmpty { null }?.toSet() ?: super.relays()

    fun relayHintUrls() = relays().take(3)

    fun relayHintUrl() = relays().firstOrNull()

    fun toNEvent() = NEvent.create(idHex, event?.pubKey, ChannelCreateEvent.KIND, relayHintUrls())

    fun toNostrUri() = NEvent.createNostrUri(idHex, event?.pubKey, ChannelCreateEvent.KIND, relayHintUrls())

    fun toEventHint() = event?.let { EventHintBundle(it, relayHintUrl(), null) }

    fun toEventId() = EventIdHintOptional(idHex, relayHintUrl())

    fun updateChannelInfo(
        creator: User,
        event: ChannelCreateEvent,
        eventNote: Note? = null,
    ) {
        this.creator = creator
        this.event = event
        this.info = event.channelInfo()

        this.infoTags = event.tags.toImmutableListOfLists()
        this.updatedMetadataAt = event.createdAt
        this.creationEventNote = eventNote

        // Kind 41s that landed before this kind 40 waited in [notes] until the creator was known: drop
        // everyone else's, and apply the creator's newest.
        notes
            .filter { _, note -> (note.event as? ChannelMetadataEvent)?.let { it.pubKey != creator.pubkeyHex } == true }
            .forEach { removeNote(it) }
        val latestUpdate = latestMetadataUpdateBy(creator)
        if (latestUpdate != null && latestUpdate.second.createdAt > updatedMetadataAt) {
            updateChannelInfo(latestUpdate.second, latestUpdate.first)
        } else {
            updateChannelInfo()
        }
    }

    /**
     * Applies a kind 41. NIP-28 honors only the kind-40 author's, so callers pass one from [creator];
     * it never changes who the creator is.
     */
    fun updateChannelInfo(
        event: ChannelMetadataEvent,
        eventNote: Note? = null,
    ) {
        this.info = event.channelInfo()

        this.infoTags = event.tags.toImmutableListOfLists()
        this.updatedMetadataAt = event.createdAt
        this.updateEventNote = eventNote

        super.updateChannelInfo()
    }

    private fun latestMetadataUpdateBy(author: User): Pair<Note, ChannelMetadataEvent>? =
        notes
            .mapNotNull { _, note ->
                (note.event as? ChannelMetadataEvent)?.takeIf { it.pubKey == author.pubkeyHex }?.let { note to it }
            }.maxByOrNull { it.second.createdAt }

    override fun toBestDisplayName(): String = info.name ?: toNEvent().toShortDisplay()

    fun summary(): String? = info.about

    fun profilePicture(): String? {
        if (info.picture.isNullOrBlank()) {
            return creator
                ?.metadataOrNull()
                ?.flow
                ?.value
                ?.info
                ?.banner
        }
        return info.picture ?: creator
            ?.metadataOrNull()
            ?.flow
            ?.value
            ?.info
            ?.banner
    }

    fun anyNameStartsWith(prefix: String): Boolean =
        idHex.startsWith(prefix) ||
            info.name?.contains(prefix, true) == true ||
            info.about?.contains(prefix, true) == true
}
