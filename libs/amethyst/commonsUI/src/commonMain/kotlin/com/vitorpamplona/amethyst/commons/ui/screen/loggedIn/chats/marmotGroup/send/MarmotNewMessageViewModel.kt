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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.latestMarmotEdit
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiSuggestionState
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.ChatFileUploadState
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.ImmutableList

/**
 * Composition state for the Marmot/MLS group message field, mirroring the
 * structure of the other chat composers (ChatNewMessageViewModel,
 * ChannelNewMessageViewModel, NestNewMessageViewModel): @-mention
 * suggestions, reply state, and file-upload state. Sending goes through
 * AccountViewModel.sendMarmotGroupMessage, which owns mention tagging.
 */
@Stable
open class MarmotNewMessageViewModel : ViewModel() {
    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    var nostrGroupId: HexKey? = null
    var chatroom: MarmotGroupChatroom? = null

    val message = TextFieldState()
    val replyTo = mutableStateOf<Note?>(null)

    // My own message being replaced; the next send publishes a kind:1009 edit of it.
    val editingMessage = mutableStateOf<Note?>(null)

    var uploadState by mutableStateOf<ChatFileUploadState?>(null)
    var userSuggestions: UserSuggestionState? = null
    var emojiSuggestions: EmojiSuggestionState? = null

    open fun init(accountVM: AccountViewModel) {
        // Called from the chat screen's composable body, so on every recomposition. Re-running the
        // setup would swap the suggestion states under the composer and reset an upload in flight;
        // only an account change needs it.
        if (::accountViewModel.isInitialized && this.accountViewModel === accountVM) return

        this.accountViewModel = accountVM
        this.account = accountVM.account

        this.userSuggestions?.reset()
        this.userSuggestions =
            UserSuggestionState(
                accountVM.account,
                accountVM.nip05ClientBuilder(),
                priorityPubkeys = { chatroom?.members?.value?.mapTo(mutableSetOf()) { it.pubkey } ?: emptySet() },
            )

        this.emojiSuggestions?.reset()
        this.emojiSuggestions = EmojiSuggestionState(accountVM.account.emoji)

        this.uploadState = ChatFileUploadState(account.settings.defaultFileServer, account.settings.stripLocationOnUpload)
    }

    open fun load(nostrGroupId: HexKey) {
        if (this.nostrGroupId != nostrGroupId) {
            this.nostrGroupId = nostrGroupId
            this.chatroom = account.marmotGroupList.getOrCreateGroup(nostrGroupId)
            this.message.clearText()
            this.replyTo.value = null
            this.editingMessage.value = null
        }
    }

    fun reply(note: Note) {
        replyTo.value = note
        // Leaving edit mode drops the edited message's prefilled text too; kept, Send would
        // post it again as a new reply.
        if (editingMessage.value != null) cancelEdit()
    }

    /** Enter edit mode for my own [note], prefilled with the text it currently shows. */
    fun editMarmotMessage(note: Note) {
        replyTo.value = null
        editingMessage.value = note
        val current = note.latestMarmotEdit()?.event?.content ?: note.event?.content ?: ""
        message.setTextAndPlaceCursorAtEnd(current)
    }

    fun cancelEdit() {
        editingMessage.value = null
        message.clearText()
    }

    fun clearReply() {
        replyTo.value = null
    }

    fun editFromDraft(draftMessage: String) {
        message.setTextAndPlaceCursorAtEnd(draftMessage)
    }

    fun canPost() = message.text.isNotBlank()

    fun onMessageChanged() {
        if (message.selection.collapsed) {
            val lastWord = message.currentWord()
            if (lastWord.startsWith("@")) {
                userSuggestions?.processCurrentWord(lastWord)
            } else {
                userSuggestions?.reset()
            }
            emojiSuggestions?.processCurrentWord(lastWord)
        }
    }

    /**
     * Completes the word under the cursor with the emoji's `:shortcode:`. Both picks of the
     * shared list land here: the send path attaches the picture encrypted, so the emoji's public
     * URL is never put in the message, where members would have to fetch it.
     */
    fun autocompleteWithEmoji(item: EmojiPackState.EmojiMedia) {
        emojiSuggestions?.autocompleteInto(message, item)
    }

    fun autocompleteWithUser(item: User) {
        userSuggestions?.let {
            it.replaceCurrentWord(message, message.currentWord(), item)
            it.reset()
        }
    }

    fun pickedMedia(media: ImmutableList<SelectedMedia>) {
        uploadState?.load(media)
    }

    /**
     * Sends the field's text. Mention rewriting and p-tagging happen in
     * AccountViewModel.sendMarmotGroupMessage.
     *
     * Returns once the message is on screen, not once it has been published:
     * encryption and the relay hand-off run on the account scope and report
     * themselves on the bubble. A message with custom emoji appears once its
     * pictures are uploaded, which also runs on the account scope. Only a failure to even build the message
     * throws here — anything later shows up as a failed bubble the user can
     * retry, so the composer is free to clear as soon as this returns.
     */
    suspend fun sendPost() {
        val groupId = nostrGroupId ?: return
        val text = message.text.toString().trim()
        if (text.isEmpty()) return

        val editing = editingMessage.value
        if (editing != null) {
            accountViewModel.sendMarmotGroupMessageEdit(groupId, editing, text)
            editingMessage.value = null
            onUiThread { message.clearText() }
            userSuggestions?.reset()
            emojiSuggestions?.reset()
            return
        }

        // Capture id+pubKey snapshot before suspending so a slow send
        // doesn't race a user-cleared reply state.
        val parentEvent = replyTo.value?.event

        // Custom emoji travel as encrypted pictures of the message (MDK 0.12+).
        accountViewModel.sendMarmotGroupMessageWithCustomEmoji(
            nostrGroupId = groupId,
            text = text,
            replyToInnerEventId = parentEvent?.id,
            replyToInnerAuthorPubKey = parentEvent?.pubKey,
        )

        onUiThread { message.clearText() }
        replyTo.value = null
        userSuggestions?.reset()
        emojiSuggestions?.reset()
    }
}
