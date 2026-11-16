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
package com.vitorpamplona.amethyst.commons.ui.note.types.trust

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nip85_designation_explainer
import com.vitorpamplona.amethyst.commons.resources.nip85_designation_no_relay
import com.vitorpamplona.amethyst.commons.resources.nip85_designation_none
import com.vitorpamplona.amethyst.commons.resources.nip85_designation_private_entries
import com.vitorpamplona.amethyst.commons.resources.nip85_designation_private_entry
import com.vitorpamplona.amethyst.commons.resources.nip85_designation_title
import com.vitorpamplona.amethyst.commons.resources.nip85_service_address_lists
import com.vitorpamplona.amethyst.commons.resources.nip85_service_address_metric
import com.vitorpamplona.amethyst.commons.resources.nip85_service_followers
import com.vitorpamplona.amethyst.commons.resources.nip85_service_hops
import com.vitorpamplona.amethyst.commons.resources.nip85_service_identifier_lists
import com.vitorpamplona.amethyst.commons.resources.nip85_service_identifier_metric
import com.vitorpamplona.amethyst.commons.resources.nip85_service_list_headers
import com.vitorpamplona.amethyst.commons.resources.nip85_service_list_items
import com.vitorpamplona.amethyst.commons.resources.nip85_service_muters
import com.vitorpamplona.amethyst.commons.resources.nip85_service_note_lists
import com.vitorpamplona.amethyst.commons.resources.nip85_service_note_metric
import com.vitorpamplona.amethyst.commons.resources.nip85_service_people_lists
import com.vitorpamplona.amethyst.commons.resources.nip85_service_rank
import com.vitorpamplona.amethyst.commons.resources.nip85_service_reporters
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.PagedMemberColumn
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.Size40dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.utils.Log
import kotlin.coroutines.cancellation.CancellationException

/** Provider rows the designation card shows before "Show more". */
private const val MAX_PROVIDER_ROWS = 10

/** Service chips one provider row shows before "+N more". */
private const val MAX_SERVICE_CHIPS = 8

/**
 * A kind-10040 Treasure Map: whose computations this person reads, and from where. It has no
 * content of its own; it is its rows, so the card lists them, one row per provider with what
 * that provider is designated for and the relay it publishes on. Mirrors Brainstorm's
 * `DesignationHero`, without the Brainstorm-only "activate" call to action.
 *
 * Private (NIP-44) rows are read through the account's own [TrustProviderListEvent] decryption
 * cache when the viewer is the author; anyone else is told that private rows exist.
 */
@Composable
fun RenderTrustProviderList(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by baseNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val event = noteState.note.event as? TrustProviderListEvent ?: return

    val privateTags by produceState<TagArray?>(initialValue = null, event) {
        if (event.content.isEmpty() || event.pubKey != accountViewModel.account.signer.pubKey) return@produceState
        value =
            try {
                accountViewModel.account.trustProviderListDecryptionCache.cachedPrivateLists
                    .privateTags(event)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("TrustProviderListCard", "Cannot decrypt the private rows of ${event.id}", e)
                null
            }
    }

    val providers = remember(event, privateTags) { designatedProviders(event.tags, privateTags) }
    val hidesPrivateRows = event.content.isNotEmpty() && privateTags == null

    TrustCardFrame {
        TrustCardHeader(
            symbol = MaterialSymbols.Security,
            title = stringRes(Res.string.nip85_designation_title),
            subtitle = stringRes(Res.string.nip85_designation_explainer),
        )

        if (providers.isEmpty() && !hidesPrivateRows) {
            Text(
                text = stringRes(Res.string.nip85_designation_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // A Treasure Map names a handful of providers; one with hundreds of rows pages instead.
        PagedMemberColumn(providers, backgroundColor = null, collapsed = MAX_PROVIDER_ROWS) { provider ->
            if (provider !== providers.first()) HorizontalDivider(thickness = DividerThickness)
            ProviderRow(provider, accountViewModel, nav)
        }

        if (hidesPrivateRows) {
            Text(
                text = stringRes(Res.string.nip85_designation_private_entries),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderRow(
    provider: DesignatedProvider,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadUser(provider.pubKey) { user ->
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (user != null) Modifier.clickable { nav.nav(routeFor(user)) } else Modifier)
                    .padding(vertical = 8.dp),
        ) {
            if (user != null) {
                UserPicture(user, Size40dp, accountViewModel = accountViewModel, nav = nav)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (user != null) {
                    UsernameDisplay(user, accountViewModel = accountViewModel)
                }
                Text(
                    text = provider.relay?.displayUrl() ?: stringRes(Res.string.nip85_designation_no_relay),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (provider.isPrivate) {
                        TonalChip(stringRes(Res.string.nip85_designation_private_entry), emphasized = true)
                    }
                    CappedChips(provider.services, MAX_SERVICE_CHIPS) { TonalChip(serviceLabel(it)) }
                }
            }
        }
    }
}

/** A designated service in words: "Rank", "Notes: Reposts", "Trusted people lists", or the raw row. */
@Composable
private fun serviceLabel(service: DesignationService): String {
    val qualifier = service.qualifier
    return when (service.kind) {
        30382 ->
            when (qualifier) {
                "rank" -> stringRes(Res.string.nip85_service_rank)
                "followers" -> stringRes(Res.string.nip85_service_followers)
                "hops" -> stringRes(Res.string.nip85_service_hops)
                "reporters" -> stringRes(Res.string.nip85_service_reporters)
                "muters" -> stringRes(Res.string.nip85_service_muters)
                null -> service.raw
                else -> metricWords(qualifier)
            }

        30383 -> qualifier?.let { stringRes(Res.string.nip85_service_note_metric, metricWords(it)) } ?: service.raw
        30384 -> qualifier?.let { stringRes(Res.string.nip85_service_address_metric, metricWords(it)) } ?: service.raw
        30385 -> qualifier?.let { stringRes(Res.string.nip85_service_identifier_metric, metricWords(it)) } ?: service.raw
        30392 -> if (qualifier == null) stringRes(Res.string.nip85_service_people_lists) else service.raw
        30393 -> if (qualifier == null) stringRes(Res.string.nip85_service_note_lists) else service.raw
        30394 -> if (qualifier == null) stringRes(Res.string.nip85_service_address_lists) else service.raw
        30395 -> if (qualifier == null) stringRes(Res.string.nip85_service_identifier_lists) else service.raw
        39998, 9998 -> if (qualifier == null || qualifier == "dlist-header") stringRes(Res.string.nip85_service_list_headers) else service.raw
        39999, 9999 -> if (qualifier == null) stringRes(Res.string.nip85_service_list_items) else service.raw
        else -> service.raw
    }
}
