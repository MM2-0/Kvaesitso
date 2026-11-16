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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz

import com.vitorpamplona.amethyst.commons.model.composer.NewMessageTagger
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.send.ChannelNewMessageViewModel
import com.vitorpamplona.quartz.buzz.forum.ForumCommentEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate

/**
 * The Buzz forum-reply composer. Reuses the whole rich chat composer ([ChannelNewMessageViewModel] +
 * `EditFieldRow`: emoji, mentions, media, drafts, typing) but overrides only the built event so a send
 * publishes a kind-45003 [ForumCommentEvent] scoped to the open thread instead of a chat message.
 */
class ForumReplyNewMessageViewModel : ChannelNewMessageViewModel() {
    private var forumRootId: HexKey? = null

    /** Bind to the forum [channel] and the thread [rootId] this composer replies into. */
    fun loadForumThread(
        channel: RelayGroupChannel,
        rootId: HexKey,
    ) {
        load(channel)
        forumRootId = rootId
    }

    override suspend fun createTemplate(): EventTemplate<out Event>? {
        val forumChannel = channel as? RelayGroupChannel ?: return super.createTemplate()
        val root = forumRootId ?: return super.createTemplate()
        if (message.text
                .toString()
                .trim()
                .isEmpty()
        ) {
            return null
        }

        // Resolve `@`-mentions the same way the chat composer does: the tagger collects the cited
        // users (seeded with the reply target) so they land as `p` mention tags — without this, a
        // named member was neither notified nor linked. The body keeps Buzz's plain `@Name` form.
        val tagger =
            NewMessageTagger(
                message = message.text.toString(),
                pTags = listOfNotNull(replyTo.value?.author),
                eTags = listOfNotNull(replyTo.value),
                dao = accountViewModel,
                userMentionsAsNames = true,
            )
        tagger.run()

        // A reply to another comment nests under it (parent = that comment); a top-level reply targets
        // the root (parent == root), per ForumCommentEvent.build's root/parent contract.
        val parent = replyTo.value?.idHex ?: root
        val mentions = tagger.pTags?.map { it.pubkeyHex }.orEmpty()
        return ForumCommentEvent.build(forumChannel.groupId.id, tagger.message.trim(), rootEventId = root, parentEventId = parent, mentions = mentions)
    }
}
