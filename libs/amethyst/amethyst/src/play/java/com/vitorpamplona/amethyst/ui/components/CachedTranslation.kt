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
package com.vitorpamplona.amethyst.ui.components

import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.lang.TranslationsCache

/**
 * The already-computed translation of [content] under the current language settings, or null
 * when no translation occurred (same language, undetected source, blocklisted) or none is
 * cached. Cache-only on purpose: this backs the "Copy Translated" option of the copy-text
 * menus, which only applies to text the user is looking at — and rendering it through
 * [MlKitTranslationPlatform] is what populated the cache.
 */
internal fun cachedMlKitTranslation(
    content: String,
    accountViewModel: AccountViewModel,
): String? {
    val languages = accountViewModel.account.settings.syncedSettings.languages
    val config =
        TranslationsCache.get(content, languages.translateTo.value, languages.dontTranslateFrom.value)
            ?: return null
    val source = config.sourceLang ?: return null
    val target = config.targetLang ?: return null
    if (source == target || config.result == content) return null
    return config.result
}
