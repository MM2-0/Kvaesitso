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
package com.vitorpamplona.amethyst.commons.browser

/**
 * Ranks omnibox autocomplete suggestions out of the user's favorites and visit history, and computes the
 * inline ghost-text completion the address bar shows. Pure and platform-agnostic (the caller maps its
 * favorites/history into [Candidate]s), so it lives in commons and is unit-tested directly.
 *
 * Ranking, roughly: a host-prefix match beats a path-prefix match beats a substring match; favorites are
 * boosted over plain history; ties break on visit frequency then recency. The result is host-deduplicated
 * (one row per site) so a frequently-visited site doesn't flood the list.
 */
object OmniboxSuggestions {
    /** A neutral candidate the caller builds from a favorite or a history entry. */
    data class Candidate(
        val url: String,
        val label: String,
        val isFavorite: Boolean,
        val visitCount: Int = 0,
        val lastVisitedAt: Long = 0L,
    )

    data class Suggestion(
        val url: String,
        val label: String,
        val host: String,
        val isFavorite: Boolean,
    )

    fun rank(
        typedRaw: String,
        candidates: List<Candidate>,
        limit: Int = 8,
    ): List<Suggestion> {
        val typed = typedRaw.trim().lowercase()
        if (typed.isEmpty()) return emptyList()
        return candidates
            .mapNotNull { c ->
                val host = OmniboxInput.hostOf(c.url) ?: c.url
                val score = score(typed, c, host) ?: return@mapNotNull null
                Scored(Suggestion(c.url, c.label, host, c.isFavorite), score, c.lastVisitedAt)
            }.sortedWith(compareByDescending<Scored> { it.score }.thenByDescending { it.lastVisitedAt })
            .distinctBy { it.suggestion.host.lowercase() }
            .take(limit)
            .map { it.suggestion }
    }

    /**
     * The host to inline-complete [typedRaw] to (the suffix the address bar pre-selects as ghost text), or
     * null when there's nothing to offer. Only completes a bare host fragment (no scheme, no path, no
     * spaces) against a ranked host that starts with it — so typing `git` offers `github.com`.
     */
    fun completion(
        typedRaw: String,
        ranked: List<Suggestion>,
    ): String? {
        val typed = typedRaw.trim()
        if (typed.isEmpty() || typed.any { it.isWhitespace() }) return null
        if (typed.contains("://") || typed.contains('/')) return null
        val lower = typed.lowercase()
        return ranked
            .firstOrNull { it.host.length > typed.length && it.host.lowercase().startsWith(lower) }
            ?.host
    }

    private fun score(
        typed: String,
        c: Candidate,
        host: String,
    ): Double? {
        val h = host.lowercase()
        val u = c.url.lowercase()
        val urlNoScheme = u.substringAfter("://", u)
        val l = c.label.lowercase()
        var base =
            when {
                h.startsWith(typed) -> 1000.0
                urlNoScheme.startsWith(typed) -> 800.0
                h.contains(typed) -> 400.0
                l.contains(typed) -> 300.0
                u.contains(typed) -> 200.0
                else -> return null
            }
        if (c.isFavorite) base += 500.0
        base += minOf(c.visitCount, 50) * 2.0
        return base
    }

    private data class Scored(
        val suggestion: Suggestion,
        val score: Double,
        val lastVisitedAt: Long,
    )
}
