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
import com.vitorpamplona.amethyst.commons.resources.kind_git_authors
import com.vitorpamplona.amethyst.commons.resources.kind_good_wiki_authors
import com.vitorpamplona.amethyst.commons.resources.kind_media_follows
import com.vitorpamplona.amethyst.commons.resources.kind_media_starter_pack
import com.vitorpamplona.amethyst.commons.resources.kind_mute_set_for_kind
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip51Lists.gitAuthorList.GitAuthorListEvent
import com.vitorpamplona.quartz.nip51Lists.goodWikiAuthorList.GoodWikiAuthorListEvent
import com.vitorpamplona.quartz.nip51Lists.kindMuteSet.KindMuteSetEvent
import com.vitorpamplona.quartz.nip51Lists.mediaFollowList.MediaFollowListEvent
import com.vitorpamplona.quartz.nip51Lists.mediaStarterPack.MediaStarterPackEvent

/** NIP-51 kind 10017: the git authors whose patches this user wants to see. */
@Composable
fun RenderGitAuthorList(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? GitAuthorListEvent ?: return

    val public = remember(noteEvent) { noteEvent.publicAuthors() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateAuthors(it) }

    ListCard(
        title = stringRes(Res.string.kind_git_authors),
        description = null,
        items = rememberAllMembers(public, private),
        hasUnreadablePrivateItems = noteEvent.hidesPrivateMembers(private),
        backgroundColor = backgroundColor,
    ) { author ->
        key(author.pubKey) { UserMemberRow(author.pubKey, accountViewModel, nav) }
    }
}

/** NIP-51 kind 10020: the people whose media this user follows. */
@Composable
fun RenderMediaFollowList(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? MediaFollowListEvent ?: return

    val public = remember(noteEvent) { noteEvent.publicFollows() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateFollows(it) }

    ListCard(
        title = stringRes(Res.string.kind_media_follows),
        description = null,
        items = rememberAllMembers(public, private),
        hasUnreadablePrivateItems = noteEvent.hidesPrivateMembers(private),
        backgroundColor = backgroundColor,
    ) { follow ->
        key(follow.pubKey) { UserMemberRow(follow.pubKey, accountViewModel, nav) }
    }
}

/** NIP-51 kind 10101: the authors whose wiki articles this user trusts. */
@Composable
fun RenderGoodWikiAuthorList(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? GoodWikiAuthorListEvent ?: return

    val public = remember(noteEvent) { noteEvent.publicAuthors() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateAuthors(it) }

    ListCard(
        title = stringRes(Res.string.kind_good_wiki_authors),
        description = null,
        items = rememberAllMembers(public, private),
        hasUnreadablePrivateItems = noteEvent.hidesPrivateMembers(private),
        backgroundColor = backgroundColor,
    ) { author ->
        key(author.pubKey) { UserMemberRow(author.pubKey, accountViewModel, nav) }
    }
}

/**
 * NIP-51 kind 30007: people muted for one kind only.
 *
 * "`d` tag MUST be the kind string" — so the `d` tag is not a name here, it is the kind being
 * muted, and printing it as a title the way other sets do would be wrong.
 */
@Composable
fun RenderKindMuteSet(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? KindMuteSetEvent ?: return

    val public = remember(noteEvent) { noteEvent.publicMutedUsers() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateMutedUsers(it) }
    val mutedKind = remember(noteEvent) { noteEvent.dTag() }

    ListCard(
        title = stringRes(Res.string.kind_mute_set_for_kind, mutedKind),
        description = null,
        items = rememberAllMembers(public, private),
        hasUnreadablePrivateItems = noteEvent.hidesPrivateMembers(private),
        backgroundColor = backgroundColor,
    ) { muted ->
        key(muted.pubKey) { UserMemberRow(muted.pubKey, accountViewModel, nav) }
    }
}

/**
 * NIP-51 kind 39092: a media starter pack — "a named set of profiles to be shared around with the
 * goal of being followed together". Public by construction: a pack exists to be handed out.
 */
@Composable
fun RenderMediaStarterPack(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? MediaStarterPackEvent ?: return

    val members = remember(noteEvent) { noteEvent.followIds() }

    ListCard(
        title = listTitle(noteEvent.title(), noteEvent.dTag(), Res.string.kind_media_starter_pack),
        description = noteEvent.description(),
        items = members,
        hasUnreadablePrivateItems = false,
        backgroundColor = backgroundColor,
    ) { pubKey ->
        key(pubKey) { UserMemberRow(pubKey, accountViewModel, nav) }
    }
}
