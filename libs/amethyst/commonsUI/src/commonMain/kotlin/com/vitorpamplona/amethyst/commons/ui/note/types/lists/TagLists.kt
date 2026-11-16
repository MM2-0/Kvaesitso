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
import com.vitorpamplona.amethyst.commons.resources.kind_geohash_follows
import com.vitorpamplona.amethyst.commons.resources.kind_hashtag_follows
import com.vitorpamplona.amethyst.commons.resources.kind_interest_set
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListEvent
import com.vitorpamplona.quartz.nip51Lists.interestSet.InterestSetEvent

/** NIP-51 kind 10015: the hashtags this user follows. */
@Composable
fun RenderInterestList(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? InterestListEvent ?: return

    val public = remember(noteEvent) { noteEvent.publicHashtags() }
    // InterestListEvent exposes no private accessor of its own, but it is a PrivateTagArrayEvent
    // like the rest, so its private half is read the same way InterestSetEvent reads its own.
    val private by loadPrivateItems(noteEvent, accountViewModel) { signer ->
        noteEvent.privateTags(signer)?.mapNotNull(HashtagTag::parse)
    }

    ListCard(
        title = stringRes(Res.string.kind_hashtag_follows),
        description = null,
        items = rememberAllMembers(public, private),
        hasUnreadablePrivateItems = noteEvent.hidesPrivateMembers(private),
        backgroundColor = backgroundColor,
    ) { tag ->
        key(tag) { HashtagMemberRow(tag, nav) }
    }
}

/** NIP-51 kind 30015: "interest topics represented by a bunch of hashtags". */
@Composable
fun RenderInterestSet(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? InterestSetEvent ?: return

    val public = remember(noteEvent) { noteEvent.publicHashtags() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateHashtags(it) }

    ListCard(
        title = listTitle(noteEvent.title(), noteEvent.dTag(), Res.string.kind_interest_set),
        description = noteEvent.description(),
        items = rememberAllMembers(public, private),
        hasUnreadablePrivateItems = noteEvent.hidesPrivateMembers(private),
        backgroundColor = backgroundColor,
    ) { tag ->
        key(tag) { HashtagMemberRow(tag, nav) }
    }
}

/**
 * NIP-51 kind 10081: the places this user follows.
 *
 * A geohash is not a hashtag — it is a location, and it gets no `#`. Shown verbatim because a
 * geohash is what the list stores; turning it into a place name would need a geocoder we don't
 * have on this path.
 */
@Composable
fun RenderGeohashList(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
) {
    val noteEvent = baseNote.event as? GeohashListEvent ?: return

    val public = remember(noteEvent) { noteEvent.publicGeohashes() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.decryptPrivateGeohashes(it) }

    ListCard(
        title = stringRes(Res.string.kind_geohash_follows),
        description = null,
        items = rememberAllMembers(public, private),
        hasUnreadablePrivateItems = noteEvent.hidesPrivateMembers(private),
        backgroundColor = backgroundColor,
    ) { geohash ->
        key(geohash) { LabelMemberRow(geohash) }
    }
}
