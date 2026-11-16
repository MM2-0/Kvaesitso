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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.drss

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drss_collect_to_title
import com.vitorpamplona.amethyst.commons.resources.drss_collection_keeps_last
import com.vitorpamplona.amethyst.commons.resources.drss_lists_empty_sets
import com.vitorpamplona.amethyst.commons.resources.drss_older_collections
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.drss.eventList.DrssEventListEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Adds the event [eventId] to one of the user's older drss collections (kind 30102, kept by event
 * id), or takes it out. drss makes new collections as curation sets; it still edits these.
 */
@Composable
fun DrssCollectToScreen(
    eventId: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val item = remember(eventId) { LocalCache.getOrCreateNote(eventId) }
    val version by accountViewModel.account.drss.version
        .collectAsStateWithLifecycle()
    val lists = remember(version) { accountViewModel.account.drss.eventLists() }

    DrssScreenFrame(stringRes(Res.string.drss_collect_to_title), nav) {
        if (lists.isEmpty()) {
            DrssHint(stringRes(Res.string.drss_lists_empty_sets))
        } else {
            LazyColumn(Modifier.fillMaxWidth()) { drssEventListRows(lists, item, accountViewModel) }
        }
    }
}

/** The older collections, each toggling [item] by its event id, under a heading. */
internal fun LazyListScope.drssEventListRows(
    lists: List<AddressableNote>,
    item: Note,
    accountViewModel: AccountViewModel,
    withHeading: Boolean = false,
) {
    if (lists.isEmpty()) return
    if (withHeading) {
        item(key = "olderTitle") {
            Text(
                text = stringRes(Res.string.drss_older_collections),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
    }
    items(lists, key = { "o" + it.address.toValue() }) { list ->
        val drss = accountViewModel.account.drss
        DrssToggleRow(
            note = list,
            folder = null,
            isIn = { event -> (event as? DrssEventListEvent)?.itemIds()?.contains(item.event?.id ?: item.idHex) == true },
            onToggle = {
                accountViewModel.launchSigner {
                    if (!drss.toggleInEventList(list, item, accountViewModel.account)) {
                        accountViewModel.toastManager.toast(Res.string.drss_older_collections, Res.string.drss_collection_keeps_last)
                    }
                }
            },
            accountViewModel = accountViewModel,
        )
    }
}
