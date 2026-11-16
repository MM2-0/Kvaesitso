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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip52Calendar.ui.CalendarRsvpButtons
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.RSVPStatusTag
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent

/**
 * Renders a 3-button RSVP row (Going / Maybe / Can't go) below a NIP-52 calendar appointment.
 *
 * Uses a deterministic d-tag derived from the target appointment's address so the user has
 * exactly one RSVP per event from this client — tapping again replaces it rather than appending
 * another addressable. The button matching the current status renders as filled-tonal; the
 * others render outlined.
 */
@Composable
fun CalendarRsvpRow(
    eventKind: Int,
    eventPubKey: String,
    eventDTag: String,
    eventId: String,
    accountViewModel: AccountViewModel,
) {
    val myPubKey = accountViewModel.userProfile().pubkeyHex
    val targetAddress = remember(eventKind, eventPubKey, eventDTag) { Address(eventKind, eventPubKey, eventDTag) }
    val myRsvpAddress = remember(targetAddress, myPubKey) { rsvpAddressFor(myPubKey, targetAddress) }

    val myRsvpNote = remember(myRsvpAddress) { LocalCache.getOrCreateAddressableNote(myRsvpAddress) }
    val myRsvpState by myRsvpNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()

    val currentStatus = (myRsvpState.note.event as? CalendarRSVPEvent)?.status()

    val onTap: (RSVPStatusTag.STATUS) -> Unit = { newStatus ->
        sendRsvp(
            accountViewModel = accountViewModel,
            targetAddress = targetAddress,
            eventId = eventId,
            myPubKey = myPubKey,
            status = newStatus,
        )
    }

    CalendarRsvpButtons(currentStatus, onTap)
}

/**
 * Deterministic per-target d-tag so each user's RSVP for a given event is a single addressable.
 * The format mirrors the a-tag coordinate so it's debuggable (`rsvp:31923:<pubkey>:<dtag>`).
 */
fun rsvpDTagFor(targetAddress: Address): String = "rsvp:${targetAddress.kind}:${targetAddress.pubKeyHex}:${targetAddress.dTag}"

fun rsvpAddressFor(
    myPubKey: String,
    targetAddress: Address,
): Address = Address(CalendarRSVPEvent.KIND, myPubKey, rsvpDTagFor(targetAddress))

private fun sendRsvp(
    accountViewModel: AccountViewModel,
    targetAddress: Address,
    eventId: String,
    myPubKey: String,
    status: RSVPStatusTag.STATUS,
) {
    val relayHint = LocalCache.getNoteIfExists(eventId)?.relays?.firstOrNull()
    val aTag = ATag(targetAddress, relayHint)
    // NIP-52's optional `e` tag: the `a` tag names the appointment's coordinate, which follows
    // the host's edits, while this pins the exact revision the user answered. A reader can then
    // tell an "accepted" cast against last week's time from one cast against the current one.
    val eTag = ETag(eventId, relayHint, targetAddress.pubKeyHex)
    val pTag = PTag(targetAddress.pubKeyHex)
    val dTag = rsvpDTagFor(targetAddress)

    // Only the host is p-tagged, per NIP-52 ("pubkey of the author of the calendar event being
    // responded to"). The other invitees still receive this RSVP: EventBroadcaster follows the
    // a-tag into the appointment and reads its participants' inbox relays from the appointment's
    // own p tags (CalendarTimeSlotEvent/CalendarDateSlotEvent are PubKeyHintProviders).
    //
    // Copying those participants onto the RSVP as extra p tags would add no routing - the
    // broadcaster's recursion and any local participant lookup read the same
    // LocalCache.getAddressableNoteIfExists(targetAddress), so they are reachable in exactly the
    // same cases - while giving every invitee a notification row for every other invitee's RSVP
    // (kind 31925 is in NOTIFICATION_KINDS and tagsAnEventByUser returns true for it), bloating
    // the signed event by a host-controlled number of tags, and muddying the spec's meaning of
    // this kind's p tag.
    accountViewModel.launchSigner {
        accountViewModel.account.signAndComputeBroadcast(
            CalendarRSVPEvent.build(
                calendarEventAddress = aTag,
                status = status,
                calendarEventId = eTag,
                calendarEventAuthor = pTag,
                dTag = dTag,
            ),
        )
    }
}
