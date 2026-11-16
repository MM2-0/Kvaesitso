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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedDefinition
import com.vitorpamplona.amethyst.commons.feeds.TopNavFilterState
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.nip52Calendar.ui.CalendarsViewMode
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.select_list_to_filter
import com.vitorpamplona.amethyst.commons.search.SearchSeed
import com.vitorpamplona.amethyst.commons.search.asSearchQuery
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.FeedFilterSpinner
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.UserDrawerSearchTopBar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent

@Composable
fun CalendarsTopBar(
    viewMode: CalendarsViewMode,
    onViewModeChange: (CalendarsViewMode) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
    // Optional trailing slot for screen-specific extras (the calendar-membership filter
    // currently lives here). Kept generic so future controls can plug in the same way without
    // top-bar surgery.
    trailing: (@Composable () -> Unit)? = null,
) {
    val list by accountViewModel.account.settings.defaultCalendarsFollowList
        .collectAsStateWithLifecycle()

    // The appointment kinds this screen shows, plus whatever the list spinner narrowed it to —
    // a hashtag or a geohash says itself as a token; a follow set does not, and seeds nothing.
    val me = accountViewModel.userProfile().pubkeyHex
    val seed =
        remember(list, me) {
            SearchSeed.merge(SearchSeed.ofKinds(CalendarTimeSlotEvent.KIND, CalendarDateSlotEvent.KIND), list.asSearchQuery(me))
        }

    Column {
        UserDrawerSearchTopBar(accountViewModel, nav, seed) {
            CalendarsTopNavFilterBar(
                followListsModel = accountViewModel.feedStates.feedListOptions,
                listName = list,
                accountViewModel = accountViewModel,
                onChange = { accountViewModel.account.settings.changeDefaultCalendarsFollowList(it.code) },
            )
        }

        CalendarsViewModeTabs(
            current = viewMode,
            onChange = onViewModeChange,
            trailing = trailing,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun CalendarsTopNavFilterBar(
    followListsModel: TopNavFilterState,
    listName: TopFilter,
    accountViewModel: AccountViewModel,
    onChange: (FeedDefinition) -> Unit,
) {
    val allLists by followListsModel.kind3GlobalPeopleRoutes.collectAsStateWithLifecycle()

    FeedFilterSpinner(
        placeholderCode = listName,
        explainer = stringRes(Res.string.select_list_to_filter),
        options = allLists,
        onSelect = onChange,
        accountViewModel = accountViewModel,
    )
}

@Composable
private fun CalendarsViewModeTabs(
    current: CalendarsViewMode,
    onChange: (CalendarsViewMode) -> Unit,
    trailing: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // The trailing control scrolls with the lenses instead of being pinned beside them: pinned,
    // it took its width out of the scrolling part, and once the lenses outgrew what was left
    // (on an 800px tablet, after "Friends going" joined them) the last lens sat half-hidden
    // against it with nothing to say the row scrolls. A chip cut by the screen edge does.
    Row(
        modifier =
            modifier
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CalendarsViewMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == current,
                onClick = { onChange(mode) },
                label = {
                    Text(
                        text = stringRes(mode.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                modifier = Modifier.padding(end = 6.dp),
                colors = FilterChipDefaults.filterChipColors(),
                shape = MaterialTheme.shapes.small,
            )
        }
        trailing?.invoke()
    }
}
