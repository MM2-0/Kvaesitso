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
package com.vitorpamplona.amethyst.commons.relayClient.subscriptions

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavPerRelayFilterSet
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

/**
 * A [Filter] that also records **why** the app asked for it.
 *
 * The client tracks every in-flight REQ per relay already (`INostrClient.activeRequests`), but a
 * filter only says *what* is being matched — kinds, authors, tags. That is not enough to answer the
 * question a user (or a developer chasing relay churn) actually has: *which of my relays are doing
 * which job?* Carrying [purpose] on the filter itself means the answer travels with the data the
 * relay screens already read, with no parallel registry to keep in sync or to leak on teardown.
 *
 * ## It never reaches a relay
 *
 * `FilterSerializer` is registered against [Filter] and writes an explicit protocol field list
 * (`kinds`/`ids`/`authors`/`#tags`/`&tagsAll`/`since`/`until`/`limit`/`search`). Jackson applies a
 * serializer registered for a class to its subclasses, so an [ExplainedFilter] serializes to
 * byte-identical JSON — the purpose is dropped at the wire boundary by construction, not by an
 * annotation someone can forget. `ExplainedFilterTest` pins that.
 *
 * This matters beyond tidiness: telling relays what each REQ is *for* would hand them a
 * ready-made fingerprint of the client's intent, and correlate subscriptions that are deliberately
 * kept separate.
 *
 * ## Copying
 *
 * [copy] is overridden because filters are copied on the live path — assemblers call
 * `copy(since = …)` after every EOSE to advance the window. Inheriting the base implementation
 * would downgrade the filter to a plain [Filter] on the first refresh, so the purpose would survive
 * the opening REQ and then quietly disappear a few seconds later.
 */
class ExplainedFilter(
    ids: List<HexKey>? = null,
    authors: List<HexKey>? = null,
    kinds: List<Kind>? = null,
    tags: Map<String, List<String>>? = null,
    tagsAll: Map<String, List<String>>? = null,
    since: Long? = null,
    until: Long? = null,
    limit: Int? = null,
    search: String? = null,
    val purpose: SubPurpose,
    /** Free-form extra context for [SubPurpose.OTHER] or for narrowing a bucket while debugging. */
    val purposeDetail: String? = null,
    /**
     * The things this filter serves — community, group, channel or mint ids.
     *
     * A **list**, because filters are routinely batched: relay-group state is fetched with one `#d`
     * filter per host relay carrying every joined group on it, so a single filter can legitimately
     * serve a dozen chats. Modelling one id would have forced either a wrong answer ("All") or a
     * filter-per-chat, which is far more REQs than the relays want.
     *
     * Ids, not names: names change, are often not loaded when the filter is built, and would pin a
     * stale copy into a long-lived subscription. The UI resolves them against `LocalCache` at render
     * time, so it shows the current name and degrades to a short id when unknown.
     */
    val entityIds: List<HexKey>? = null,
    /**
     * Which logged-in accounts asked for this. Several accounts are commonly active at once and they
     * do not share relay sets, so "why is this relay connected" is only answerable per account —
     * without it, one account's communities look like another's.
     *
     * A **list**, because a filter can legitimately serve several accounts at once. Notifications are
     * `#p`-scoped, so every account reading the same relay can be asked for in one filter naming all
     * of them — which is what keeps a relay under its `max_subscriptions` cap when the app holds four
     * accounts open. Filters that serve one account carry a single-element list.
     *
     * Pubkeys rather than account references: filters outlive the objects that created them and are
     * held by the relay pool for the session, so holding an `Account` here would keep a logged-out
     * account alive.
     */
    val accountPubKeys: List<HexKey>? = null,
    /**
     * The top-nav selection that produced this filter — Global, the user's follows, a hashtag, a
     * geohash, a community.
     *
     * [entityIds] answers "which known thing does this serve"; discovery filters have no such thing,
     * because they go looking for chats/articles/streams rather than serving ones already named. That
     * is not the same as having no explanation: the feed selection behind them was always known where
     * the filter was built, it simply had nowhere to travel, so the screen could only render those
     * rows as "no entity".
     *
     * The per-relay value, not the whole set: this filter is already scoped to one relay, so it
     * carries only the slice that applies to it and holds no reference to the other relays' authors.
     *
     * A typed value rather than a formatted string, because [purposeDetail] taught the lesson —
     * text built in `commons` can never be translated. The UI matches on the type and picks its own
     * localized wording.
     */
    val scope: IFeedTopNavPerRelayFilter? = null,
) : Filter(ids, authors, kinds, tags, tagsAll, since, until, limit, search) {
    override fun copy(
        ids: List<String>?,
        authors: List<String>?,
        kinds: List<Int>?,
        tags: Map<String, List<String>>?,
        tagsAll: Map<String, List<String>>?,
        since: Long?,
        until: Long?,
        limit: Int?,
        search: String?,
    ) = ExplainedFilter(ids, authors, kinds, tags, tagsAll, since, until, limit, search, purpose, purposeDetail, entityIds, accountPubKeys, scope)

    companion object {
        /** Tags [filter] with a [purpose], preserving every protocol field. */
        fun of(
            filter: Filter,
            purpose: SubPurpose,
            detail: String? = null,
            entityIds: List<HexKey>? = null,
            accountPubKeys: List<HexKey>? = null,
            scope: IFeedTopNavPerRelayFilter? = null,
        ) = ExplainedFilter(
            filter.ids,
            filter.authors,
            filter.kinds,
            filter.tags,
            filter.tagsAll,
            filter.since,
            filter.until,
            filter.limit,
            filter.search,
            purpose,
            detail,
            entityIds,
            accountPubKeys,
            scope,
        )
    }
}

/** The purpose behind this filter, or null when it was never tagged. */
fun Filter.purposeOrNull(): SubPurpose? = (this as? ExplainedFilter)?.purpose

/** The purposes behind a relay's in-flight filters, deduplicated — what this relay is doing for us. */
fun Collection<Filter>.purposes(): Set<SubPurpose> = mapNotNullTo(mutableSetOf()) { it.purposeOrNull() }

/**
 * One row per (purpose, entity, account) a relay is serving, deduplicated.
 *
 * The entity/account pair is what makes a chip answerable — "Communities" alone cannot tell you
 * *which* community made you connect here, and with several accounts logged in it cannot tell you
 * whose. Entries with no entity collapse to a single row for that purpose.
 */
fun Collection<Filter>.purposeEntities(): Set<PurposeEntity> =
    flatMapTo(mutableSetOf()) { filter ->
        val explained = filter as? ExplainedFilter ?: return@flatMapTo emptyList()
        val ids = explained.entityIds
        if (ids.isNullOrEmpty()) {
            listOf(PurposeEntity(explained.purpose, null, explained.accountPubKeys?.firstOrNull(), explained.purposeDetail))
        } else {
            ids.map { PurposeEntity(explained.purpose, it, explained.accountPubKeys?.firstOrNull(), explained.purposeDetail) }
        }
    }

/** A single "this relay is doing X, for Y, on behalf of account Z" fact. Ids only; names resolve in the UI. */
data class PurposeEntity(
    val purpose: SubPurpose,
    val entityId: HexKey? = null,
    val accountPubKey: HexKey? = null,
    val detail: String? = null,
)

/**
 * Stamps [accountPubKey] onto every tagged filter that does not already name one.
 *
 * Applied once where a subscription manager knows its account, rather than threading a pubkey
 * parameter through the ~200 filter builders below it. Filters that already name an account are left
 * alone, so a builder with better knowledge always wins.
 */
fun List<RelayBasedFilter>.attributedTo(accountPubKey: HexKey): List<RelayBasedFilter> =
    map { relayFilter ->
        val filter = relayFilter.filter
        if (filter is ExplainedFilter && filter.accountPubKeys.isNullOrEmpty()) {
            RelayBasedFilter(
                relay = relayFilter.relay,
                filter = ExplainedFilter.of(filter, filter.purpose, filter.purposeDetail, filter.entityIds, listOf(accountPubKey), filter.scope),
            )
        } else {
            relayFilter
        }
    }

/**
 * Stamps the top-nav selection onto every tagged filter that does not already name one.
 *
 * Applied once at each feed's `make…Filter` dispatch — the single place that still knows which
 * selection is being served, and the same place that already chooses a builder from it. Below that
 * point the builders have flattened it into `authors`/`#t`/`#g` and the selection is unrecoverable.
 *
 * Each filter gets the slice for its own relay, so a filter aimed at a relay the selection says
 * nothing about is simply left alone rather than labelled with someone else's scope.
 */
fun List<RelayBasedFilter>.scopedTo(feedSettings: IFeedTopNavPerRelayFilterSet): List<RelayBasedFilter> =
    map { relayFilter ->
        val filter = relayFilter.filter
        val scope = if (filter is ExplainedFilter && filter.scope == null) feedSettings.scopeFor(relayFilter.relay) else null
        if (filter is ExplainedFilter && scope != null) {
            RelayBasedFilter(
                relay = relayFilter.relay,
                filter = ExplainedFilter.of(filter, filter.purpose, filter.purposeDetail, filter.entityIds, filter.accountPubKeys, scope),
            )
        } else {
            relayFilter
        }
    }
