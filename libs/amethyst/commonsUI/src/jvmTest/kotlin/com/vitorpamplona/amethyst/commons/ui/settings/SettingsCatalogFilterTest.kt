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

import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.account_settings
import com.vitorpamplona.amethyst.commons.resources.backup_keys
import com.vitorpamplona.amethyst.commons.resources.danger_zone
import com.vitorpamplona.amethyst.commons.resources.relays
import com.vitorpamplona.amethyst.commons.resources.settings_section_appearance
import com.vitorpamplona.amethyst.commons.resources.theme
import org.jetbrains.compose.resources.StringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogFilterTest {
    // Real catalog entries stand in for the rows, but their text comes from this map:
    // filterSettings takes its lookup as a parameter precisely so the filter itself
    // stays Compose-free, and resolving a StringResource for real would need a runtime.
    private val accountCategory = Res.string.account_settings
    private val dangerCategory = Res.string.danger_zone
    private val relayEntry = Res.string.relays
    private val uiEntry = Res.string.settings_section_appearance
    private val backupEntry = Res.string.backup_keys
    private val uiKeywords = Res.string.theme

    private val strings =
        mapOf(
            accountCategory to "Account Settings",
            dangerCategory to "Danger Zone",
            relayEntry to "Relay Setup",
            uiEntry to "UI Preferences",
            backupEntry to "Backup Keys",
            uiKeywords to "dark mode, theme, font size",
        )

    private fun entry(
        titleRes: StringResource,
        keywordsRes: StringResource? = null,
        isDanger: Boolean = false,
    ) = SettingsEntry(
        titleRes = titleRes,
        icon = SettingsIcon.Painter(Res.drawable.relays, 0),
        keywordsRes = keywordsRes,
        isDanger = isDanger,
        onClick = {},
    )

    private val catalog =
        listOf(
            SettingsCategory(
                titleRes = accountCategory,
                entries =
                    listOf(
                        entry(relayEntry),
                        entry(uiEntry, keywordsRes = uiKeywords),
                    ),
            ),
            SettingsCategory(
                titleRes = dangerCategory,
                isDanger = true,
                entries = listOf(entry(backupEntry, isDanger = true)),
            ),
        )

    private fun run(query: String) =
        filterSettings(
            catalog = catalog,
            query = query,
            stringLookup = { strings.getValue(it) },
        )

    @Test
    fun blankQueryReturnsFullCatalog() {
        val result = run("")
        assertEquals(2, result.size)
        assertEquals(2, result[0].entries.size)
        assertEquals(1, result[1].entries.size)
    }

    @Test
    fun whitespaceQueryReturnsFullCatalog() {
        assertEquals(2, run("   ").size)
    }

    @Test
    fun titleMatchIsCaseInsensitive() {
        val result = run("relay")
        assertEquals(1, result.size)
        assertEquals(accountCategory, result[0].titleRes)
        assertEquals(1, result[0].entries.size)
        assertEquals(relayEntry, result[0].entries[0].titleRes)
    }

    @Test
    fun keywordMatchSurfacesEntryWhoseTitleDoesNotMatch() {
        val result = run("dark mode")
        assertEquals(1, result.size)
        assertEquals(uiEntry, result[0].entries[0].titleRes) // UI Preferences, matched via keywords
    }

    @Test
    fun categoryTitleMatchSurfacesWholeCategory() {
        val result = run("account")
        assertEquals(1, result.size)
        assertEquals(accountCategory, result[0].titleRes)
        assertEquals(2, result[0].entries.size) // both rows shown because the category name matched
    }

    @Test
    fun categoryWithNoMatchesIsDropped() {
        val result = run("relay")
        assertTrue(result.none { it.titleRes == dangerCategory })
    }

    @Test
    fun noMatchesReturnsEmptyList() {
        assertTrue(run("zzzznomatch").isEmpty())
    }

    @Test
    fun dangerFlagsPreservedThroughFiltering() {
        val result = run("backup")
        assertEquals(1, result.size)
        assertTrue(result[0].isDanger)
        assertTrue(result[0].entries[0].isDanger)
    }

    @Test
    fun prefixOfAWordMatches() {
        // "rel" is a prefix of "Relay" (title); "the" is a prefix of "theme" (keyword).
        assertEquals(relayEntry, run("rel")[0].entries[0].titleRes)
        assertEquals(uiEntry, run("the")[0].entries[0].titleRes)
    }

    @Test
    fun midWordTermDoesNotMatch() {
        // Word-prefix, not substring: "ackup" is inside "Backup" but not a prefix of any word.
        assertTrue(run("ackup").isEmpty())
    }

    @Test
    fun everyQueryTermMustPrefixSomeWord() {
        assertEquals(uiEntry, run("dark size")[0].entries[0].titleRes) // both terms hit UI Preferences
        assertTrue(run("dark zzz").isEmpty()) // second term matches nothing
    }
}
