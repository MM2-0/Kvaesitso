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
package com.vitorpamplona.amethyst.commons.nip34Git.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes

/**
 * Builds a syntax-highlighted [AnnotatedString] for a source file using the
 * `dev.snipme:highlights` KMP tokenizer (Apache-2.0). Highlighting is CPU work,
 * so callers should invoke [highlight] off the main thread.
 */
object CodeHighlighter {
    /** Maps a file name to a supported language, or null when no highlighter fits. */
    fun languageForFile(name: String): SyntaxLanguage? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> SyntaxLanguage.KOTLIN
            "java" -> SyntaxLanguage.JAVA
            "js", "mjs", "cjs", "jsx" -> SyntaxLanguage.JAVASCRIPT
            "ts", "tsx" -> SyntaxLanguage.TYPESCRIPT
            "py" -> SyntaxLanguage.PYTHON
            "rb" -> SyntaxLanguage.RUBY
            "rs" -> SyntaxLanguage.RUST
            "go" -> SyntaxLanguage.GO
            "c", "h" -> SyntaxLanguage.C
            "cpp", "cc", "cxx", "hpp", "hh", "hxx" -> SyntaxLanguage.CPP
            "cs" -> SyntaxLanguage.CSHARP
            "swift" -> SyntaxLanguage.SWIFT
            "php" -> SyntaxLanguage.PHP
            "pl", "pm" -> SyntaxLanguage.PERL
            "dart" -> SyntaxLanguage.DART
            "coffee" -> SyntaxLanguage.COFFEESCRIPT
            "sh", "bash", "zsh", "ksh" -> SyntaxLanguage.SHELL
            else -> null
        }
    }

    fun highlight(
        code: String,
        language: SyntaxLanguage,
        darkMode: Boolean,
    ): AnnotatedString {
        val highlights =
            Highlights
                .Builder()
                .code(code)
                .language(language)
                .theme(SyntaxThemes.darcula(darkMode = darkMode))
                .build()
                .getHighlights()

        return buildAnnotatedString {
            append(code)
            val len = code.length
            highlights.forEach { highlight ->
                val start = highlight.location.start.coerceIn(0, len)
                val end = highlight.location.end.coerceIn(start, len)
                if (start == end) return@forEach
                when (highlight) {
                    is ColorHighlight ->
                        addStyle(SpanStyle(color = Color(0xFF000000L or (highlight.rgb.toLong() and 0xFFFFFF))), start, end)
                    is BoldHighlight ->
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                }
            }
        }
    }
}
