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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drss_lists_empty_sets
import com.vitorpamplona.amethyst.commons.resources.drss_new_collection_placeholder
import com.vitorpamplona.amethyst.commons.resources.drss_no_write_list
import com.vitorpamplona.amethyst.commons.resources.drss_save_to_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip51Lists.articleCurationSet.ArticleCurationSetEvent

/**
 * Saves an article into one of the collections drss.io writes to (NIP-51 article curation sets
 * under the write folder), or a new one put there. A tap on a collection saves or unsaves.
 */
@Composable
fun DrssSaveToScreen(
    articleAddress: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val article = remember(articleAddress) { Address.parse(articleAddress) } ?: return
    val account = accountViewModel.account
    val drss = account.drss
    val version by drss.version.collectAsStateWithLifecycle()
    val write by drss.writeList.collectAsStateWithLifecycle()
    val sets = remember(version, write) { drss.writableSets(ArticleCurationSetEvent.KIND) }
    val articleNote = remember(article) { LocalCache.getOrCreateAddressableNote(article) }
    val articleEvent by observeNoteEvent<LongFormContentEvent>(articleNote, accountViewModel)
    val olderLists = remember(version) { drss.eventLists() }

    DrssScreenFrame(stringRes(Res.string.drss_save_to_title), nav) {
        articleEvent?.title()?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()
        }
        DrssCreateRow(stringRes(Res.string.drss_new_collection_placeholder)) { title ->
            accountViewModel.launchSigner { drss.newCollection(title, article, account) }
        }
        if (write == null) DrssHint(stringRes(Res.string.drss_no_write_list))
        HorizontalDivider()
        if (sets.isEmpty()) DrssHint(stringRes(Res.string.drss_lists_empty_sets))
        if (sets.isNotEmpty() || olderLists.isNotEmpty()) {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(sets, key = { it.note.address.toValue() }) { set ->
                    DrssToggleRow(
                        note = set.note,
                        folder = set.folder,
                        isIn = { event -> (event as? ArticleCurationSetEvent)?.publicItemAddressIds()?.contains(article.toValue()) == true },
                        onToggle = { accountViewModel.launchSigner { drss.toggleSaved(set.note.address, article, account) } },
                        accountViewModel = accountViewModel,
                    )
                }
                // drss's older collections hold this version of the article, by its event id.
                if (articleEvent != null) drssEventListRows(olderLists, articleNote, accountViewModel, withHeading = true)
            }
        }
    }
}
