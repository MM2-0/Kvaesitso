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
package com.vitorpamplona.amethyst.commons.qrcode

/**
 * The codes found in one still image, with Structured Append parts already joined.
 *
 * [texts] holds every ordinary code plus every multi-part code whose parts were all in the
 * picture, joined. [incomplete] is the progress of the best multi-part code that was *not* whole
 * (captured to total), or null when there was none.
 */
data class AssembledCodes(
    val texts: List<String>,
    val incomplete: Pair<Int, Int>?,
)

/**
 * Joins the Structured Append parts in one still image.
 *
 * The camera feeds parts to a [StructuredAppendAccumulator] across frames; a picture has only the
 * one frame, so every part it holds is fed at once. Without this a screenshot of a split key
 * backup listed each fragment as a separate code — and only the first fragment of a split `nsec`
 * starts with `nsec1`, so the rest were classified as harmless text and printed with a Copy
 * button. A fragment is never returned on its own: either its sequence completes, or it only
 * counts toward [AssembledCodes.incomplete].
 */
fun assembleStructuredAppend(results: List<ScanResult>): AssembledCodes {
    val texts = mutableListOf<String>()
    var incomplete: Pair<Int, Int>? = null

    results.filterNot { it.isPartOfSequence }.mapTo(texts) { it.text }

    results
        .filter { it.isPartOfSequence }
        .groupBy { it.sequenceId to it.sequenceSize }
        .values
        .forEach { group ->
            val accumulator = StructuredAppendAccumulator()
            var joined: String? = null
            for (part in group.sortedBy { it.sequenceIndex }) {
                joined = accumulator.add(part, nowMs = 0) ?: continue
                break
            }
            if (joined != null) {
                texts += joined
            } else {
                val best = incomplete
                if (best == null || accumulator.captured > best.first) {
                    incomplete = accumulator.captured to accumulator.total
                }
            }
        }

    return AssembledCodes(texts.distinct(), incomplete)
}

/** What one imported picture should lead to, decided once for every image entry point. */
sealed interface ImageCodesOutcome {
    /** No QR code in the picture at all. */
    data object NothingFound : ImageCodesOutcome

    /** Only part of a multi-part code: nothing can be opened until the rest is scanned. */
    data class OnlyPartial(
        val captured: Int,
        val total: Int,
    ) : ImageCodesOutcome

    /** Exactly one code and nothing else in the picture, so asking would only add a tap. */
    data class Open(
        val text: String,
    ) : ImageCodesOutcome

    /**
     * The user picks. Several codes, or one code next to part of a multi-part code: opening the
     * one silently would hide that the picture also held a half-captured sequence (often a split
     * key backup), so [partial] is shown alongside the choice.
     */
    data class Choose(
        val texts: List<String>,
        val partial: Pair<Int, Int>?,
    ) : ImageCodesOutcome
}

fun AssembledCodes.outcome(): ImageCodesOutcome {
    val partial = incomplete
    return when {
        texts.isEmpty() && partial != null -> ImageCodesOutcome.OnlyPartial(partial.first, partial.second)
        texts.isEmpty() -> ImageCodesOutcome.NothingFound
        texts.size == 1 && partial == null -> ImageCodesOutcome.Open(texts.first())
        else -> ImageCodesOutcome.Choose(texts, partial)
    }
}
