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
package com.vitorpamplona.amethyst.commons.relayClient.event

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.amethyst.commons.relayClient.event.observeCommunityApprovalNeedStatus as sharedObserveCommunityApprovalNeedStatus
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote as sharedObserveNote
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteAndMap as sharedObserveNoteAndMap
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent as sharedObserveNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEventAndMap as sharedObserveNoteEventAndMap
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEventAndMapNotNull as sharedObserveNoteEventAndMapNotNull
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteHasEvent as sharedObserveNoteHasEvent
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteMinichatReplyCount as sharedObserveNoteMinichatReplyCount
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteModifications as sharedObserveNoteModifications
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteOts as sharedObserveNoteOts
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteReactionCount as sharedObserveNoteReactionCount
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteReactions as sharedObserveNoteReactions
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteReferences as sharedObserveNoteReferences
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteReplies as sharedObserveNoteReplies
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteReplyCount as sharedObserveNoteReplyCount
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteRepostCount as sharedObserveNoteRepostCount
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteReposts as sharedObserveNoteReposts
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteRepostsBy as sharedObserveNoteRepostsBy
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteZaps as sharedObserveNoteZaps

/*
 * Android overloads of the shared per-note observers in commons `relayClient/event`: they take the
 * account and event-finder data source from [AccountViewModel] instead of the composition locals,
 * so they also work under Activity roots that don't provide those locals.
 */

@Composable
fun observeNote(
    note: Note,
    accountViewModel: AccountViewModel,
): State<NoteState> = sharedObserveNote(note, accountViewModel.account, accountViewModel.dataSources().eventFinder)

@Composable
inline fun <reified T : Event> observeNoteEvent(
    note: Note,
    accountViewModel: AccountViewModel,
): State<T?> = sharedObserveNoteEvent<T>(note, accountViewModel.account, accountViewModel.dataSources().eventFinder)

@Composable
fun <T> observeNoteAndMap(
    note: Note,
    accountViewModel: AccountViewModel,
    map: (Note) -> T,
): State<T> = sharedObserveNoteAndMap<T>(note, accountViewModel.account, accountViewModel.dataSources().eventFinder, map)

@Composable
fun <T, U> observeNoteEventAndMapNotNull(
    note: Note,
    accountViewModel: AccountViewModel,
    map: (T) -> U,
): State<U?> = sharedObserveNoteEventAndMapNotNull<T, U>(note, accountViewModel.account, accountViewModel.dataSources().eventFinder, map)

@Composable
fun <T, U> observeNoteEventAndMap(
    note: Note,
    accountViewModel: AccountViewModel,
    map: (T?) -> U,
): State<U> = sharedObserveNoteEventAndMap<T, U>(note, accountViewModel.account, accountViewModel.dataSources().eventFinder, map)

@Composable
fun observeNoteHasEvent(
    note: Note,
    accountViewModel: AccountViewModel,
): State<Boolean> = sharedObserveNoteHasEvent(note, accountViewModel.account, accountViewModel.dataSources().eventFinder)

@Composable
fun observeNoteReplies(
    note: Note,
    accountViewModel: AccountViewModel,
): State<NoteState?> = sharedObserveNoteReplies(note, accountViewModel.account, accountViewModel.dataSources().eventFinder)

@Composable
fun observeNoteReplyCount(
    note: Note,
    accountViewModel: AccountViewModel,
): State<Int> = sharedObserveNoteReplyCount(note, accountViewModel.account, accountViewModel.dataSources().eventFinder)

@Composable
fun observeNoteMinichatReplyCount(
    note: Note,
    accountViewModel: AccountViewModel,
): State<Int> = sharedObserveNoteMinichatReplyCount(note, accountViewModel.account, accountViewModel.dataSources().eventFinder)

@Composable
fun observeNoteReactions(
    note: Note,
    accountViewModel: AccountViewModel,
): State<NoteState?> = sharedObserveNoteReactions(note, accountViewModel.account, accountViewModel.dataSources().eventFinder)

@Composable
fun observeNoteReactionCount(
    note: Note,
    accountViewModel: AccountViewModel,
): State<Int> = sharedObserveNoteReactionCount(note, accountViewModel.account, accountViewModel.dataSources().eventFinder)

@Composable
fun observeNoteZaps(
    note: Note,
    accountViewModel: AccountViewModel,
): State<NoteState?> = sharedObserveNoteZaps(note, accountViewModel.account, accountViewModel.dataSources().eventFinder)

@Composable
fun observeNoteReposts(
    note: Note,
    accountViewModel: AccountViewModel,
): State<NoteState?> = sharedObserveNoteReposts(note, accountViewModel.account, accountViewModel.dataSources().eventFinder)

@Composable
fun observeNoteRepostsBy(
    note: Note,
    user: User,
    accountViewModel: AccountViewModel,
): State<Boolean> = sharedObserveNoteRepostsBy(note, user, accountViewModel.account, accountViewModel.dataSources().eventFinder)

@Composable
fun observeNoteRepostCount(
    note: Note,
    accountViewModel: AccountViewModel,
): State<Int> = sharedObserveNoteRepostCount(note, accountViewModel.account, accountViewModel.dataSources().eventFinder)

@Composable
fun observeNoteReferences(
    note: Note,
    accountViewModel: AccountViewModel,
): State<Boolean> = sharedObserveNoteReferences(note, accountViewModel.account, accountViewModel.dataSources().eventFinder)

@Composable
fun observeNoteOts(
    note: Note,
    accountViewModel: AccountViewModel,
): State<NoteState?> = sharedObserveNoteOts(note, accountViewModel.account, accountViewModel.dataSources().eventFinder)

@Composable
fun observeNoteModifications(
    note: Note,
    accountViewModel: AccountViewModel,
): State<List<Note>?> = sharedObserveNoteModifications(note, accountViewModel.account, accountViewModel.dataSources().eventFinder)

@Composable
fun observeCommunityApprovalNeedStatus(
    note: Note,
    community: Note,
    accountViewModel: AccountViewModel,
): State<Boolean?> = sharedObserveCommunityApprovalNeedStatus(note, community, accountViewModel.account, accountViewModel.dataSources().eventFinder)
