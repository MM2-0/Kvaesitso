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
package com.vitorpamplona.amethyst.commons.ui.settings

import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/** Leading-icon representation mirroring the two [SettingsItem] overloads. */
sealed interface SettingsIcon {
    data class Symbol(
        val symbol: MaterialSymbol,
    ) : SettingsIcon

    data class Painter(
        val iconPainter: DrawableResource,
        val iconPainterRef: Int,
    ) : SettingsIcon
}

/**
 * One row on the settings screen. [keywordsRes] is an optional resource of extra search
 * terms for the destination sub-screen (English, non-translated). The value is split into
 * words on any whitespace/punctuation run and each word is independently prefix-matched, so
 * separators are interchangeable and multi-word phrases ("zap split") carry no phrase grouping.
 */
data class SettingsEntry(
    val titleRes: StringResource,
    val icon: SettingsIcon,
    val keywordsRes: StringResource? = null,
    val isDanger: Boolean = false,
    val onClick: () -> Unit,
)

/** One category card on the settings screen. */
data class SettingsCategory(
    val titleRes: StringResource,
    val isDanger: Boolean = false,
    val entries: List<SettingsEntry>,
)

private val SEARCH_DELIMITERS = Regex("[^\\p{L}\\p{N}]+")

/** Lowercases and splits text into searchable words on any non-letter/digit run. */
private fun String.searchWords(): List<String> = lowercase().split(SEARCH_DELIMITERS).filter { it.isNotEmpty() }

/**
 * Filters [catalog] by [query] using case-insensitive **word-prefix** matching over
 * category title + entry title + keywords: an entry matches when every word in the
 * query is the prefix of some word in that haystack (so `dark mo` matches "dark mode",
 * but `tor` does not match "his**tor**y"). Blank/whitespace query returns [catalog]
 * unchanged. Categories left with no matching entries are dropped. Pure — no
 * Compose/Android — so it is unit-testable; string-resource resolution is injected
 * via [stringLookup].
 */
fun filterSettings(
    catalog: List<SettingsCategory>,
    query: String,
    stringLookup: (StringResource) -> String,
): List<SettingsCategory> {
    val terms = query.searchWords()
    if (terms.isEmpty()) return catalog

    return catalog.mapNotNull { category ->
        val categoryWords = stringLookup(category.titleRes).searchWords()
        val matched =
            category.entries.filter { entry ->
                val words =
                    categoryWords + stringLookup(entry.titleRes).searchWords() +
                        entry.keywordsRes?.let { stringLookup(it).searchWords() }.orEmpty()
                terms.all { term -> words.any { it.startsWith(term) } }
            }
        if (matched.isEmpty()) null else category.copy(entries = matched)
    }
}
