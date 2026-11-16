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
package com.vitorpamplona.amethyst.commons.nipsOnNostr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nipsOnNostr.NipLinks
import com.vitorpamplona.amethyst.commons.nipsOnNostr.NipsOnNostr
import com.vitorpamplona.amethyst.commons.nipsOnNostr.dal.KindEventsFeedViewModel
import com.vitorpamplona.amethyst.commons.nipsOnNostr.ui.datasource.KindEventsSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.event_kind_inspect
import com.vitorpamplona.amethyst.commons.resources.event_kind_nips_on_nostr
import com.vitorpamplona.amethyst.commons.resources.event_kind_no_definitions
import com.vitorpamplona.amethyst.commons.resources.event_kind_no_recent
import com.vitorpamplona.amethyst.commons.resources.event_kind_official
import com.vitorpamplona.amethyst.commons.resources.event_kind_recent
import com.vitorpamplona.amethyst.commons.resources.event_kind_title
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.kinds.KindNames

/**
 * An event kind's own page: its name, the official NIP that defines it (when the registry knows
 * one), the NIPs published on Nostr whose `k` tags define it, and a sample of its recent events,
 * each drawn the way the feed draws it. Both lists are feeds over the cache (the definitions the
 * account's NIPs feed, the sample this page's own), kept live by their subscriptions while the page
 * is open. Reached from a NIP's kind chips and from the card of an event the app can't render.
 */
@Composable
fun EventKindScreen(
    kind: Int,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val eventsModel: KindEventsFeedViewModel =
        rememberViewModel(key = "KindEvents-$kind", factory = KindEventsFeedViewModel.Factory(kind, accountViewModel.account))

    // The definitions are read from the account's NIPs feed; the sample is this page's own feed. Each
    // is kept current from the cache and fed by its own live subscription.
    val definitions = rememberNipsDefining(kind, accountViewModel)
    val loadingDefinitions = definitions.isEmpty() && feedNotes(accountViewModel.feedStates.nipsFeed).second
    WatchLifecycleAndUpdateModel(eventsModel)
    KindEventsSubscription(kind, accountViewModel)
    val (recent, loadingRecent) = feedNotes(eventsModel.feedState)

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.event_kind_title, kind), nav) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item(key = "header") { KindHeader(kind, remember(definitions) { NipsOnNostr.labelOf(kind, definitions) }, nav) }

            item(key = "defs-title") { SectionTitle(stringRes(Res.string.event_kind_nips_on_nostr), loadingDefinitions) }
            if (definitions.isEmpty() && !loadingDefinitions) {
                item(key = "defs-empty") { EmptyLine(stringRes(Res.string.event_kind_no_definitions)) }
            }
            items(definitions, key = { "def-" + it.idHex }) { note ->
                NoteCompose(baseNote = note, quotesLeft = 1, accountViewModel = accountViewModel, nav = nav)
                HorizontalDivider(thickness = 0.5.dp)
            }

            item(key = "recent-title") { SectionTitle(stringRes(Res.string.event_kind_recent), loadingRecent) }
            if (recent.isEmpty() && !loadingRecent) {
                item(key = "recent-empty") { EmptyLine(stringRes(Res.string.event_kind_no_recent)) }
            }
            items(recent, key = { "recent-" + it.idHex }) { note ->
                NoteCompose(baseNote = note, quotesLeft = 1, accountViewModel = accountViewModel, nav = nav)
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun KindHeader(
    kind: Int,
    nameFromNip: String?,
    nav: INav,
) {
    val name = knownKindName(kind) ?: nameFromNip
    val officialNip = KindNames.nipFor(kind)?.let { NipLinks.normalize(it) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = name ?: stringRes(Res.string.event_kind_title, kind),
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (name != null) {
                NipPill(
                    label = stringRes(Res.string.event_kind_title, kind),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            officialNip?.let { nip ->
                NipPill(
                    label = stringRes(Res.string.event_kind_official, nip),
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                    bold = true,
                    onClick = { nav.nav(Route.Nip(nip)) },
                )
            }
        }
        Row(
            modifier = Modifier.clickable { nav.nav(Route.DevToolsKindUsage(kind)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MaterialSymbols.Code, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(6.dp))
            Text(stringRes(Res.string.event_kind_inspect), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
private fun SectionTitle(
    text: String,
    loading: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.grayText,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
