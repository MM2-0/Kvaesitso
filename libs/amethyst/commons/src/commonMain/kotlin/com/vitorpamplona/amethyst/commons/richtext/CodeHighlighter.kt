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
package com.vitorpamplona.amethyst.commons.richtext

import androidx.compose.runtime.Immutable

enum class CodeTokenType {
    KEY,
    STRING,
    NUMBER,
    KEYWORD,
    COMMENT,
    PUNCTUATION,

    /** A `<placeholder>` the text stands in for, as NIPs write `<32-bytes lowercase hex>`. */
    PLACEHOLDER,
}

/** A run of [code] from [start] (inclusive) to [end] (exclusive) drawn as [type]. Text between tokens is plain. */
@Immutable
data class CodeToken(
    val start: Int,
    val end: Int,
    val type: CodeTokenType,
)

/**
 * A forgiving highlighter for the JSON-like code in specs and notes: JSON, JSON with `//`
 * comments and `<placeholders>` (how NIPs write events), JavaScript object literals and YAML.
 * It never fails: whatever it does not recognize stays plain text.
 */
object CodeHighlighter {
    private val KEYWORDS = setOf("true", "false", "null", "undefined", "const", "let", "var", "function", "return", "new", "await", "async")

    fun tokenize(
        code: String,
        language: String?,
    ): List<CodeToken> {
        val lang = language?.lowercase()
        val hashComments = lang == "yaml" || lang == "yml"
        val singleQuotes = lang == "js" || lang == "javascript" || lang == "ts" || lang == "typescript" || hashComments

        val tokens = ArrayList<CodeToken>()
        val n = code.length
        var i = 0
        // The last non-space character on this line before i: a key starts a line or follows `{`, `,` or `- `.
        var lineLead = '\n'

        while (i < n) {
            val c = code[i]
            when {
                c == '\n' -> {
                    lineLead = '\n'
                    i++
                    continue
                }

                c == ' ' || c == '\t' || c == '\r' -> {
                    i++
                    continue
                }

                c == '/' && i + 1 < n && code[i + 1] == '/' && startsComment(code.getOrNull(i - 1)) -> {
                    val end = lineEnd(code, i)
                    tokens.add(CodeToken(i, end, CodeTokenType.COMMENT))
                    i = end
                    continue
                }

                c == '/' && i + 1 < n && code[i + 1] == '*' -> {
                    val close = code.indexOf("*/", i + 2)
                    val end = if (close < 0) n else close + 2
                    tokens.add(CodeToken(i, end, CodeTokenType.COMMENT))
                    i = end
                    continue
                }

                c == '#' && hashComments && (i == 0 || code[i - 1].isWhitespace()) -> {
                    val end = lineEnd(code, i)
                    tokens.add(CodeToken(i, end, CodeTokenType.COMMENT))
                    i = end
                    continue
                }

                c == '"' || (c == '\'' && singleQuotes) -> {
                    val end = stringEnd(code, i, c)
                    val type = if (isKeyPosition(lineLead) && followedByColon(code, end)) CodeTokenType.KEY else CodeTokenType.STRING
                    tokens.add(CodeToken(i, end, type))
                    i = end
                }

                c == '<' && isPlaceholderStart(code, i) -> {
                    val end = code.indexOf('>', i) + 1
                    tokens.add(CodeToken(i, end, CodeTokenType.PLACEHOLDER))
                    i = end
                }

                c.isDigit() || (c == '-' && i + 1 < n && code[i + 1].isDigit() && !isIdentifierPart(code.getOrNull(i - 1))) -> {
                    if (isIdentifierPart(code.getOrNull(i - 1))) {
                        i++
                    } else {
                        val end = numberEnd(code, i)
                        tokens.add(CodeToken(i, end, CodeTokenType.NUMBER))
                        i = end
                    }
                }

                isIdentifierStart(c) -> {
                    var end = i + 1
                    while (end < n && isIdentifierPart(code[end])) end++
                    val word = code.substring(i, end)
                    when {
                        isKeyPosition(lineLead) && followedByColon(code, end) -> tokens.add(CodeToken(i, end, CodeTokenType.KEY))
                        word in KEYWORDS -> tokens.add(CodeToken(i, end, CodeTokenType.KEYWORD))
                    }
                    i = end
                }

                c == '{' || c == '}' || c == '[' || c == ']' || c == ',' || c == ':' -> {
                    tokens.add(CodeToken(i, i + 1, CodeTokenType.PUNCTUATION))
                    i++
                }

                else -> {
                    i++
                }
            }
            lineLead = code[i - 1].let { if (it.isWhitespace()) lineLead else it }
        }
        return tokens
    }

    private fun lineEnd(
        code: String,
        from: Int,
    ): Int = code.indexOf('\n', from).let { if (it < 0) code.length else it }

    /** The index after the closing quote, honoring backslash escapes; a string never runs past its line. */
    private fun stringEnd(
        code: String,
        from: Int,
        quote: Char,
    ): Int {
        var j = from + 1
        while (j < code.length) {
            val ch = code[j]
            if (ch == '\\') {
                j += 2
                continue
            }
            if (ch == quote) return j + 1
            if (ch == '\n') return j
            j++
        }
        return code.length
    }

    private fun numberEnd(
        code: String,
        from: Int,
    ): Int {
        var j = from + 1
        while (j < code.length && (code[j].isDigit() || code[j] == '.' || code[j] == 'e' || code[j] == 'E' || ((code[j] == '+' || code[j] == '-') && (code[j - 1] == 'e' || code[j - 1] == 'E')))) j++
        return j
    }

    private fun followedByColon(
        code: String,
        from: Int,
    ): Boolean {
        var j = from
        while (j < code.length && (code[j] == ' ' || code[j] == '\t')) j++
        return j < code.length && code[j] == ':' && code.getOrNull(j + 1) != ':' && code.getOrNull(j + 1) != '/'
    }

    private fun isKeyPosition(lead: Char) = lead == '\n' || lead == '{' || lead == ',' || lead == '-'

    /** A `//` after a space or a delimiter opens a comment; one after `https:` is part of an unquoted URL. */
    private fun startsComment(previous: Char?) = previous == null || previous.isWhitespace() || previous == ',' || previous == '{' || previous == '[' || previous == '}' || previous == ']'

    /** `<hex>`, `<32-bytes lowercase hex>`: a `<`, then a letter or digit, then a `>` before the line ends. */
    private fun isPlaceholderStart(
        code: String,
        at: Int,
    ): Boolean {
        val next = code.getOrNull(at + 1) ?: return false
        if (!next.isLetterOrDigit()) return false
        val close = code.indexOf('>', at)
        val newline = code.indexOf('\n', at)
        return close > 0 && (newline < 0 || close < newline) && close - at < 240
    }

    private fun isIdentifierStart(c: Char) = c.isLetter() || c == '_' || c == '$'

    private fun isIdentifierPart(c: Char?) = c != null && (c.isLetterOrDigit() || c == '_' || c == '$' || c == '-')
}
