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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.drss.DrssTree
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drss_list_in_folder
import com.vitorpamplona.amethyst.commons.resources.drss_provenance_feed
import com.vitorpamplona.amethyst.commons.resources.drss_provenance_follow_into
import com.vitorpamplona.amethyst.commons.resources.drss_provenance_follow_none
import com.vitorpamplona.amethyst.commons.resources.drss_provenance_note
import com.vitorpamplona.amethyst.commons.resources.drss_provenance_source
import com.vitorpamplona.amethyst.commons.resources.drss_provenance_through
import com.vitorpamplona.amethyst.commons.resources.drss_provenance_title
import com.vitorpamplona.amethyst.commons.resources.drss_save
import com.vitorpamplona.amethyst.commons.resources.drss_untitled
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent

/**
 * drss.io's provenance: records which feed the user found through an article, where (a link) and
 * why (a note), files it in their provenance vault and, when they pick a list, follows the feed
 * there. drss resolves a link to a feed on its server; here the user names the feed's key.
 */
@Composable
fun DrssRecordProvenanceScreen(
    articleAddress: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val article = remember(articleAddress) { Address.parse(articleAddress) }
    val account = accountViewModel.account
    val drss = account.drss

    var feedKey by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var followInto by rememberSaveable { mutableStateOf<String?>(null) }

    val feedHex =
        remember(feedKey) {
            feedKey
                .trim()
                .removePrefix("nostr:")
                .let { runCatching { decodePublicKeyAsHexOrNull(it) }.getOrNull() }
                ?.takeIf { it.length == 64 }
        }
    val version by drss.version.collectAsStateWithLifecycle()
    val followSets = remember(version) { drss.writableSets(FollowSetEvent.KIND) }

    DrssScreenFrame(stringRes(Res.string.drss_provenance_title), nav) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            article?.let { address ->
                val articleNote = remember(address) { LocalCache.getOrCreateAddressableNote(address) }
                val articleEvent by observeNoteEvent<LongFormContentEvent>(articleNote, accountViewModel)
                Text(stringRes(Res.string.drss_provenance_through), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.grayText)
                Text(
                    articleEvent?.title() ?: stringRes(Res.string.drss_untitled),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            SpellCheckedOutlinedTextField(
                value = feedKey,
                onValueChange = { feedKey = it },
                label = { Text(stringRes(Res.string.drss_provenance_feed)) },
                singleLine = true,
                isError = feedKey.isNotBlank() && feedHex == null,
                modifier = Modifier.fillMaxWidth(),
            )
            feedHex?.let { hex ->
                LoadUser(hex) { user ->
                    if (user == null) return@LoadUser
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        UserPicture(hex, 32.dp, accountViewModel = accountViewModel, nav = nav)
                        UsernameDisplay(user, fontWeight = FontWeight.SemiBold, accountViewModel = accountViewModel)
                    }
                }
            }
            SpellCheckedOutlinedTextField(
                value = source,
                onValueChange = { source = it },
                label = { Text(stringRes(Res.string.drss_provenance_source)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            SpellCheckedOutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringRes(Res.string.drss_provenance_note)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringRes(Res.string.drss_provenance_follow_into), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.grayText)
            FollowChoice(stringRes(Res.string.drss_provenance_follow_none), followInto == null) { followInto = null }
            followSets.forEach { set ->
                val event by observeNoteEvent<Event>(set.note, accountViewModel)
                val folderTitle = set.folder?.let { observeNoteEvent<Event>(it, accountViewModel).value }?.let(DrssTree::titleOf)
                val title = DrssTree.titleOf(event) ?: stringRes(Res.string.drss_untitled)
                val key = set.note.address.toValue()
                FollowChoice(folderTitle?.let { stringRes(Res.string.drss_list_in_folder, title, it) } ?: title, followInto == key) { followInto = key }
            }

            Button(
                onClick = {
                    val hex = feedHex ?: return@Button
                    val chosen = followInto?.let(Address::parse)
                    accountViewModel.launchSigner {
                        val feed = LocalCache.getOrCreateUser(hex)
                        drss.recordProvenance(feed, source.trim(), note.trim(), article, account)
                        if (chosen != null) drss.followInto(chosen, feed, account)
                    }
                    nav.popBack()
                },
                enabled = feedHex != null && source.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringRes(Res.string.drss_save))
            }
        }
    }
}

@Composable
private fun FollowChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            symbol = if (selected) MaterialSymbols.RadioButtonChecked else MaterialSymbols.RadioButtonUnchecked,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
