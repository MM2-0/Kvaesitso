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

/**
 * The NIP-73 external ids and NIP-01 tag values a token is actually asked with.
 *
 * A relay indexes tag values byte-for-byte, so "which spellings of this thing are worth asking
 * for" is a real question with a per-scheme answer: an ISBN is written with and without hyphens,
 * a DOI is case-insensitive but stored as typed, an ISAN has a 5-segment root, and a bare
 * hostname is two schemes and two trailing-slash forms. Each list is canonical-first and deduped.
 */
object ScopeIds {
    /** The NIP-73 scope prefixes, longest `podcast:` form first so a shorter one cannot half-match. */
    val PREFIXES =
        listOf(
            "podcast:item:guid",
            "podcast:publisher",
            "podcast:guid",
            "site",
            "isbn",
            "geo",
            "isan",
            "doi",
        )

    private val SCHEME = Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE)
    private val SCHEME_HOST_PATH = Regex("^([a-z][a-z0-9+.-]*://)([^/]*)(.*)$", RegexOption.IGNORE_CASE)

    /**
     * Every spelling of `tag` worth asking a `#t`/`#l` filter for, best first: as typed,
     * lowercase, Capitalized, UPPERCASE, deduped. Relays match tag values cased, and clients do
     * not agree on which case they write a hashtag in.
     */
    fun tagValues(tag: String?): List<String> {
        val t = tag ?: return emptyList()
        if (t.isEmpty()) return emptyList()
        val lower = t.lowercase()
        return listOf(
            t,
            lower,
            lower.replaceFirstChar { it.uppercase() },
            t.uppercase(),
        ).distinct()
    }

    /**
     * The NIP-73 web ids a `site:` value may be written as, canonical first: the fragment is
     * dropped, both schemes are asked when none was typed, and each is asked with and without
     * its trailing slash. Scheme and host lowercase; the path keeps its case, because it is
     * case-sensitive on most servers.
     */
    fun siteIds(value: String): List<String> {
        val bare = value.substringBefore('#')
        if (bare.isEmpty()) return emptyList()
        val typed = if (SCHEME.containsMatchIn(bare)) listOf(bare) else listOf("https://$bare", "http://$bare")
        val cased =
            typed.flatMap { url ->
                val m = SCHEME_HOST_PATH.find(url)
                if (m == null) {
                    listOf(url)
                } else {
                    val (scheme, host, path) = m.destructured
                    listOf(scheme.lowercase() + host.lowercase() + path, url)
                }
            }
        return cased
            .flatMap { listOf(it, if (it.endsWith("/")) it.dropLast(1) else "$it/") }
            .distinct()
    }

    /**
     * Every spelling of one scope worth a tag filter's while, canonical first and as typed beside
     * it. `isbn:` drops hyphens, `geo:` and `doi:` are lowercased, `isan:` is uppercased and also
     * asked as its 5-segment root, and a `podcast:publisher:` value takes a `guid:` segment.
     *
     * An empty list means the token asks nothing — which is what makes it not a token at all.
     */
    fun scopeIds(
        field: String,
        value: String?,
    ): List<String> {
        val v = value ?: return emptyList()
        if (v.isEmpty()) return emptyList()
        return when (field) {
            "site" -> siteIds(v)
            "isbn" -> listOf("isbn:${v.replace("-", "")}", "isbn:$v").distinct()
            "geo" -> listOf("geo:${v.lowercase()}", "geo:$v").distinct()
            "doi" -> listOf("doi:${v.lowercase()}", "doi:$v").distinct()
            "isan" -> {
                val parts = v.split("-")
                val root = if (parts.size == 8) parts.take(5).joinToString("-") else v
                listOf("isan:${root.uppercase()}", "isan:$root", "isan:${v.uppercase()}", "isan:$v").distinct()
            }

            "podcast:publisher" ->
                if (!v.startsWith("guid:", ignoreCase = true)) {
                    listOf(
                        "podcast:publisher:guid:$v",
                        "podcast:publisher:guid:${v.lowercase()}",
                        "podcast:publisher:$v",
                        "podcast:publisher:${v.lowercase()}",
                    ).distinct()
                } else {
                    listOf("podcast:publisher:$v", "podcast:publisher:${v.lowercase()}").distinct()
                }

            else -> listOf("$field:$v", "$field:${v.lowercase()}").distinct()
        }
    }

    /**
     * The NIP-73 external ids a hashtag is written as, for a NIP-22 comment's `i`/`I`: `#topic`
     * per the spec, plus the unprefixed form some clients reused.
     */
    fun hashtagIds(tags: List<String>): List<String> = tags.flatMap { listOf("#$it", it) }.distinct()
}
