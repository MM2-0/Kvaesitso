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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nip85_about
import com.vitorpamplona.amethyst.commons.resources.nip85_contact_card
import com.vitorpamplona.amethyst.commons.resources.nip85_contact_card_private
import com.vitorpamplona.amethyst.commons.resources.nip85_hops_away
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_comments
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_expires
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_followers
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_muters
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_posts
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_quotes
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_reactions
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_replies
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_reporters
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_reports_received
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_reports_sent
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_reposts
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_sats_received
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_sats_received_per_day
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_sats_sent
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_sats_sent_per_day
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_sats_zapped
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_zaps
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_zaps_received
import com.vitorpamplona.amethyst.commons.resources.nip85_metric_zaps_sent
import com.vitorpamplona.amethyst.commons.resources.nip85_no_follow_path
import com.vitorpamplona.amethyst.commons.resources.nip85_rank
import com.vitorpamplona.amethyst.commons.resources.nip85_rank_of_100
import com.vitorpamplona.amethyst.commons.resources.nip85_score_explainer
import com.vitorpamplona.amethyst.commons.resources.nip85_score_for_address
import com.vitorpamplona.amethyst.commons.resources.nip85_score_for_event
import com.vitorpamplona.amethyst.commons.resources.nip85_score_for_identifier
import com.vitorpamplona.amethyst.commons.resources.nip85_score_for_named
import com.vitorpamplona.amethyst.commons.resources.nip85_score_for_relay
import com.vitorpamplona.amethyst.commons.resources.nip85_scored
import com.vitorpamplona.amethyst.commons.resources.nip85_scored_by
import com.vitorpamplona.amethyst.commons.resources.nip85_trust_score
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateSkeletonFormatter
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.UserCompose
import com.vitorpamplona.amethyst.commons.ui.note.types.ReplyRenderType
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip85TrustedAssertions.AssertionMetric
import com.vitorpamplona.quartz.nip85TrustedAssertions.AssertionTimestamp
import com.vitorpamplona.quartz.nip85TrustedAssertions.addressables.AddressableAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.assertionMetrics
import com.vitorpamplona.quartz.nip85TrustedAssertions.assertionTimestamps
import com.vitorpamplona.quartz.nip85TrustedAssertions.events.EventAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.externalIds.ExternalIdAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.UserAssertionEvent
import com.vitorpamplona.quartz.utils.Hex
import org.jetbrains.compose.resources.StringResource

/** Metric tiles an assertion card shows before a "+N more" chip; providers publish about a dozen. */
private const val MAX_METRIC_TILES = 12

/** A moment's tile: the short date, in the locale's order ("Oct 14, 2026" / "14 Oct 2026"). */
private val momentDateFormat = DateSkeletonFormatter("yMMMd")

/** Counts that are a negative signal: tinted when they are not zero. */
private val NEGATIVE_METRICS = setOf("reporters", "muters", "reports_cnt_recd", "verifiedReporterCount", "verifiedMuterCount")

private val METRIC_LABELS: Map<String, StringResource> =
    mapOf(
        "followers" to Res.string.nip85_metric_followers,
        "reporters" to Res.string.nip85_metric_reporters,
        "muters" to Res.string.nip85_metric_muters,
        "post_cnt" to Res.string.nip85_metric_posts,
        "reply_cnt" to Res.string.nip85_metric_replies,
        "reactions_cnt" to Res.string.nip85_metric_reactions,
        "reaction_cnt" to Res.string.nip85_metric_reactions,
        "comment_cnt" to Res.string.nip85_metric_comments,
        "quote_cnt" to Res.string.nip85_metric_quotes,
        "repost_cnt" to Res.string.nip85_metric_reposts,
        "zap_cnt" to Res.string.nip85_metric_zaps,
        "zap_amount" to Res.string.nip85_metric_sats_zapped,
        "zap_cnt_recd" to Res.string.nip85_metric_zaps_received,
        "zap_cnt_sent" to Res.string.nip85_metric_zaps_sent,
        "zap_amt_recd" to Res.string.nip85_metric_sats_received,
        "zap_amt_sent" to Res.string.nip85_metric_sats_sent,
        "zap_avg_amt_day_recd" to Res.string.nip85_metric_sats_received_per_day,
        "zap_avg_amt_day_sent" to Res.string.nip85_metric_sats_sent_per_day,
        "reports_cnt_recd" to Res.string.nip85_metric_reports_received,
        "reports_cnt_sent" to Res.string.nip85_metric_reports_sent,
    )

@Composable
private fun metricLabel(name: String): String = METRIC_LABELS[name]?.let { stringRes(it) } ?: metricWords(name)

/**
 * A NIP-85 trusted assertion (kinds 30382-30385): whom or what it scores, its rank, and the
 * counts beside it. The rank is one observer's, computed by the provider that signed it, which
 * the card says, because ranks from two observers do not compare and a score is not an
 * endorsement. Mirrors Brainstorm's `AssertionHero`.
 */
@Composable
fun RenderTrustedAssertion(
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
    val event = noteState.note.event ?: return

    RenderTrustedAssertion(event, quotesLeft, backgroundColor, accountViewModel, nav)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderTrustedAssertion(
    event: Event,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val metrics = remember(event) { event.tags.assertionMetrics() }
    val rank = remember(metrics) { metrics.firstOrNull { it.name == "rank" } }
    val hops = remember(metrics) { metrics.firstOrNull { it.name == "hops" } }
    val rest = remember(metrics) { metrics.filter { it.name != "rank" && it.name != "hops" } }
    // A provider's timestamps (`mint_audit_at`, `expiration`): dates after the counts, never numbers.
    val moments = remember(event) { event.tags.assertionTimestamps() }
    val tiles = remember(rest, moments) { rest.map { MetricTileItem.Count(it) } + moments.map { MetricTileItem.Moment(it) } }

    val isPerson = event is UserAssertionEvent
    // The account's own contact cards share kind 30382 with provider scores; they carry their
    // nickname in encrypted content and no public metrics, so they are not a "trust score".
    val isContactCard = isPerson && metrics.isEmpty() && moments.isEmpty() && event.content.isNotEmpty()
    val identifier = remember(event) { (event as? ExternalIdAssertionEvent)?.aboutExternalId()?.takeIf { it.isNotBlank() } }

    TrustCardFrame {
        TrustCardHeader(
            symbol = if (isContactCard) MaterialSymbols.Person else MaterialSymbols.Shield,
            // The thing scored leads: an identifier is named in the title, and every subject's
            // card sits right under the title, above the numbers.
            title =
                when (event) {
                    is UserAssertionEvent -> stringRes(if (isContactCard) Res.string.nip85_contact_card else Res.string.nip85_trust_score)
                    is EventAssertionEvent -> stringRes(Res.string.nip85_score_for_event)
                    is AddressableAssertionEvent -> stringRes(Res.string.nip85_score_for_address)
                    else ->
                        identifier?.let {
                            stringRes(
                                if (isRelayIdentifier(it)) Res.string.nip85_score_for_relay else Res.string.nip85_score_for_named,
                                identifierTitleName(it),
                            )
                        } ?: stringRes(Res.string.nip85_score_for_identifier)
                },
            subtitle = stringRes(if (isContactCard) Res.string.nip85_contact_card_private else Res.string.nip85_score_explainer),
        )

        AssertionSubject(event, quotesLeft, backgroundColor, accountViewModel, nav)

        if (rank != null || (isPerson && !isContactCard) || tiles.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rank?.let {
                    val value = formatMetricValue(it)
                    StatTile(
                        value = if (isPerson && it.isWhole()) stringRes(Res.string.nip85_rank_of_100, value) else value,
                        label = stringRes(Res.string.nip85_rank),
                        emphasized = true,
                    )
                }
                // An absent `hops` is no known follow path, never 0, which is the observer's own record.
                if (isPerson && !isContactCard) {
                    if (hops != null) {
                        StatTile(formatMetricValue(hops), pluralStringRes(Res.plurals.nip85_hops_away, hops.value.toIntOrNull() ?: 0))
                    } else {
                        StatTile("—", stringRes(Res.string.nip85_no_follow_path))
                    }
                }
                CappedChips(tiles, MAX_METRIC_TILES) {
                    when (it) {
                        is MetricTileItem.Count -> MetricTile(it.metric)
                        is MetricTileItem.Moment -> MomentTile(it.moment)
                    }
                }
            }
        }

        LabeledInlineUser(stringRes(Res.string.nip85_scored_by), event.pubKey, accountViewModel, nav)
    }
}

@Composable
private fun MetricTile(metric: AssertionMetric) {
    StatTile(
        value = formatMetricValue(metric),
        label = metricLabel(metric.name),
        warning = metric.name in NEGATIVE_METRICS && metric.number > 0,
    )
}

/** A tile of the card's metric row: a count, or a moment drawn as a date. */
@Immutable
private sealed interface MetricTileItem {
    @Immutable
    class Count(
        val metric: AssertionMetric,
    ) : MetricTileItem

    @Immutable
    class Moment(
        val moment: AssertionTimestamp,
    ) : MetricTileItem
}

/** A moment as a short date ("Oct 14, 2026", in the reader's locale), labelled by its name; `expiration` is "Expires". */
@Composable
private fun MomentTile(moment: AssertionTimestamp) {
    StatTile(
        value = remember(moment.seconds) { momentDateFormat.format(moment.seconds * 1000) },
        label = if (moment.name == "expiration") stringRes(Res.string.nip85_metric_expires) else metricLabel(moment.name),
    )
}

@Composable
private fun AssertionSubject(
    event: Event,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (event) {
        is UserAssertionEvent -> {
            val subject = event.aboutUser()
            if (!Hex.isHex64(subject)) return
            TrustSectionLabel(stringRes(Res.string.nip85_about))
            LoadUser(subject) { user ->
                user?.let { UserCompose(it, accountViewModel = accountViewModel, nav = nav) }
            }
        }

        is EventAssertionEvent -> {
            val id = event.aboutEvent()
            if (!Hex.isHex64(id) || quotesLeft <= 0) return
            TrustSectionLabel(stringRes(Res.string.nip85_scored))
            LoadNote(id) { note ->
                note?.let { QuotedSubject(it, quotesLeft, backgroundColor, accountViewModel, nav) }
            }
        }

        is AddressableAssertionEvent -> {
            val address = Address.parse(event.aboutAddress()) ?: return
            if (quotesLeft <= 0) return
            TrustSectionLabel(stringRes(Res.string.nip85_scored))
            LoadAddressableNote(address) { note ->
                note?.let { QuotedSubject(it, quotesLeft, backgroundColor, accountViewModel, nav) }
            }
        }

        is ExternalIdAssertionEvent -> {
            val identifier = event.aboutExternalId().takeIf { it.isNotBlank() } ?: return
            TrustSectionLabel(stringRes(Res.string.nip85_scored))
            if (identifier.startsWith("https://") || identifier.startsWith("http://")) {
                ClickableUrl(url = identifier)
            } else {
                Text(
                    text = identifier,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun QuotedSubject(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    NoteCompose(
        baseNote = note,
        modifier = MaterialTheme.colorScheme.replyModifier,
        isQuotedNote = true,
        unPackReply = ReplyRenderType.NONE,
        makeItShort = true,
        quotesLeft = quotesLeft - 1,
        parentBackgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}
