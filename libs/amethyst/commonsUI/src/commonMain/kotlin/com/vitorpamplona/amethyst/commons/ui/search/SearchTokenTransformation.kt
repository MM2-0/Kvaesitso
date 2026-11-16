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
package com.vitorpamplona.amethyst.commons.ui.search

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.SpanStyle
import com.vitorpamplona.amethyst.commons.search.KindRegistry
import com.vitorpamplona.amethyst.commons.search.SearchSegment
import com.vitorpamplona.amethyst.commons.search.SearchTokenizer
import com.vitorpamplona.amethyst.commons.search.rawText

/**
 * One stretch of the typed text and what is drawn over it.
 *
 * [start]..[end] is the span in the value the reader is actually editing; [drawn] is what the
 * field shows there. They are usually the same string — a hashtag draws as itself, in colour —
 * and differ only where the typed form is unreadable: a 63-character npub draws as its owner's
 * name, a NIP-19 pointer as a short form.
 */
@Immutable
internal data class DrawnRun(
    val start: Int,
    val end: Int,
    val drawn: String,
    val style: SpanStyle?,
)

/** The drawn text a list of runs adds up to. */
internal fun List<DrawnRun>.drawnText(): String = joinToString("") { it.drawn }

/**
 * Swaps each run's span for its drawn form. Back to front, so the offsets of the runs not yet
 * visited still point at the untouched text.
 */
internal fun TextFieldBuffer.replaceRuns(
    typed: CharSequence,
    runs: List<DrawnRun>,
) {
    for (i in runs.indices.reversed()) {
        val run = runs[i]
        val unchanged = run.drawn.length == run.end - run.start && typed.regionMatches(run.start, run.drawn, 0, run.drawn.length)
        if (!unchanged) replace(run.start, run.end, run.drawn)
    }
}

/**
 * Draws the tokens the search language holds as chips, over the plain text that stays the field's
 * real value.
 *
 * The value is never rewritten — every rendering is a view of it — so undo, IME composition, text
 * selection and a paste all keep working on exactly what the reader typed, and copying the field
 * yields a query that can be pasted back.
 *
 * Mapping the caret, the selection and the IME's composing region across a chip that is shorter
 * or longer than its text is left to the text field: an [OutputTransformation]'s replacements are
 * mapped by the field itself, which keeps every one of those ranges inside the drawn text. (The
 * legacy `VisualTransformation` this replaced asked a hand-written offset mapping instead, and
 * Android's keyboard bridge crashed on it whenever the composing region sat inside a chip.)
 *
 * A token only settles into a chip once the caret has left it: [caret] is read on every run and
 * passed straight through to [SearchTokenizer.drawable], so a half-typed `#bit` stays plain text
 * under the cursor rather than reflowing on every keystroke. It is a snapshot read, so the field
 * redraws when it changes; return null for a field nobody is typing in.
 *
 * [displayName] is asked for a key token's owner and [groupName] for a group's id; returning null
 * leaves the token short-formed rather than named, which is what something that has not arrived
 * yet should look like — an invented name would be worse than a visible id.
 */
@Immutable
class SearchTokenTransformation(
    private val caret: () -> Int?,
    private val styles: SearchTokenStyles,
    private val displayName: (String) -> String?,
    private val groupName: (String) -> String? = { null },
    /**
     * A NIP-73 scope's human name, given its field and value — a `geo:` geohash's city, say.
     * Synchronous by contract: this runs inside a text transformation, so it may only read what
     * is already resolved and must return null rather than wait for anything.
     */
    private val scopeName: (String, String) -> String? = { _, _ -> null },
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val typed = asCharSequence().toString()
        val runs = runs(typed)
        replaceRuns(typed, runs)

        // Styles go on after every replacement, in the drawn text's own coordinates.
        var at = 0
        runs.forEach { run ->
            run.style?.let { addStyle(it, at, at + run.drawn.length) }
            at += run.drawn.length
        }
    }

    /** How [text] is drawn, run by run; the runs tile it end to end. */
    internal fun runs(text: String): List<DrawnRun> {
        var at = 0
        return SearchTokenizer.drawable(text, caret()).map { seg ->
            val raw = seg.rawText
            val start = at
            at += raw.length
            DrawnRun(start, at, drawnForm(seg, raw), styles.styleFor(seg))
        }
    }

    /** What a segment draws as. Everything but a key and a pointer draws as it was typed. */
    private fun drawnForm(
        seg: SearchSegment,
        raw: String,
    ): String =
        when (seg) {
            is SearchSegment.Key -> {
                val name = displayName(seg.pubkey)?.takeIf { it.isNotBlank() }
                val shown = name?.let { clip(it, 32) } ?: shortBech32(rawKeyOf(raw))
                seg.field?.let { "${it.token}:$shown" } ?: shown
            }

            is SearchSegment.Pointer -> "to:${shortBech32(raw.substringAfter(':'))}"
            // A group id is a stranger's opaque string; its name is the only part a reader can
            // check against the room they meant. The id stays the value, so the query is unchanged.
            is SearchSegment.Group -> "group:${groupName(seg.id)?.takeIf { it.isNotBlank() }?.let { clip(it, 32) } ?: seg.id}"
            // Likewise a geohash: "9q8yy" says nothing, "San Francisco" says what was filtered on.
            is SearchSegment.Scope -> scopeName(seg.field, seg.value)?.takeIf { it.isNotBlank() }?.let { "${seg.field}:${clip(it, 32)}" } ?: raw
            // A kind typed as a number draws under the name the registry has for it, so a screen
            // that seeds `kind:20` shows "kind:picture" — the only form a reader can check. Only
            // an exact one-token match is used: `kind:30312` must not draw as the wider `live`.
            is SearchSegment.Kind -> "kind:${seg.pseudoKind ?: KindRegistry.tokenize(seg.kinds).singleOrNull() ?: seg.alias}"
            // The quotes are punctuation once the span is a chip: the chip itself is what says
            // these words travel together. Dropping them is a length change, so it maps.
            is SearchSegment.Phrase -> seg.text.ifEmpty { seg.raw }
            else -> raw
        }

    /** The bech32 half of a `from:`/`to:` key token, or the whole thing when it is a bare npub. */
    private fun rawKeyOf(raw: String) = raw.substringAfter(':', raw)

    private fun shortBech32(value: String) = if (value.length <= 16) value else value.take(10) + "…" + value.takeLast(5)

    private fun clip(
        value: String,
        max: Int,
    ) = if (value.length <= max) value else value.take(max - 1) + "…"
}
