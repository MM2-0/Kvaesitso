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
package com.vitorpamplona.amethyst.commons.ui.actions

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.ui.spellcheck.TextReplacement
import com.vitorpamplona.amethyst.commons.ui.spellcheck.VisualOffsetMapping
import com.vitorpamplona.amethyst.commons.ui.text.MENTION_REGEX
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.utils.urldetector.detection.UrlDetector
import kotlin.coroutines.cancellation.CancellationException

/**
 * The field's [UrlUserTagOutputTransformation], kept across recompositions. BasicTextField keys its
 * transformed state on the transformation's equality: a new instance per recomposition restarts a
 * focused field's keyboard session and drops the mention cache.
 */
@Composable
fun rememberUrlUserTagOutputTransformation(color: Color = MaterialTheme.colorScheme.primary): UrlUserTagOutputTransformation = remember(color) { UrlUserTagOutputTransformation(color) }

class UrlUserTagOutputTransformation(
    val color: Color,
) : OutputTransformation,
    VisualOffsetMapping {
    override fun TextFieldBuffer.transformOutput() {
        val replacements = replacements(asCharSequence().toString())

        // Phase 1: replace mentions back to front, so earlier indices stay valid after each replace.
        // Styles come after: addStyle must be called after all text mutations.
        for (index in replacements.indices.reversed()) {
            val replacement = replacements[index]
            replace(replacement.rawStart, replacement.rawEnd, replacement.visual)
        }

        // Phase 2: style the display names, front to back, tracking the shift of earlier replacements.
        val style = SpanStyle(color = color, textDecoration = TextDecoration.None)
        var cumulativeShift = 0
        for (replacement in replacements) {
            val adjustedStart = replacement.rawStart + cumulativeShift
            addStyle(style, adjustedStart, adjustedStart + replacement.visual.length)
            cumulativeShift += replacement.visual.length - (replacement.rawEnd - replacement.rawStart)
        }

        // Highlight URLs in remaining text
        highlightUrls(color)
    }

    // The last text and its replacements. transformOutput and the spell checker both ask for the
    // same text right after each edit, and must agree: a display name that loads in between would
    // otherwise shift the checker's offsets away from what the field drew.
    private var lastText: String? = null
    private var lastReplacements: List<TextReplacement> = emptyList()

    /** Each `@npub…`/`nostr:nprofile…` mention and the `@Name` the field draws instead, in order. */
    override fun replacements(rawText: String): List<TextReplacement> {
        if (rawText == lastText) return lastReplacements
        return findReplacements(rawText).also {
            lastText = rawText
            lastReplacements = it
        }
    }

    private fun findReplacements(rawText: String): List<TextReplacement> =
        MENTION_REGEX.findAll(rawText).mapNotNullTo(ArrayList()) { match ->
            try {
                val bech32 =
                    match.value
                        .removePrefix("@")
                        .removePrefix("nostr:")
                val hex = decodePublicKeyAsHexOrNull(bech32) ?: return@mapNotNullTo null
                val user = LocalCache.getOrCreateUser(hex)
                TextReplacement(match.range.first, match.range.last + 1, "@${user.toBestDisplayName()}")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }
}

// The same detector the note renderer links with, so the editor highlights what the posted
// note will make clickable.
private fun TextFieldBuffer.highlightUrls(color: Color) {
    val text = asCharSequence().toString()
    var searchFrom = 0
    UrlDetector(text).detect().forEach { url ->
        val start = text.indexOf(url.originalUrl, searchFrom)
        if (start >= 0) {
            val end = start + url.originalUrl.length
            addStyle(SpanStyle(color = color, textDecoration = TextDecoration.None), start, end)
            searchFrom = end
        }
    }
}
