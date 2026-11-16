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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.marmot_group_fallback_name
import com.vitorpamplona.amethyst.commons.resources.marmot_no_messages_yet
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_label
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_preview
import com.vitorpamplona.amethyst.commons.resources.marmot_preview_group_updated
import com.vitorpamplona.amethyst.commons.resources.marmot_preview_media
import com.vitorpamplona.amethyst.commons.resources.marmot_preview_no_text
import com.vitorpamplona.amethyst.commons.resources.marmot_preview_with_sender
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.observeChatEdit
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.hasEncryptedMediaV2
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.hasMip04Media
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.marmot.client.GroupMemberInfo
import com.vitorpamplona.marmot.foundation.appEvents.MarmotAppEvent
import com.vitorpamplona.marmot.foundation.appEvents.MarmotPolls
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * The second line of a Marmot group's row: the newest message, prefixed with its
 * sender's name. Shared by the Marmot group list and the Messages tab, which had
 * drifted: the Messages tab required an author on the note and fell back to
 * "No messages yet" for anything else, including a group whose newest entry was
 * a system row or an attachment.
 */
@Composable
fun marmotGroupPreviewText(
    newestMessage: Note?,
    accountViewModel: AccountViewModel,
): String {
    // A preview has to survive the messages that carry no text: a system row
    // keeps its state in tags and MIP-04 media keeps its in imeta, so both used
    // to render as a blank second line under the group name.
    val myPubKey = accountViewModel.account.signer.pubKey
    val previewEvent = newestMessage?.event
    // An edited last message previews its new text, as its bubble does. Observed rather than
    // read once so an edit arriving while the list is open updates the row.
    val editedContent = newestMessage?.let { observeChatEdit(it) }?.event?.content
    val shownText = editedContent ?: previewEvent?.content.orEmpty()
    val previewBody =
        when {
            newestMessage == null -> stringRes(Res.string.marmot_no_messages_yet)
            previewEvent == null -> stringRes(Res.string.marmot_preview_no_text)
            previewEvent.kind == MarmotAppEvent.KIND_SYSTEM -> stringRes(Res.string.marmot_preview_group_updated)
            // A poll's question alone reads like a plain message; MDK shows a poll label.
            previewEvent.kind == MarmotPolls.KIND_POLL ->
                MarmotPolls.parsePoll(previewEvent)?.let { stringRes(Res.string.marmot_poll_preview, it.question) }
                    ?: stringRes(Res.string.marmot_poll_label)
            // Text first: an attachment usually carries a caption, and showing
            // "Attachment" over the words the sender actually wrote would be a
            // step back from the raw `content` this replaced.
            shownText.isNotBlank() -> shownText
            hasMip04Media(previewEvent) || hasEncryptedMediaV2(previewEvent) -> stringRes(Res.string.marmot_preview_media)
            else -> stringRes(Res.string.marmot_preview_no_text)
        }
    // Only a genuinely known name earns the prefix: `bestName` returns null
    // without metadata, and "a1b2c3d4: hi" is noise, not attribution.
    //
    // Read through `observeUserInfo`, not `metadataOrNull()`: the latter is a
    // plain StateFlow.value read, so a kind:0 arriving after the row composed
    // would never reach it.
    //
    // Deliberately `getUserIfExists` rather than the `LoadUser` idiom: this only
    // subscribes for a sender the cache already knows, and a sender it does not
    // simply goes unprefixed. Creating a User per unknown sender would put a
    // metadata REQ behind every row of a list that is mostly strangers' names
    // the reader never asked for — a preview line is not worth that.
    val senderUser =
        previewEvent
            ?.pubKey
            ?.takeIf { it != myPubKey }
            ?.let { LocalCache.getUserIfExists(it) }
    val senderName =
        if (senderUser != null) {
            observeUserInfo(senderUser, accountViewModel).value?.info?.bestName()
        } else {
            null
        }
    val previewText =
        // A system caption already names its actor, so prefixing one would say it twice.
        if (senderName != null && previewEvent?.kind != MarmotAppEvent.KIND_SYSTEM) {
            stringRes(Res.string.marmot_preview_with_sender, senderName, previewBody)
        } else {
            previewBody
        }

    return previewText
}

/**
 * The members a group's row or header should picture and name: everyone but us, like a
 * NIP-17 room. A group of just us falls back to us, so a fresh group still has a face.
 */
fun marmotOtherMembers(
    members: List<GroupMemberInfo>,
    myPubKey: HexKey,
): List<HexKey> {
    val all = members.map { it.pubkey }.distinct()
    return all.filter { it != myPubKey }.ifEmpty { all }
}

/**
 * A group's title: its name, else the other members' names. White Noise creates 1:1
 * chats without a name, and "Group c4f0426f…" told the reader nothing about who was
 * on the other side.
 */
@Composable
fun marmotGroupTitle(
    displayName: String?,
    otherMembers: List<HexKey>,
    nostrGroupId: HexKey,
    accountViewModel: AccountViewModel,
): String {
    if (!displayName.isNullOrBlank()) return displayName
    if (otherMembers.isEmpty()) return stringRes(Res.string.marmot_group_fallback_name, nostrGroupId.take(8))
    val names = otherMembers.take(4).map { key(it) { observeUserNameByHex(it, accountViewModel) } }
    return names.joinToString(", ")
}
