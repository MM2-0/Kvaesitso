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
package com.vitorpamplona.amethyst.commons.chats.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.model.ChatLayoutType
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.util.codePointAtKmp
import com.vitorpamplona.amethyst.commons.util.codePointCharCount

/**
 * Whether an emoji-only message may render as jumbo emoji here. The compact chat layout turns it
 * off: a 48sp emoji tripled the height of a layout whose point is one short line per message.
 */
val LocalChatJumboEmoji = compositionLocalOf { true }

/**
 * Whether an emoji-only message renders jumbo here: not in the compact layout, and not where a
 * caller turned it off ([LocalChatJumboEmoji]). Reads the layout setting itself, so a renderer that
 * decides before ChatBubbleLayout draws its row (cordn's) agrees with one that decides inside it.
 */
@Composable
fun chatJumboEmojiAllowed(): Boolean = LocalChatJumboEmoji.current && LocalDisplaySettings.current.chatLayout != ChatLayoutType.COMPACT

/** A message of up to this many emoji renders as jumbo emoji without a bubble. */
const val MAX_JUMBO_EMOJI = 3

/**
 * Number of emoji in [content] when the message is emoji-only (ignoring
 * whitespace) and short enough to render jumbo, or 0 when it contains any
 * non-emoji text or more than [MAX_JUMBO_EMOJI] emoji.
 *
 * Modifier code points (variation selectors, ZWJ, skin tones, keycap marks)
 * don't count as separate emoji: a ZWJ family counts what it draws as, a keycap
 * like 1️⃣ counts as one, and a flag needs its full regional-indicator pair —
 * incomplete sequences make the message non-jumbo.
 *
 * Built on the KMP-safe code-point helpers so the logic can move to commonMain.
 */
fun jumboEmojiCount(content: String): Int {
    var count = 0
    var previousWasZwj = false

    // A regional indicator waiting for its pair (flags are two RIs = one emoji).
    var pendingRegionalIndicator = false

    // An ASCII keycap base ('0'-'9', '#', '*') waiting for its combining keycap
    // mark; only then does it count as one emoji.
    var pendingKeycapBase = false

    var i = 0
    while (i < content.length) {
        val cp = content.codePointAtKmp(i)
        i += codePointCharCount(cp)

        when {
            isWhitespace(cp) -> {
                if (pendingKeycapBase || pendingRegionalIndicator) return 0
                previousWasZwj = false
            }

            cp == KEYCAP_MARK -> {
                // Completes a keycap sequence like '1' + VS16 + U+20E3.
                if (!pendingKeycapBase) return 0
                pendingKeycapBase = false
                count++
                previousWasZwj = false
            }

            isEmojiModifier(cp) -> {
                previousWasZwj = cp == ZWJ
            }

            isKeycapBase(cp) -> {
                // A bare digit is only an emoji when its keycap mark follows.
                if (pendingKeycapBase || pendingRegionalIndicator) return 0
                pendingKeycapBase = true
                previousWasZwj = false
            }

            isRegionalIndicator(cp) -> {
                if (pendingKeycapBase) return 0
                // Two regional indicators pair into one flag; count on completion.
                if (pendingRegionalIndicator) {
                    pendingRegionalIndicator = false
                    count++
                } else {
                    pendingRegionalIndicator = true
                }
                previousWasZwj = false
            }

            isEmojiBase(cp) -> {
                if (pendingKeycapBase || pendingRegionalIndicator) return 0
                if (!previousWasZwj) count++
                previousWasZwj = false
            }

            else -> return 0
        }

        if (count > MAX_JUMBO_EMOJI) return 0
    }

    // Incomplete sequences (half a flag, a digit with no keycap mark) mean the
    // message is not emoji-only.
    if (pendingRegionalIndicator || pendingKeycapBase) return 0

    return count
}

private const val ZWJ = 0x200D
private const val KEYCAP_MARK = 0x20E3

private fun isWhitespace(cp: Int): Boolean = cp <= 0xFFFF && cp.toChar().isWhitespace()

private fun isEmojiModifier(cp: Int): Boolean =
    cp == ZWJ ||
        cp == 0xFE0E || // variation selector 15 (text style)
        cp == 0xFE0F || // variation selector 16 (emoji style)
        cp in 0x1F3FB..0x1F3FF // skin tones

private fun isKeycapBase(cp: Int): Boolean = cp in '0'.code..'9'.code || cp == '#'.code || cp == '*'.code

private fun isRegionalIndicator(cp: Int): Boolean = cp in 0x1F1E6..0x1F1FF

private fun isEmojiBase(cp: Int): Boolean =
    cp in 0x1F000..0x1FAFF || // pictographs, emoticons, transport, supplemental, extended-A
        cp in 0x2600..0x27BF || // misc symbols + dingbats (hearts, stars, hands)
        cp in 0x2B00..0x2BFF || // arrows-C block (star, heavy circles)
        cp == 0x203C || // double exclamation
        cp == 0x2049 || // exclamation question
        cp == 0x00A9 || // copyright (with VS16)
        cp == 0x00AE || // registered (with VS16)
        cp in 0x2934..0x2935 || // arrow emoji
        cp in 0x3297..0x3299 || // circled ideographs
        cp == 0x3030 || // wavy dash
        cp == 0x303D // part alternation mark

// Jumbo sizes step down as the emoji count grows so up to three still fit a line.
private val JumboEmojiSingle = 50.sp
private val JumboEmojiPair = 40.sp
private val JumboEmojiTriple = 32.sp

/**
 * How large to draw an emoji-only message of [jumboCount] emoji. Shared by every chat
 * surface that renders one, so a jumbo message is the same size wherever it appears.
 */
fun jumboEmojiFontSize(jumboCount: Int): TextUnit =
    when (jumboCount) {
        1 -> JumboEmojiSingle
        2 -> JumboEmojiPair
        else -> JumboEmojiTriple
    }
