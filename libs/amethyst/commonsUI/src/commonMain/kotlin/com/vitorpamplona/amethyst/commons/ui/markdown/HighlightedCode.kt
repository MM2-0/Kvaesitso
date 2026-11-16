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
package com.vitorpamplona.amethyst.commons.ui.markdown

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import com.vitorpamplona.amethyst.commons.richtext.CodeHighlighter
import com.vitorpamplona.amethyst.commons.richtext.CodeTokenType
import com.vitorpamplona.amethyst.commons.ui.theme.isLight

/** The colors of highlighted code, one palette per theme (picked for contrast on the code block's tint). */
@Immutable
class CodeColors(
    val key: Color,
    val string: Color,
    val number: Color,
    val keyword: Color,
    val comment: Color,
    val punctuation: Color,
    val placeholder: Color,
) {
    companion object {
        val Dark =
            CodeColors(
                key = Color(0xFF9CDCFE),
                string = Color(0xFFCE9178),
                number = Color(0xFFB5CEA8),
                keyword = Color(0xFFC586C0),
                comment = Color(0xFF8B949E),
                punctuation = Color(0xFFA0A8B4),
                placeholder = Color(0xFFDCDCAA),
            )

        val Light =
            CodeColors(
                key = Color(0xFF0451A5),
                string = Color(0xFFA31515),
                number = Color(0xFF098658),
                keyword = Color(0xFF8E24AA),
                comment = Color(0xFF6A737D),
                punctuation = Color(0xFF57606A),
                placeholder = Color(0xFF795E26),
            )
    }
}

val ColorScheme.codeColors: CodeColors get() = if (isLight) CodeColors.Light else CodeColors.Dark

/** [code] with the [CodeHighlighter]'s tokens colored; text between tokens keeps the block's color. */
fun highlightCode(
    code: String,
    language: String?,
    colors: CodeColors,
): AnnotatedString =
    buildAnnotatedString {
        append(code)
        CodeHighlighter.tokenize(code, language).forEach { token ->
            val style =
                when (token.type) {
                    CodeTokenType.KEY -> SpanStyle(color = colors.key)
                    CodeTokenType.STRING -> SpanStyle(color = colors.string)
                    CodeTokenType.NUMBER -> SpanStyle(color = colors.number)
                    CodeTokenType.KEYWORD -> SpanStyle(color = colors.keyword)
                    CodeTokenType.COMMENT -> SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)
                    CodeTokenType.PUNCTUATION -> SpanStyle(color = colors.punctuation)
                    CodeTokenType.PLACEHOLDER -> SpanStyle(color = colors.placeholder, fontStyle = FontStyle.Italic)
                }
            addStyle(style, token.start, token.end)
        }
    }
