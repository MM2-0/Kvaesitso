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
package com.vitorpamplona.amethyst.commons.search

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

/**
 * A search, end to end, in one place: what was typed becomes a query, the query becomes filters,
 * what comes back is narrowed, and what survives is ordered.
 *
 * ```
 *   text ──► parse ──► filters ──► (ask relays / ask the cache) ──► keep ──► rank
 * ```
 *
 * Every piece here already existed and was already tested. What did not exist was the *sequence*,
 * and that is the whole reason for this object: each front end assembled the steps by hand, and
 * each of them forgot a different one. Android never called [keep], so `-term` and the pseudo-kinds
 * silently did nothing; it never used [rank]'s scorer, so Relevance sorted by date; it dropped the
 * query's kinds on the way to [filters], so a `kind:` chip narrowed nothing. Desktop had all three
 * and lacked others. None of those were visible as a missing call, because there was nothing they
 * were missing *from*.
 *
 * So the steps are here, in order, and a caller holding results calls [narrowAndOrder]. Reaching
 * for an individual step is still allowed — a relay subscription needs [filters] and nothing else
 * — but a caller collecting results and skipping [keep] is now a visible mistake rather than an
 * absence.
 *
 * ## Generic over the item
 *
 * Android holds `Note` (a mutable box around an event, with a zap total attached); desktop holds a
 * raw `Event`. Neither type belongs in the other's world, so [keep] and [rank] take accessors
 * instead: what event does this item carry, and what is it worth. That is the entire difference
 * between the two front ends, and it is two lambdas wide.
 *
 * CLI-safe: no Compose, no coroutines, no state. Pure functions over data.
 */
object SearchPipeline {
    /** What the reader typed, as a query. */
    fun parse(text: String): SearchQuery = QueryParser.parse(text)

    /**
     * The REQs this query sends, or an empty list when it asks nothing a relay can answer.
     *
     * [kinds] is the caller’s kind window; when the query names its own, that wins — a `kind:`
     * chip that did not reach here is a chip claiming a filter the REQ never carried.
     */
    fun filters(
        query: SearchQuery,
        kinds: List<Int>? = null,
        limit: Int = 100,
    ): List<Filter> =
        SearchFilterBuilder.build(
            query,
            query.kinds.takeIf { it.isNotEmpty() }?.toList() ?: kinds,
            limit,
        )

    /**
     * The parts of the query no relay can be asked about, applied to what came back.
     *
     * NIP-50 has no negation operator, and "is a reply" is a shape of an event's tags rather than
     * anything a relay indexes, so `-term` and the `kind:reply`/`kind:media` pseudo-kinds only ever
     * narrow anything if the client applies them here.
     */
    fun <T> keep(
        items: List<T>,
        query: SearchQuery,
        event: (T) -> Event?,
    ): List<T> = items.filter { item -> event(item)?.let { SearchResultFilter.matches(it, query) } != false }

    /**
     * The order results are shown in.
     *
     * Relevance scores the query's *leftover* terms rather than the whole box: `from:npub1…` and
     * `kind:article` are filters, and looking for their literal text inside an event's content
     * ranks on noise. A query that is all chips has nothing to be more or less relevant to, and
     * falls back to newest.
     *
     * [zapTotal] answers POPULAR, as a plain [Double] rather than the `BigDecimal` a `Note`
     * carries: that type is `expect`-declared in quartz with no `Comparable`, and its JVM actual
     * is `java.math.BigDecimal`, so a comparator over it cannot be written in common code. A
     * double holds every sat total exactly up to 2^53, which is past any zap that will exist.
     *
     * A front end holding raw events has no zap totals and leaves it at zero, which collapses
     * POPULAR into newest for that caller — the only honest answer a raw `Event` can give.
     *
     * Sort keys are snapshotted per item before comparing. A `Note` is a mutable box: a newer
     * addressable event arriving from a relay mid-sort changes `createdAt` under the comparator,
     * and TimSort answers that with "Comparison method violates its general contract!".
     */
    fun <T> rank(
        items: List<T>,
        order: SearchSortOrder,
        terms: String,
        event: (T) -> Event?,
        zapTotal: (T) -> Double = { 0.0 },
    ): List<T> {
        if (items.isEmpty()) return items
        val keyed = items.map { Sortable(it, event(it), zapTotal(it), terms, order) }
        val sorted =
            when (order) {
                SearchSortOrder.OLDEST -> keyed.sortedWith(compareBy<Sortable<T>> { it.createdAt }.thenBy { it.id })
                SearchSortOrder.POPULAR -> keyed.sortedWith(compareByDescending<Sortable<T>> { it.zaps }.thenByDescending { it.createdAt }.thenBy { it.id })
                SearchSortOrder.RELEVANCE ->
                    if (terms.isBlank()) {
                        keyed.sortedWith(compareByDescending<Sortable<T>> { it.createdAt }.thenBy { it.id })
                    } else {
                        keyed.sortedWith(compareByDescending<Sortable<T>> { it.score }.thenByDescending { it.createdAt }.thenBy { it.id })
                    }
                // NAME_AZ/NAME_ZA order people, not events; they leave a result list alone.
                SearchSortOrder.NAME_AZ, SearchSortOrder.NAME_ZA -> keyed
                SearchSortOrder.NEWEST -> keyed.sortedWith(compareByDescending<Sortable<T>> { it.createdAt }.thenBy { it.id })
            }
        return sorted.map { it.item }
    }

    /**
     * [keep] then [rank], which is the pair a caller holding results always wants and the pair
     * that was forgotten one half at a time.
     */
    fun <T> narrowAndOrder(
        items: List<T>,
        query: SearchQuery,
        order: SearchSortOrder,
        event: (T) -> Event?,
        zapTotal: (T) -> Double = { 0.0 },
    ): List<T> = rank(keep(items, query, event), order, query.text, event, zapTotal)

    /** One item with its sort keys read once. See [rank] on why they are snapshotted. */
    private class Sortable<T>(
        val item: T,
        event: Event?,
        val zaps: Double,
        terms: String,
        order: SearchSortOrder,
    ) {
        val createdAt = event?.createdAt ?: 0L
        val id = event?.id ?: ""
        val score = if (order == SearchSortOrder.RELEVANCE && event != null && terms.isNotBlank()) SearchResultSorter.scoreEvent(event, terms) else 0.0
    }
}
