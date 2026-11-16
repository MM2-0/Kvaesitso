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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.highlights

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.composer.NewMessageTagger
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.EmojiMedia
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiSuggestionState
import com.vitorpamplona.amethyst.commons.ui.note.creators.messagefield.IMessageField
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import com.vitorpamplona.amethyst.commons.ui.text.replaceCurrentWord
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip01Core.tags.people.toPTag
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent

/**
 * Backs the "New Highlight" composer. A NIP-84 highlight is a quoted passage (the event
 * `content`), its source, and an optional annotation. The annotation reuses the short-note
 * composer's rich [message] field via [IMessageField], so it gets @-mention and custom-emoji
 * autocomplete and inline previews; on publish it becomes the highlight's `comment` tag with
 * the mentions, emoji, URLs, hashtags and quotes it references emitted as their own tags.
 *
 * The passage, the `textquoteselector` prefix/suffix, the surrounding `context`, and the
 * nostr source (`a`/`e`/`p`) are carried through from the share or the "Highlight this note"
 * action. When a nostr event is the source, [originalNote] is resolved so the screen can
 * render it as a reply-style preview instead of showing a URL field.
 */
@Stable
class NewHighlightPostViewModel :
    ViewModel(),
    IMessageField {
    private var accountViewModel: AccountViewModel? = null
    private var account: Account? = null

    /** The highlighted passage — becomes the event `content`. */
    var quote by mutableStateOf("")

    /** The source URL — becomes an `r` tag. Hidden when a nostr event is the source. */
    var url by mutableStateOf("")

    /** The user's annotation — the rich comment field; becomes a `comment` tag. */
    override val message = TextFieldState()

    /** The source note, when highlighting a nostr article/note, for the reply-style preview. */
    var originalNote by mutableStateOf<Note?>(null)
        private set

    var userSuggestions: UserSuggestionState? = null
    var emojiSuggestions: EmojiSuggestionState? = null

    private var prefix: String? = null
    private var suffix: String? = null
    private var context: String? = null
    private var sourceAddress: String? = null
    private var sourceEventId: String? = null
    private var author: String? = null

    private var loaded = false

    fun init(accountViewModel: AccountViewModel) {
        if (this.accountViewModel == accountViewModel) return
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
        userSuggestions = UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder())
        emojiSuggestions = EmojiSuggestionState(accountViewModel.account.emoji)
    }

    /**
     * Applies the incoming source once, and resolves [originalNote] for a nostr source. Guarded
     * so a recomposition can't clobber edits the user already made.
     */
    fun load(
        quote: String?,
        url: String?,
        prefix: String?,
        suffix: String?,
        comment: String?,
        context: String?,
        sourceAddress: String?,
        sourceEventId: String?,
        author: String?,
    ) {
        if (loaded) return
        loaded = true

        this.quote = quote.orEmpty()
        this.url = url.orEmpty()
        comment?.ifBlank { null }?.let { message.setTextAndPlaceCursorAtEnd(it) }
        this.prefix = prefix
        this.suffix = suffix
        this.context = context
        this.sourceAddress = sourceAddress
        this.sourceEventId = sourceEventId
        this.author = author

        val accountViewModel = accountViewModel
        if (accountViewModel != null) {
            originalNote =
                when {
                    !sourceAddress.isNullOrBlank() -> Address.parse(sourceAddress)?.let { LocalCache.getOrCreateAddressableNote(it) }
                    !sourceEventId.isNullOrBlank() -> LocalCache.getOrCreateNote(sourceEventId)
                    else -> null
                }
        }
    }

    override fun onMessageChanged() {
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

    fun autocompleteWithUser(item: User) {
        userSuggestions?.let {
            val lastWord = message.currentWord()
            it.replaceCurrentWord(message, lastWord, item)
            it.reset()
        }
    }

    fun autocompleteWithEmoji(item: EmojiMedia) {
        emojiSuggestions?.autocompleteInto(message, item)
    }

    fun autocompleteWithEmojiUrl(item: EmojiMedia) {
        message.replaceCurrentWord(item.link + " ")
        emojiSuggestions?.reset()
    }

    fun canPost(): Boolean = quote.isNotBlank()

    suspend fun sendHighlight() {
        val account = account ?: return
        val dao = accountViewModel ?: return
        if (!canPost()) return

        // Resolve @mentions, nostr: refs, emoji, URLs and hashtags out of the annotation the same
        // way the short-note composer does, so a highlight comment behaves like any other note.
        val tagger = NewMessageTagger(message.text.toString().trim(), null, null, dao)
        tagger.run()

        val commentText = tagger.message.ifBlank { null }
        val mentions = tagger.directMentionsUsers.map { it.toPTag() }
        val emojiTags = account.emoji.findEmojiTags(tagger.message)
        val urls = findURLs(tagger.message)
        val tags = findHashtags(tagger.message)
        val quotes = findNostrUris(tagger.message)

        account.signAndComputeBroadcast(
            HighlightEvent.build(
                quote = quote.trim(),
                url = url.trim().ifBlank { null },
                prefix = prefix,
                suffix = suffix,
                comment = commentText,
                context = context,
                address = sourceAddress,
                event = sourceEventId,
                author = author,
            ) {
                if (mentions.isNotEmpty()) pTags(mentions)
                references(urls)
                hashtags(tags)
                quotes(quotes)
                emojis(emojiTags)
            },
        )
    }
}
