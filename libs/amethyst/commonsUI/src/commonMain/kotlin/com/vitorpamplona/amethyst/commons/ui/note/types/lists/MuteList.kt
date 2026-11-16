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
package com.vitorpamplona.amethyst.commons.ui.note.types.lists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.kind_mute_list
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.EventTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.HashtagTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.WordTag

/**
 * NIP-51 kind 10000: the mute list.
 *
 * The only list whose members are of four different kinds at once — people, hashtags, words and
 * threads — so it is the one kind that cannot borrow another card's body. Each member is drawn as
 * what it actually is: a profile row for a person, a hashtag that opens its feed, a word as
 * itself, and a muted thread as the note it silences.
 *
 * Most mute lists are mostly private, and for anyone but their author this renders as a title and
 * an honest line saying so — which is the entire point, given that it used to render as the
 * NIP-44 ciphertext.
 */
@Composable
fun RenderMuteList(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? MuteListEvent ?: return

    val public = remember(noteEvent) { noteEvent.publicMutes() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateMutes(it) }

    ListCard(
        title = stringRes(Res.string.kind_mute_list),
        description = null,
        items = rememberAllMembers(public, private),
        hasUnreadablePrivateItems = noteEvent.hidesPrivateMembers(private),
        backgroundColor = backgroundColor,
    ) { mute ->
        when (mute) {
            is UserTag -> key(mute.pubKey) { UserMemberRow(mute.pubKey, accountViewModel, nav) }
            is HashtagTag -> key(mute.hashtag) { HashtagMemberRow(mute.hashtag, nav) }
            is WordTag -> key(mute.word) { LabelMemberRow(mute.word) }
            is EventTag ->
                key(mute.eventId) {
                    MutedThreadRow(mute.eventId, quotesLeft, backgroundColor, accountViewModel, nav)
                }
        }
    }
}

/** A muted conversation, shown as the note it silences. */
@Composable
private fun MutedThreadRow(
    eventId: String,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (quotesLeft <= 0) return

    LoadNote(eventId) { note ->
        note?.let {
            NoteCompose(
                baseNote = it,
                isQuotedNote = true,
                quotesLeft = quotesLeft - 1,
                parentBackgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}
