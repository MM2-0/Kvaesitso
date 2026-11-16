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

import com.vitorpamplona.quartz.utils.urldetector.detection.UrlDetector
import java.util.regex.Pattern

/**
 * Pure-JVM helpers that protect non-translatable substrings (URLs, Lightning invoices, NIP-19
 * references, NIP-08 positional references) by swapping them with ICU MessageFormat positional
 * tokens ({0}, {1}, ...) around a translator. Extracted out of [LanguageTranslatorService] so the
 * round-trip can be unit-tested without ML Kit / Android runtime.
 */
internal object TranslationDictionary {
    // Placeholders use ICU MessageFormat positional syntax: {0}, {1}, .... ML Kit's translation
    // models are trained on localization corpora and preserve these tokens intact across all source
    // languages we measured (ja, zh, ko, ar, hi, ru, pt, de). Prior schemes failed: U+E000+ PUA
    // codepoints were dropped by CJK models (issue #1180); "B0/C0/A0" markers collided with user
    // text. 10k slots per note is effectively unbounded.
    const val PLACEHOLDER_LIMIT: Int = 9999

    // Texts shorter than this, or with no letter codepoints (emoji-only, punctuation), are skipped
    // before any ML Kit work — language identification is unreliable on them.
    private const val MIN_TRANSLATABLE_LENGTH = 4

    val lnRegex: Pattern = Pattern.compile("\\blnbc[a-z0-9]+\\b", Pattern.CASE_INSENSITIVE)
    val tagRegex: Pattern =
        Pattern.compile(
            "(nostr:)?@?(nsec1|npub1|nevent1|naddr1|note1|nprofile1|nrelay1)([qpzry9x8gf2tvdw0s3jn54khce6mua7l]+)",
            Pattern.CASE_INSENSITIVE,
        )

    // Legacy NIP-08 positional references like #[0]. Translators tend to insert a space inside the
    // brackets ("# [0]"), so we shield them via the placeholder dictionary.
    val nip08RefRegex: Pattern = Pattern.compile("#\\[\\d+]")

    // Matches placeholders the user already wrote in their note (e.g. a code snippet containing
    // `{0}`). We start our own counter above the max user-supplied index so encode/decode can't
    // clobber it.
    private val placeholderRegex: Pattern = Pattern.compile("\\{(\\d+)\\}")

    fun isWorthTranslating(text: String): Boolean {
        if (text.length < MIN_TRANSLATABLE_LENGTH) return false
        for (cp in text.codePoints()) {
            if (Character.isLetter(cp)) return true
        }
        return false
    }

    fun build(text: String): Map<String, String> {
        val dict = LinkedHashMap<String, String>()
        var counter = firstFreeIndex(text)

        fun addUnique(value: String) {
            if (value.isEmpty()) return
            if (counter > PLACEHOLDER_LIMIT) return
            if (dict.containsValue(value)) return
            dict[placeholder(counter++)] = value
        }

        lnRegex.forEachMatch(text, ::addUnique)
        tagRegex.forEachMatch(text, ::addUnique)
        nip08RefRegex.forEachMatch(text, ::addUnique)

        for (url in UrlDetector(text).detect()) {
            val original = url.originalUrl
            // The URL detector greedily includes Chinese full-width punctuation; skip those false hits.
            if (original.contains('，') || original.contains('。')) continue
            addUnique(original)
        }

        return dict
    }

    private fun firstFreeIndex(text: String): Int {
        if (text.indexOf('{') < 0) return 0
        var max = -1
        val matcher = placeholderRegex.matcher(text)
        while (matcher.find()) {
            val idx = matcher.group(1)?.toIntOrNull() ?: continue
            // Clamp at the table limit so adversarial user text like `{99999}` can't push our
            // counter past PLACEHOLDER_LIMIT and make every subsequent placeholder() throw.
            if (idx > PLACEHOLDER_LIMIT) continue
            if (idx > max) max = idx
        }
        return max + 1
    }

    private inline fun Pattern.forEachMatch(
        text: String,
        block: (String) -> Unit,
    ) {
        val matcher = matcher(text)
        while (matcher.find()) block(matcher.group())
    }

    fun encode(
        text: String,
        dict: Map<String, String>,
    ): String {
        if (dict.isEmpty()) return text
        var newText = text
        // Replace longest values first so a URL prefix never clobbers a longer URL or tag.
        for ((token, original) in dict.entries.sortedByDescending { it.value.length }) {
            newText = newText.replace(original, token, ignoreCase = false)
        }
        return newText
    }

    fun decode(
        text: String?,
        dict: Map<String, String>,
    ): String? {
        if (text == null || dict.isEmpty()) return text
        var newText: String = text
        for ((token, original) in dict) {
            newText = newText.replace(token, original, ignoreCase = false)
        }
        return newText
    }

    fun placeholder(index: Int): String {
        require(index in 0..PLACEHOLDER_LIMIT) { "placeholder index $index out of range" }
        return "{$index}"
    }
}
