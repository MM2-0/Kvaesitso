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
package com.vitorpamplona.amethyst.service.lang

import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.concurrent.ConcurrentHashMap

/**
 * Matches the language codes the app compares to the ones ML Kit's translator loads models for.
 *
 * Three sources spell the same language differently. Language identification emits a mix of old
 * and new ISO 639 codes (`iw` for Hebrew, but `id` for Indonesian, `no` for Norwegian, `fil` for
 * Filipino). The "Don't translate from" and "Translate to" settings hold `Locale.language`, which
 * gives the old codes (`iw`, `in`) or the new ones (`he`, `id`) depending on the runtime, and `nb`
 * for a Norwegian Bokmål device. The translator accepts `iw`, `in` and `nb` as aliases. So a raw
 * string comparison let a blocked language through to the model download.
 */
object TranslationLanguages {
    // No model for the tag. ConcurrentHashMap can't hold null.
    private const val NO_MODEL = ""

    // Runs for every rendered note, and fromLanguageTag builds its 59-language list on each call.
    // The tags seen are the few dozen the identifier and the settings produce, so this stays small.
    private val translatorCodes = ConcurrentHashMap<String, String>()

    /**
     * The translator's code for [tag], or null when it has no model for it: `und`, romanized tags
     * such as `zh-Latn`, and languages ML Kit doesn't translate.
     */
    fun toTranslatorCode(tag: String): String? = translatorCodes.getOrPut(tag) { resolve(tag) ?: NO_MODEL }.ifEmpty { null }

    private fun resolve(tag: String): String? =
        if (tag.equals("fil", ignoreCase = true)) {
            TranslateLanguage.TAGALOG
        } else {
            TranslateLanguage.fromLanguageTag(tag)
        }

    /**
     * Whether text detected as [detected] should be translated into [translateTo]: both have a
     * model, they are different languages, and [detected] isn't one of [dontTranslateFrom].
     */
    fun shouldTranslate(
        detected: String,
        dontTranslateFrom: Set<String>,
        translateTo: String,
    ): Boolean {
        val source = toTranslatorCode(detected) ?: return false
        val target = toTranslatorCode(translateTo) ?: return false
        if (source == target) return false
        return !blocks(dontTranslateFrom, source)
    }

    /** Whether [dontTranslateFrom] lists the language of translator code [code], in any spelling. */
    fun blocks(
        dontTranslateFrom: Set<String>,
        code: String,
    ): Boolean = dontTranslateFrom.any { toTranslatorCode(it) == code }
}
