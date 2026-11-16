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
package com.vitorpamplona.amethyst.commons.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.search.UserSearchEngine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** How many rows a picker offers. More than this and the reader is scrolling, not choosing. */
const val SEARCH_PICKER_LIMIT = 8

/**
 * The people a `from:`/`to:` picker offers, from the search engine the caller already has.
 *
 * Cache hits come first and relay answers follow, because the reader almost always means someone
 * they already follow, and a relay that is slow to answer must not reorder rows under a finger
 * that is already moving toward one.
 */
@Composable
fun rememberPersonCandidates(engine: UserSearchEngine): ImmutableList<PersonCandidate> {
    val local by engine.localResults.collectAsStateWithLifecycle()
    val relay by engine.relayResults.collectAsStateWithLifecycle()
    return remember(local, relay) {
        (local + relay)
            .distinctBy { it.pubkeyHex }
            .take(SEARCH_PICKER_LIMIT)
            .map { it.toCandidate() }
            .toImmutableList()
    }
}

/**
 * The name a key chip draws, for whoever is already in the cache. Null for a profile that has
 * not arrived, which leaves the chip showing a short npub rather than an invented name.
 */
@Composable
fun rememberChipNames(engine: UserSearchEngine): (String) -> String? {
    val local by engine.localResults.collectAsStateWithLifecycle()
    val relay by engine.relayResults.collectAsStateWithLifecycle()
    val names = remember(local, relay) { (local + relay).associate { it.pubkeyHex to it.toBestDisplayName() } }
    return { names[it] }
}

private fun User.toCandidate() =
    PersonCandidate(
        pubkeyHex = pubkeyHex,
        name = toBestDisplayName(),
        subtitle =
            metadataOrNull()
                ?.flow
                ?.value
                ?.info
                ?.nip05,
        pictureUrl = profilePicture(),
    )
