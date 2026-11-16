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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import androidx.compose.ui.platform.UriHandler
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.about_legal
import com.vitorpamplona.amethyst.commons.resources.child_safety_search_keywords
import com.vitorpamplona.amethyst.commons.resources.child_safety_standards
import com.vitorpamplona.amethyst.commons.resources.privacy_policy
import com.vitorpamplona.amethyst.commons.resources.privacy_policy_search_keywords
import com.vitorpamplona.amethyst.commons.ui.settings.SettingsCategory
import com.vitorpamplona.amethyst.commons.ui.settings.SettingsEntry
import com.vitorpamplona.amethyst.commons.ui.settings.SettingsIcon

/** Play build surfaces the GitHub-hosted legal policy links. */
fun legalSettingsCategory(uriHandler: UriHandler): SettingsCategory? =
    SettingsCategory(
        titleRes = Res.string.about_legal,
        entries =
            listOf(
                SettingsEntry(
                    titleRes = Res.string.privacy_policy,
                    icon = SettingsIcon.Symbol(MaterialSymbols.Lock),
                    keywordsRes = Res.string.privacy_policy_search_keywords,
                    onClick = {
                        runCatching {
                            uriHandler.openUri(
                                "https://github.com/vitorpamplona/amethyst/blob/main/PRIVACY.md",
                            )
                        }
                    },
                ),
                SettingsEntry(
                    titleRes = Res.string.child_safety_standards,
                    icon = SettingsIcon.Symbol(MaterialSymbols.Shield),
                    keywordsRes = Res.string.child_safety_search_keywords,
                    onClick = {
                        runCatching {
                            uriHandler.openUri(
                                "https://github.com/vitorpamplona/amethyst/blob/main/PRIVACY.md#child-safety-standards",
                            )
                        }
                    },
                ),
            ),
    )
