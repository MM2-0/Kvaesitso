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
package com.vitorpamplona.amethyst.commons.ui.text

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.TextRange
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull

/**
 * Matches a complete Nostr mention token: `@npub1…`, `nostr:npub1…`,
 * `@nprofile1…`, `nostr:nprofile1…`. Shared with the app's `UrlUserTagOutputTransformation`
 * so the wedge it produces and the input-side guard below agree on what
 * counts as a mention.
 */
val MENTION_REGEX = Regex("(?:@|nostr:)(?:npub1[a-z0-9]{58}|nprofile1[a-z0-9]+)")

// A mention, or a bare `npub1…`/`nprofile1…` pasted without its prefix.
private val PROFILE_REGEX = Regex("(?:@|nostr:)?(?:npub1[a-z0-9]{58}|nprofile1[a-z0-9]+)")

/**
 * Keeps Nostr mentions atomic against IME edits that would only modify part of
 * the underlying bech32 (notably Microsoft SwiftKey, which re-enters word-edit
 * mode over a previously-committed display token and rewrites a single word of
 * a multi-word `@DisplayName`, leaving an orphan tail of the npub that no
 * longer matches the mention regex).
 *
 * Three change shapes are blocked and routed to atomic-collapse:
 *  - Partial overlap (the change's `originalRange` overlaps a mention but does
 *    not fully cover it).
 *  - Scope-exact replace (the change's `originalRange` matches the mention's
 *    range exactly and the replacement is non-empty — covers IMEs that
 *    fully-cover-replace a multi-word display token with one of its words).
 *
 * Neither is blocked when the edited text is a complete, decodable profile
 * reference: the author selected the chip and pasted another profile over it.
 *
 * Anything else passes through:
 *  - Pure delete that fully covers a mention (the user removed the chip).
 *  - A change whose range covers more than just the mention (select-all + type,
 *    select-paragraph + paste, etc.) — treated as deliberate broader edit.
 */
@OptIn(ExperimentalFoundationApi::class)
object MentionPreservingInputTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val changeCount = changes.changeCount
        if (changeCount == 0) return

        val original = originalText
        // Cheap gate — most keystrokes happen in mention-free text.
        if (!original.contains("npub1") && !original.contains("nprofile1")) return

        val touched =
            MENTION_REGEX.findAll(original).firstOrNull { match ->
                val mStart = match.range.first
                val mEndExclusive = match.range.last + 1
                (0 until changeCount).any { i ->
                    val origRange = changes.getOriginalRange(i)
                    val origStart = origRange.min
                    val origEnd = origRange.max
                    val overlaps = origStart < mEndExclusive && origEnd > mStart
                    val fullyCovers = origStart <= mStart && origEnd >= mEndExclusive
                    val isScopeExact = origStart == mStart && origEnd == mEndExclusive
                    val newRange = changes.getRange(i)
                    val isPureDelete = newRange.length == 0
                    overlaps && (!fullyCovers || (isScopeExact && !isPureDelete)) && !landsInProfile(newRange)
                }
            } ?: return

        revertAllChanges()
        val mEndExclusive = touched.range.last + 1
        val deleteEnd =
            if (mEndExclusive < length && asCharSequence()[mEndExclusive].isWhitespace()) {
                mEndExclusive + 1
            } else {
                mEndExclusive
            }
        replace(touched.range.first, deleteEnd, "")
    }

    /**
     * Swapping one mention for another (select the chip, paste the new npub) is a deliberate edit,
     * not an IME rewriting the chip's display name. The IME path trims the prefix the two share
     * (`nostr:npub1`), so the change only covers part of the old mention: judge it by the result,
     * a complete profile reference that decodes and contains the edit.
     */
    private fun TextFieldBuffer.landsInProfile(range: TextRange): Boolean {
        val text = asCharSequence()
        // A profile reference holds no whitespace: only the run around the edit can be one.
        var start = range.min
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        var end = range.max
        while (end < text.length && !text[end].isWhitespace()) end++
        if (start == end) return false

        return PROFILE_REGEX.findAll(text.subSequence(start, end)).any { match ->
            match.range.first + start <= range.min &&
                match.range.last + 1 + start >= range.max &&
                decodePublicKeyAsHexOrNull(match.value.removePrefix("@").removePrefix("nostr:")) != null
        }
    }
}
