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
import com.vitorpamplona.amethyst.commons.resources.accent_color
import com.vitorpamplona.amethyst.commons.resources.app_settings
import com.vitorpamplona.amethyst.commons.resources.application_preferences
import com.vitorpamplona.amethyst.commons.resources.automatically_hide_nav_bars
import com.vitorpamplona.amethyst.commons.resources.automatically_load_images_gifs
import com.vitorpamplona.amethyst.commons.resources.automatically_play_videos
import com.vitorpamplona.amethyst.commons.resources.automatically_show_profile_picture
import com.vitorpamplona.amethyst.commons.resources.automatically_show_url_preview
import com.vitorpamplona.amethyst.commons.resources.autoplay_videos
import com.vitorpamplona.amethyst.commons.resources.font_family
import com.vitorpamplona.amethyst.commons.resources.font_size
import com.vitorpamplona.amethyst.commons.resources.language
import com.vitorpamplona.amethyst.commons.resources.navigation_layout
import com.vitorpamplona.amethyst.commons.resources.relays
import com.vitorpamplona.amethyst.commons.resources.theme
import com.vitorpamplona.amethyst.commons.resources.ui_preferences
import com.vitorpamplona.amethyst.commons.resources.ui_preferences_search_keywords
import com.vitorpamplona.amethyst.commons.resources.ui_style
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The settings search only sees row titles and their keywords, not the screens behind them. The
 * "UI Preferences" row opens Application Preferences, so each option on that screen has to be
 * findable through the row's keywords, with the real English strings.
 */
class UiPreferencesSearchKeywordsTest {
    private val optionsOnScreen =
        listOf(
            Res.string.application_preferences,
            Res.string.theme,
            Res.string.accent_color,
            Res.string.font_family,
            Res.string.font_size,
            Res.string.language,
            Res.string.ui_style,
            Res.string.automatically_hide_nav_bars,
            Res.string.navigation_layout,
            Res.string.automatically_load_images_gifs,
            Res.string.automatically_play_videos,
            Res.string.autoplay_videos,
            Res.string.automatically_show_url_preview,
            Res.string.automatically_show_profile_picture,
        )

    private val catalog =
        listOf(
            SettingsCategory(
                titleRes = Res.string.app_settings,
                entries =
                    listOf(
                        SettingsEntry(
                            titleRes = Res.string.ui_preferences,
                            icon = SettingsIcon.Painter(Res.drawable.relays, 0),
                            keywordsRes = Res.string.ui_preferences_search_keywords,
                            onClick = {},
                        ),
                    ),
            ),
        )

    @Test
    fun everyOptionOnTheScreenFindsTheRow() =
        runBlocking {
            val labels = (optionsOnScreen + Res.string.app_settings + Res.string.ui_preferences + Res.string.ui_preferences_search_keywords).associateWith { getString(it) }
            optionsOnScreen.forEach { option ->
                val query = labels.getValue(option)
                val found = filterSettings(catalog, query) { labels.getValue(it) }
                assertEquals("searching \"$query\"", 1, found.sumOf { it.entries.size })
            }
        }
}
