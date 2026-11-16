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
package com.vitorpamplona.geode

import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy

/**
 * Index strategy for a *public relay*, as opposed to quartz's
 * [DefaultIndexingStrategy] which is tuned for client-side stores that
 * only ever query their own kinds.
 *
 * A relay cannot predict its clients' filters, and the cheapest query a
 * client can send — `{"limit": N}`, the firehose/landing REQ — carries no
 * kind, author or tag for the planner to use. Without an index on
 * `created_at` alone that REQ is a full-table scan + top-N sort on every
 * poll; relayBench measured it 4× slower than strfry at 10k events and
 * the gap grows linearly with the table. The extra index costs one B-tree
 * insert per event, which the same benchmark shows is noise next to the
 * Schnorr verify + tag indexing already paid on the write path.
 *
 * @param fullTextSearch maintain the NIP-50 FTS index. Off skips the
 *   per-event tokenization on ingest (relayBench measured it at roughly a
 *   quarter of write cost) at the price of `search` filters matching
 *   nothing — pair it with a NIP-11 doc that doesn't advertise 50.
 * @param liveNegentropyIndex keep the always-current `(created_at, id)`
 *   set that serves full-corpus NIP-77 NEG-OPENs without a scan + seal
 *   (strfry answers those off its live tree; the scan+seal path measured
 *   ~340 ms per cold open at 50k events). Costs ~140 B/event of JVM heap
 *   (hex-string ids) and one indexed pre-SELECT per replaceable insert.
 *   `[negentropy].live_index = false` turns it off.
 */
fun relayIndexingStrategy(
    fullTextSearch: Boolean = true,
    liveNegentropyIndex: Boolean = true,
) = DefaultIndexingStrategy(
    indexEventsByCreatedAtAlone = true,
    // Authors-only filters (no kinds) are relay-common — archives,
    // migration tools. strfry maintains the same (pubkey, created_at)
    // index unconditionally; without it the filter walks the whole
    // time index.
    indexEventsByPubkeyAlone = true,
    // The tag ∩ author ∩ kind shape (DM rooms, reports-by-follows,
    // follows-scoped community feeds — 65 client assembler call sites)
    // otherwise reads every row for the tag/kind before filtering the
    // author. TagAuthorIndexBenchmark @ 1M events: 14.2 ms -> 0.66 ms
    // (~21x, growing with corpus size) with insert cost inside run noise
    // (49.0 vs 47.4 µs/event). Existing DBs build the index on next open
    // via ensureOptionalIndexes.
    indexTagsWithKindAndPubkey = true,
    indexFullTextSearch = fullTextSearch,
    // Tokenize off the commit path; NostrServer drives the catch-up
    // worker and search queries drain it first, so NIP-50 stays
    // exactly as fresh while publishes stop paying for it.
    deferFullTextSearchIndexing = fullTextSearch,
    maintainLiveNegentropyIndex = liveNegentropyIndex,
)

/** Stock relay strategy — everything on, matching geode's defaults. */
val RelayIndexingStrategy = relayIndexingStrategy()
