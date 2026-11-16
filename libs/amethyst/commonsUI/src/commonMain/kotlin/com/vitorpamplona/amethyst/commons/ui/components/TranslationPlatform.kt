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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The on-device translator behind every translatable text, supplied by the platform through
 * [LocalTranslationPlatform]. Android's Play build installs its ML Kit translator; builds and
 * platforms without one fall back to [None], which shows the text as written.
 */
@Stable
interface TranslationPlatform {
    /**
     * Renders [content] through [displayText], translated when the account's language settings
     * ask for it, with the platform's "translated from" bar under it.
     */
    @Composable
    fun Translatable(
        content: String,
        id: String,
        translationMessageModifier: Modifier,
        accountViewModel: AccountViewModel,
        displayText: @Composable (String) -> Unit,
    )

    /** The translation of [content] under the current settings, or [content] when none applies. */
    @Composable
    fun rememberTranslation(
        content: String,
        accountViewModel: AccountViewModel,
    ): String

    /**
     * The already-computed translation of [content] under the current settings, or null when
     * none occurred or none is cached. Cache-only: it backs "Copy Translated", which applies
     * to text on screen, and rendering that text through [Translatable] filled the cache.
     */
    fun cachedTranslation(
        content: String,
        accountViewModel: AccountViewModel,
    ): String? = null

    /**
     * Whether this translator downloads a model per language, which the app settings then let the
     * user hold to Wi-Fi. False for [None].
     */
    val downloadsModels: Boolean get() = false

    /**
     * The user just added [language] to the account's "Don't translate from" list, so the
     * platform can drop what it stored for translating from it. No-op for [None].
     */
    fun onLanguageBlocked(
        language: String,
        accountViewModel: AccountViewModel,
    ) {}

    /** No translator: the content is always its own translation. */
    object None : TranslationPlatform {
        @Composable
        override fun Translatable(
            content: String,
            id: String,
            translationMessageModifier: Modifier,
            accountViewModel: AccountViewModel,
            displayText: @Composable (String) -> Unit,
        ) = displayText(content)

        @Composable
        override fun rememberTranslation(
            content: String,
            accountViewModel: AccountViewModel,
        ): String = content
    }
}

val LocalTranslationPlatform = staticCompositionLocalOf<TranslationPlatform> { TranslationPlatform.None }
