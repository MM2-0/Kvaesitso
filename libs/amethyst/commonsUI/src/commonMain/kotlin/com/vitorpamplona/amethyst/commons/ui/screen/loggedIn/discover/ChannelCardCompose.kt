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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.CheckHiddenFeedWatchBlockAndReport
import com.vitorpamplona.amethyst.commons.ui.note.ClickableNote
import com.vitorpamplona.amethyst.commons.ui.note.LongPressToQuickAction
import com.vitorpamplona.amethyst.commons.ui.note.WatchNoteEvent
import com.vitorpamplona.amethyst.commons.ui.note.calculateBackgroundColor
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip23LongForm.RenderLongFormThumb
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip28Chats.RenderPublicChatChannelThumb
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip51FollowSets.RenderFollowSetThumb
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip53LiveActivities.RenderLiveActivityThumb
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip72Communities.RenderCommunitiesThumb
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip90DVMs.RenderContentDVMThumb
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip99Classifieds.RenderClassifiedsThumb
import com.vitorpamplona.amethyst.commons.ui.theme.HalfPadding
import com.vitorpamplona.amethyst.commons.ui.theme.StdPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent

@Composable
fun ChannelCardCompose(
    baseNote: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    parentBackgroundColor: MutableState<Color>? = null,
    forceEventKind: Int?,
    isHiddenFeed: Boolean = false,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchNoteEvent(baseNote = baseNote, accountViewModel = accountViewModel, nav) {
        if (forceEventKind == null || baseNote.event?.kind == forceEventKind) {
            CheckHiddenFeedWatchBlockAndReport(
                note = baseNote,
                modifier = modifier,
                ignoreAllBlocksAndReports = isHiddenFeed,
                showHiddenWarning = false,
                accountViewModel = accountViewModel,
                nav = nav,
            ) { _ ->
                NormalChannelCard(
                    baseNote = baseNote,
                    routeForLastRead = routeForLastRead,
                    modifier = modifier,
                    parentBackgroundColor = parentBackgroundColor,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
fun NormalChannelCard(
    baseNote: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LongPressToQuickAction(baseNote, accountViewModel, nav) { showPopup ->
        CheckNewAndRenderChannelCard(
            baseNote,
            routeForLastRead,
            modifier,
            parentBackgroundColor,
            accountViewModel,
            showPopup,
            nav,
        )
    }
}

@Composable
private fun CheckNewAndRenderChannelCard(
    baseNote: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    showPopup: () -> Unit,
    nav: INav,
) {
    val backgroundColor =
        calculateBackgroundColor(
            createdAt = baseNote.createdAt(),
            routeForLastRead = routeForLastRead,
            parentBackgroundColor = parentBackgroundColor,
            accountViewModel = accountViewModel,
        )

    ClickableNote(
        baseNote = baseNote,
        backgroundColor = backgroundColor,
        modifier = modifier,
        accountViewModel = accountViewModel,
        showPopup = showPopup,
        nav = nav,
    ) {
        InnerChannelCardWithReactions(
            baseNote = baseNote,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun InnerChannelCardWithReactions(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (baseNote.event) {
        is LiveActivitiesEvent -> InnerCardRow(baseNote, accountViewModel, nav)
        is MeetingSpaceEvent -> InnerCardRow(baseNote, accountViewModel, nav)
        is MeetingRoomEvent -> InnerCardRow(baseNote, accountViewModel, nav)
        is CommunityDefinitionEvent -> InnerCardRow(baseNote, accountViewModel, nav)
        is ChannelCreateEvent -> InnerCardRow(baseNote, accountViewModel, nav)
        is ClassifiedsEvent -> InnerCardBox(baseNote, accountViewModel, nav)
        is AppDefinitionEvent -> InnerCardRow(baseNote, accountViewModel, nav)
        is StarterPackEvent -> InnerCardRow(baseNote, accountViewModel, nav)
        is LongFormContentEvent -> InnerCardRow(baseNote, accountViewModel, nav)
    }
}

@Composable
fun InnerCardRow(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(StdPadding) {
        SensitivityWarning(
            note = baseNote,
            accountViewModel = accountViewModel,
        ) {
            RenderNoteRow(
                baseNote,
                accountViewModel,
                nav,
            )
        }
    }
}

@Composable
fun InnerCardBox(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(HalfPadding) {
        SensitivityWarning(
            note = baseNote,
            accountViewModel = accountViewModel,
        ) {
            RenderClassifiedsThumb(baseNote, accountViewModel, nav)
        }
    }
}

@Composable
private fun RenderNoteRow(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (baseNote.event) {
        is LiveActivitiesEvent -> RenderLiveActivityThumb(baseNote, accountViewModel, nav)
        is MeetingSpaceEvent -> RenderLiveActivityThumb(baseNote, accountViewModel, nav)
        is MeetingRoomEvent -> RenderLiveActivityThumb(baseNote, accountViewModel, nav)
        is CommunityDefinitionEvent -> RenderCommunitiesThumb(baseNote, accountViewModel, nav)
        is ChannelCreateEvent -> RenderPublicChatChannelThumb(baseNote, accountViewModel, nav)
        is AppDefinitionEvent -> RenderContentDVMThumb(baseNote, accountViewModel, nav)
        is StarterPackEvent -> RenderFollowSetThumb(baseNote, accountViewModel, nav)
        is LongFormContentEvent -> RenderLongFormThumb(baseNote, accountViewModel, nav)
    }
}
