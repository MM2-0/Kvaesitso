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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip43RelayMembers.ui.RelayAddMemberCard
import com.vitorpamplona.amethyst.commons.nip43RelayMembers.ui.RelayJoinRequestCard
import com.vitorpamplona.amethyst.commons.nip43RelayMembers.ui.RelayLeaveRequestCard
import com.vitorpamplona.amethyst.commons.nip43RelayMembers.ui.RelayMembershipListCard
import com.vitorpamplona.amethyst.commons.nip43RelayMembers.ui.RelayRemoveMemberCard
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip43RelayMembers.addMember.RelayAddMemberEvent
import com.vitorpamplona.quartz.nip43RelayMembers.list.RelayMembershipListEvent
import com.vitorpamplona.quartz.nip43RelayMembers.removeMember.RelayRemoveMemberEvent

// Dispatcher entries for the NIP-43 relay-membership kinds. The cards draw only from the event,
// so the entries just decode the note; the rendering is in commonsUI nip43RelayMembers/ui.

@Composable
fun RenderRelayMembershipList(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? RelayMembershipListEvent ?: return
    RelayMembershipListCard(noteEvent)
}

@Composable
fun RenderRelayAddMember(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? RelayAddMemberEvent ?: return
    RelayAddMemberCard(noteEvent)
}

@Composable
fun RenderRelayRemoveMember(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? RelayRemoveMemberEvent ?: return
    RelayRemoveMemberCard(noteEvent)
}

@Composable
fun RenderRelayJoinRequest(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) = RelayJoinRequestCard()

@Composable
fun RenderRelayLeaveRequest(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) = RelayLeaveRequestCard()
