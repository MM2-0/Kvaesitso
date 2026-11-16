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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import com.vitorpamplona.marmot.client.MarmotManager
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log

/**
 * Mirrors a group's MLS state (MIP-01 metadata, members, gates) onto its [MarmotGroupChatroom].
 * Call after joining a group, processing a commit, or restoring from storage.
 *
 * The chatroom is Amethyst's view model, so this lives here and reads only [MarmotManager]'s
 * public API; the library has no notion of a chatroom.
 */
fun MarmotManager.syncMetadataTo(
    nostrGroupId: HexKey,
    chatroom: MarmotGroupChatroom,
) {
    // Read through [MarmotManager.groupView], not [MarmotManager.groupMetadata]: the legacy
    // accessor returns null for every current-profile group, which left them with a blank
    // name, no admins, no relays and no avatar in the UI. The group worked; it just looked empty.
    val view = groupView(nostrGroupId)
    if (view != null) {
        if (view.name.isNotEmpty()) chatroom.displayName.value = view.name
        if (view.description.isNotEmpty()) chatroom.description.value = view.description
        chatroom.adminPubkeys.value = view.adminPubkeys
        chatroom.relays.value = view.relays
        chatroom.image.value = view.image
        chatroom.avatarUrl.value = view.avatarUrl
        // Drives which actions a front end may offer at all — a legacy
        // group has no carrier for lifecycle, URL avatar or media policy.
        chatroom.isCurrentProfile.value = view.isCurrentProfile
        chatroom.hasEncryptedMediaPolicy.value = encryptedMediaPolicy(nostrGroupId) != null
    }
    // Read every sync, because a gate is raised and cleared by protocol
    // events the UI never sees directly — a disband request resolving, a
    // removal being realized.
    chatroom.outboundGate.value = publishGate.outboundGateNow(nostrGroupId)
    chatroom.isOutOfSync.value = isDesynced(nostrGroupId)
    // A group we created is ours: keep it out of "New Requests" across restarts.
    // `markAsKnown` at creation is in-memory only, and a creator who has not posted
    // yet has nothing else that says so. See [MarmotManager.isCreatedLocally] for why
    // this is not read off the tree.
    if (isCreatedLocally(nostrGroupId)) chatroom.ownerSentMessage = true
    val previousCount = chatroom.members.value.size
    val members = memberPubkeys(nostrGroupId)
    chatroom.members.value = members
    chatroom.memberCount.value = members.size
    Log.d("MarmotDbg") {
        "syncMetadataTo: group=${nostrGroupId.take(8)}… members $previousCount→${members.size} " +
            "(leafs=${members.map { it.leafIndex }})"
    }
}
