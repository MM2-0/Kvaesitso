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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.trusted_list_built_from_tag
import com.vitorpamplona.amethyst.commons.resources.trusted_list_default_title
import com.vitorpamplona.amethyst.commons.resources.trusted_list_member_count
import com.vitorpamplona.amethyst.commons.resources.trusted_list_param_cutoff
import com.vitorpamplona.amethyst.commons.resources.trusted_list_param_min_rank
import com.vitorpamplona.amethyst.commons.resources.trusted_list_param_rigor
import com.vitorpamplona.amethyst.commons.resources.trusted_list_ranked_through
import com.vitorpamplona.amethyst.commons.resources.trusted_list_retracted
import com.vitorpamplona.amethyst.commons.resources.trusted_list_score_of_100
import com.vitorpamplona.amethyst.commons.resources.trusted_list_tag_by
import com.vitorpamplona.amethyst.commons.resources.trusted_list_truncated
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.AddressMemberRow
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.BookmarkMemberRow
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.ListCard
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.trustedLists.TrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.addressables.tags.AddressMemberTag
import com.vitorpamplona.quartz.experimental.trustedLists.events.tags.EventMemberTag
import com.vitorpamplona.quartz.experimental.trustedLists.externalIds.tags.ExternalIdMemberTag
import com.vitorpamplona.quartz.experimental.trustedLists.tags.TrustedListMemberTag
import com.vitorpamplona.quartz.experimental.trustedLists.users.tags.PubKeyMemberTag
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import com.vitorpamplona.quartz.utils.Hex

/**
 * A Trusted List (kinds 30392-30395): a set of members computed under one point of view, each
 * with a 0-100 score. The card names the list, says where it came from (the tag it was built
 * from and whose web of trust ranked it), shows the parameters it was built with, and lists the
 * members best first. Mirrors Brainstorm's `TrustedListHero`, in the shared NIP-51 [ListCard]
 * frame so it reads like Amethyst's other lists.
 */
@Composable
fun RenderTrustedList(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by baseNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val event = noteState.note.event as? TrustedListEvent ?: return

    // Published best first; sorted anyway (stable, so ties keep their order). Unscored last.
    val members = remember(event) { event.members().sortedByDescending { it.score ?: -1 } }

    ListCard(
        title = event.title()?.takeIf { it.isNotBlank() } ?: stringRes(Res.string.trusted_list_default_title),
        description = event.description(),
        items = members,
        hasUnreadablePrivateItems = false,
        backgroundColor = backgroundColor,
        subtitle = { TrustedListSubtitle(event, members.size, accountViewModel, nav) },
    ) { member ->
        TrustedListMemberRow(member, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrustedListSubtitle(
    event: TrustedListEvent,
    memberCount: Int,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val source = remember(event) { event.sourceTag() }
    val observer = remember(event) { event.observer() }
    val truncatedTotal = remember(event) { event.truncatedTotal() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text =
                if (truncatedTotal != null && truncatedTotal > memberCount) {
                    stringRes(Res.string.trusted_list_truncated, memberCount, truncatedTotal)
                } else {
                    pluralStringRes(Res.plurals.trusted_list_member_count, memberCount, memberCount)
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // Where the list comes from: the tag, and whose web of trust ranked it.
        if (source?.slug != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringRes(Res.string.trusted_list_built_from_tag),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = source.slug ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                source.author?.let { LabeledInlineUser(stringRes(Res.string.trusted_list_tag_by), it, accountViewModel, nav) }
            }
        }
        observer?.let { LabeledInlineUser(stringRes(Res.string.trusted_list_ranked_through), it, accountViewModel, nav) }

        // The build, as published: a reader comparing two copies of a list wants to see why they differ.
        val metric = remember(event) { event.metric() }
        val minRank = remember(event) { event.minRank() }
        val cutoff = remember(event) { event.cutoff() }
        val rigor = remember(event) { event.rigor() }
        val retracted = remember(event) { event.isRetracted() }
        if (metric != null || minRank != null || cutoff != null || rigor != null || retracted) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (retracted) TonalChip(stringRes(Res.string.trusted_list_retracted), emphasized = true)
                metric?.let { TonalChip(metricWords(it)) }
                minRank?.let { TonalChip(stringRes(Res.string.trusted_list_param_min_rank, it.toString())) }
                cutoff?.let { TonalChip(stringRes(Res.string.trusted_list_param_cutoff, it.toString())) }
                rigor?.let { TonalChip(stringRes(Res.string.trusted_list_param_rigor, it)) }
            }
        }
    }
}

@Composable
private fun TrustedListMemberRow(
    member: TrustedListMemberTag,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (member) {
        is PubKeyMemberTag -> ScoredUserRow(member, accountViewModel, nav)

        is EventMemberTag -> {
            if (!Hex.isHex64(member.eventId)) return
            ScoredQuote(member.score) {
                BookmarkMemberRow(EventBookmark(member.eventId), quotesLeft, backgroundColor, accountViewModel, nav)
            }
        }

        is AddressMemberTag -> {
            val address = remember(member) { Address.parse(member.address) } ?: return
            ScoredQuote(member.score) {
                AddressMemberRow(address, quotesLeft, backgroundColor, accountViewModel, nav)
            }
        }

        is ExternalIdMemberTag -> ScoredIdentifierRow(member)
    }
}

/** A person on the list: avatar, name, and their score on the list's metric at the end. */
@Composable
private fun ScoredUserRow(
    member: PubKeyMemberTag,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (!Hex.isHex64(member.pubKey)) return
    LoadUser(member.pubKey) { user ->
        if (user == null) return@LoadUser
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { nav.nav(routeFor(user)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                UserPicture(user, Size35dp, accountViewModel = accountViewModel, nav = nav)
                UsernameDisplay(user, Modifier.weight(1f), accountViewModel = accountViewModel)
                member.score?.let { ScoreBadge(it) }
            }
            HorizontalDivider(thickness = DividerThickness)
        }
    }
}

/** A quoted note or address on the list, with its score above it. */
@Composable
private fun ScoredQuote(
    score: Int?,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        score?.let {
            Text(
                text = stringRes(Res.string.trusted_list_score_of_100, it),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        content()
    }
}

/** An external identifier on the list (NIP-73): the id, linked when it is a web address. */
@Composable
private fun ScoredIdentifierRow(member: ExternalIdMemberTag) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            val id = member.externalId
            if (id.startsWith("https://") || id.startsWith("http://")) {
                ClickableUrl(url = id, modifier = Modifier.weight(1f))
            } else {
                Text(
                    text = id,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            member.score?.let { ScoreBadge(it) }
        }
        HorizontalDivider(thickness = DividerThickness)
    }
}

@Composable
private fun ScoreBadge(score: Int) {
    TonalChip(text = score.toString(), emphasized = true)
}
