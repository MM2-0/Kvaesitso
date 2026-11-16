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
 * The search engines the browser omnibox can send a query to, chosen in Settings → Search engine and
 * stored as [SearchEngine.id] in the app-wide UI settings. [OmniboxInput.resolve] takes the chosen
 * engine's [SearchEngine.queryPrefix]; the query is URL-encoded onto it.
 *
 * Names are brand names, so they are not translated.
 */
object SearchEngines {
    data class SearchEngine(
        /** Stable key persisted in settings and passed to the browser process. Never rename one. */
        val id: String,
        val name: String,
        val queryPrefix: String,
    ) {
        /** The engine's host, shown under its name (e.g. `duckduckgo.com`). */
        val host: String get() = OmniboxInput.hostOf(queryPrefix)?.removePrefix("www.") ?: queryPrefix
    }

    val DUCKDUCKGO = SearchEngine("duckduckgo", "DuckDuckGo", OmniboxInput.DEFAULT_SEARCH_PREFIX)

    /** Privacy-respecting engines first, then the mainstream ones. */
    val list: List<SearchEngine> =
        listOf(
            DUCKDUCKGO,
            SearchEngine("brave", "Brave Search", "https://search.brave.com/search?q="),
            SearchEngine("startpage", "Startpage", "https://www.startpage.com/sp/search?query="),
            SearchEngine("mojeek", "Mojeek", "https://www.mojeek.com/search?q="),
            SearchEngine("qwant", "Qwant", "https://www.qwant.com/?q="),
            SearchEngine("ecosia", "Ecosia", "https://www.ecosia.org/search?q="),
            SearchEngine("kagi", "Kagi", "https://kagi.com/search?q="),
            SearchEngine("google", "Google", "https://www.google.com/search?q="),
            SearchEngine("bing", "Bing", "https://www.bing.com/search?q="),
        )

    val DEFAULT: SearchEngine = DUCKDUCKGO

    /** The engine stored under [id], or [DEFAULT] when it is null or no longer offered. */
    fun byId(id: String?): SearchEngine = list.firstOrNull { it.id == id } ?: DEFAULT
}
