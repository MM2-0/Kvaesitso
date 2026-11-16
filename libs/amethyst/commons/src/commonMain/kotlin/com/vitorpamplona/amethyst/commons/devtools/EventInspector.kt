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
package com.vitorpamplona.amethyst.commons.devtools

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.kinds.KindNames
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.isAddressable
import com.vitorpamplona.quartz.nip01Core.core.isReplaceable
import com.vitorpamplona.quartz.nip01Core.crypto.generateId
import com.vitorpamplona.quartz.nip01Core.crypto.verifySignature
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Where a pasted event came from, so the reader knows what was unwrapped for them. */
enum class EventSource {
    JSON,
    RELAY_MESSAGE,
    CLIENT_COMMAND,
    NEMBED,
}

/** Something wrong, or worth a second look, in an otherwise parseable event. */
enum class EventIssue {
    ID_MISMATCH,
    BAD_SIGNATURE,
    CREATED_IN_FUTURE,
    UNKNOWN_KIND,
}

@Immutable
data class EventInspection(
    val event: Event,
    val source: EventSource,
    val idValid: Boolean,
    val computedId: String,
    val signatureValid: Boolean,
    val kindName: String?,
    val kindNip: String?,
    val issues: ImmutableList<EventIssue>,
    val codes: EventCodes,
) {
    val isValid get() = idValid && signatureValid
}

/** The NIP-19 handles a developer copies out of an event: its nevent, its naddr when it has one, and its author's npub. */
@Immutable
data class EventCodes(
    val nevent: String,
    /** Null for a kind that is neither replaceable nor addressable, or a d-tag too long for an naddr. */
    val naddr: String?,
    val npub: String,
) {
    companion object {
        fun of(event: Event) =
            EventCodes(
                nevent = NEvent.create(event.id, event.pubKey, event.kind, emptyList()),
                naddr =
                    when {
                        event.kind.isAddressable() -> NAddress.createOrNull(event.kind, event.pubKey, event.tags.dTag(), emptyList())
                        event.kind.isReplaceable() -> NAddress.createOrNull(event.kind, event.pubKey, "", emptyList())
                        else -> null
                    },
                npub = NPub.create(event.pubKey),
            )
    }
}

@Immutable
sealed interface EventInspectResult {
    data object Empty : EventInspectResult

    @Immutable
    data class Unparseable(
        val message: String,
    ) : EventInspectResult

    @Immutable
    data class Parsed(
        val inspection: EventInspection,
    ) : EventInspectResult
}

/**
 * The event verifier behind the inspector: takes whatever a developer has on hand — a bare
 * event, a relay frame (`["EVENT", sub, {...}]`), a client command (`["EVENT", {...}]`) or an
 * `nembed` — and reports whether the stated id matches the one recomputed from the content
 * (NIP-01), whether the Schnorr signature checks out, and what the kind is.
 */
object EventInspector {
    /** Seconds of clock skew tolerated before a `created_at` reads as "in the future". */
    private const val FUTURE_SKEW_SECS = 15 * 60

    fun inspect(input: String): EventInspectResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return EventInspectResult.Empty

        val (event, source) =
            try {
                unwrap(trimmed)
            } catch (e: Exception) {
                return EventInspectResult.Unparseable(e.message ?: e::class.simpleName ?: "parse error")
            } ?: return EventInspectResult.Unparseable("not an event, an EVENT frame or an nembed")

        return EventInspectResult.Parsed(inspect(event, source))
    }

    fun inspect(
        event: Event,
        source: EventSource = EventSource.JSON,
    ): EventInspection {
        val computedId = event.generateId()
        val idValid = computedId == event.id
        val signatureValid =
            try {
                event.verifySignature()
            } catch (_: Exception) {
                false
            }
        val info = KindNames.infoFor(event.kind)

        val issues =
            buildList {
                if (!idValid) add(EventIssue.ID_MISMATCH)
                if (!signatureValid) add(EventIssue.BAD_SIGNATURE)
                if (event.createdAt > TimeUtils.now() + FUTURE_SKEW_SECS) add(EventIssue.CREATED_IN_FUTURE)
                if (info == null) add(EventIssue.UNKNOWN_KIND)
            }

        return EventInspection(
            event = event,
            source = source,
            idValid = idValid,
            computedId = computedId,
            signatureValid = signatureValid,
            kindName = info?.name,
            kindNip = info?.nip,
            issues = issues.toImmutableList(),
            codes = EventCodes.of(event),
        )
    }

    private fun unwrap(input: String): Pair<Event, EventSource>? {
        if (input.startsWith("{")) return Event.fromJson(input) to EventSource.JSON

        if (input.startsWith("[")) {
            // A relay frame carries a subscription id before the event, a client command
            // does not; try the relay side first since that is what people copy from logs.
            val asMessage = runCatching { Message.fromJson(input) }.getOrNull()
            if (asMessage is EventMessage) return asMessage.event to EventSource.RELAY_MESSAGE
            val asCommand = runCatching { Command.fromJson(input) }.getOrNull()
            if (asCommand is EventCmd) return asCommand.event to EventSource.CLIENT_COMMAND
            return null
        }

        val embed = Nip19Parser.uriToRoute(input)?.entity as? NEmbed ?: return null
        return embed.event to EventSource.NEMBED
    }
}
