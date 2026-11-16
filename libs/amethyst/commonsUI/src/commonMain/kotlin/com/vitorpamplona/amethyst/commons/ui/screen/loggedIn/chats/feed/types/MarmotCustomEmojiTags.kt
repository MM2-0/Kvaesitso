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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.marmotGroups.marmotGroupIdFor
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.marmot.appComponents.EncryptedMediaV2Cipher
import com.vitorpamplona.marmot.foundation.appEvents.MarmotCustomEmoji

/**
 * The tags a Marmot message renders with, or null when [note] is not a Marmot
 * message (the caller then uses its own tags).
 *
 * A Marmot custom emoji's URL names ciphertext: an attachment of the same
 * message, opened with the media key of the epoch that carried it. So:
 * - an emoji tag no attachment backs is dropped, and its `:shortcode:` stays
 *   literal text; its URL is never fetched;
 * - a backed emoji tag is held back until its cipher is registered with the
 *   download layer, so the first fetch of that URL already decrypts. Until
 *   then the shortcode shows as text, never as a broken image.
 */
@Composable
fun rememberMarmotRenderTags(
    note: Note,
    accountViewModel: AccountViewModel,
): ImmutableListOfLists<String>? {
    val event = note.event
    val groupId = remember(note) { note.marmotGroupIdFor(accountViewModel.account) }
    // Every chat row passes through here; only a Marmot message has work to do.
    if (groupId == null) return null
    val matched = remember(event) { event?.let { MarmotCustomEmoji.matchedEmojiAttachments(it) } ?: emptyMap() }
    val withoutEmoji =
        remember(event) {
            event
                ?.tags
                ?.filter { !(it.isNotEmpty() && it[0] == MarmotCustomEmoji.TAG_EMOJI) }
                ?.toTypedArray()
                ?.toImmutableListOfLists()
        }

    val tags by
        produceState(withoutEmoji, event, groupId) {
            // The initial value only applies once: a later event (an edit replacing an edit) must
            // not keep the previous one's tags while its own are resolved.
            value = withoutEmoji
            if (event == null || matched.isEmpty()) return@produceState
            val secret = accountViewModel.marmotMediaSecretForMessage(groupId, note.idHex) ?: return@produceState
            matched.forEach { (url, reference) ->
                if (url.startsWith("https://") || url.startsWith("http://")) {
                    accountViewModel.account.encryptionKeyCache.add(url, EncryptedMediaV2Cipher(secret, reference), reference.mediaType)
                }
            }
            value = MarmotCustomEmoji.safeRenderTags(event).toImmutableListOfLists()
        }

    return tags
}
