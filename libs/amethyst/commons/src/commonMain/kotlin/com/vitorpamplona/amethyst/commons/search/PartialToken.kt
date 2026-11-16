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
package com.vitorpamplona.amethyst.commons.search

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.search.calendar.DateField
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub

/**
 * A prefixed token the caret is sitting inside, with offsets back into the text so a pick can
 * splice the finished token in without re-reading the field.
 *
 * [complete] says the token already names something real, which is how a picker knows to stand
 * down rather than offer to finish what is already finished.
 */
@Immutable
data class PartialToken(
    val field: String,
    val partial: String,
    val start: Int,
    val end: Int,
    val complete: Boolean,
)

/** Which picker the caret's position calls for, if any. */
@Immutable
sealed interface ActivePicker {
    val token: PartialToken

    /** `from:`/`to:` — the people picker. */
    @Immutable
    data class People(
        override val token: PartialToken,
        val keyField: KeyField,
    ) : ActivePicker

    /** `since:`/`until:` — the calendar. */
    @Immutable
    data class Calendar(
        override val token: PartialToken,
        val dateField: DateField,
    ) : ActivePicker

    /** `group:` — the NIP-29 group picker. */
    @Immutable
    data class Group(
        override val token: PartialToken,
    ) : ActivePicker

    /** `kind:` — the kind vocabulary, which is a constant rather than something to look up. */
    @Immutable
    data class Kind(
        override val token: PartialToken,
    ) : ActivePicker
}

/**
 * Which half-written token the caret is in, and therefore which picker belongs under the field.
 *
 * Everything here is derived from the text and the caret, never from whatever picker happens to
 * be on screen: a field can be blurred and refocused with a token still half-written, and coming
 * back to it must pick up exactly where it left off.
 */
object PartialTokens {
    private val MENTION_PREFIXES = listOf("from:", "to:")
    private val DATE_PREFIXES = listOf("since:", "until:")
    private val GROUP_PREFIXES = listOf("group:")
    private val KIND_PREFIXES = listOf("kind:")

    /** A `to:` that has begun a NIP-19 pointer names no person, so the people picker stands down. */
    private val POINTER_PREFIXES = listOf("note1", "nevent1", "naddr1")

    /**
     * At most one picker can be open, and the order here is the order the prefixes are tried:
     * the calendar first (its prefixes are unambiguous), then kinds, then groups, then people.
     */
    fun activePicker(
        text: String,
        caret: Int,
    ): ActivePicker? {
        dateAt(text, caret)?.let { token ->
            if (token.complete) return null
            return DateField.of(token.field)?.let { ActivePicker.Calendar(token, it) }
        }
        kindAt(text, caret)?.let { token ->
            if (token.complete) return null
            return ActivePicker.Kind(token)
        }
        // Never complete: `group:gen` is a plausible id and a prefix of `general`, so only a
        // space ends a group token — which means the picker stays up until the reader leaves it.
        groupAt(text, caret)?.let { return ActivePicker.Group(it) }
        mentionAt(text, caret)?.let { token ->
            if (token.complete) return null
            return KeyField.of(token.field)?.let { ActivePicker.People(token, it) }
        }
        return null
    }

    /** The `from:`/`to:` token the caret is inside — what the people picker asks. */
    fun mentionAt(
        text: String,
        caret: Int,
    ): PartialToken? {
        val token = partialAt(text, caret, MENTION_PREFIXES) ?: return null
        if (POINTER_PREFIXES.any { token.partial.startsWith(it, ignoreCase = true) }) return null
        return token.copy(complete = isKey(token.partial))
    }

    /** The `since:`/`until:` token the caret is inside; complete once it is a real day. */
    fun dateAt(
        text: String,
        caret: Int,
    ): PartialToken? {
        val token = partialAt(text, caret, DATE_PREFIXES) ?: return null
        return token.copy(complete = SearchDate.parse(token.partial) != null)
    }

    /**
     * The `kind:` token the caret is inside; complete once it names a kind exactly.
     *
     * Unlike a group id, the kind vocabulary is closed, so an exact match really is finished and
     * the picker stands down — there is nothing left to offer for `kind:article`.
     */
    fun kindAt(
        text: String,
        caret: Int,
    ): PartialToken? {
        val token = partialAt(text, caret, KIND_PREFIXES) ?: return null
        return token.copy(complete = KindRegistry.isExactAlias(token.partial))
    }

    /** The `group:` token the caret is inside, which the group picker asks. */
    fun groupAt(
        text: String,
        caret: Int,
    ): PartialToken? = partialAt(text, caret, GROUP_PREFIXES)

    /**
     * Is this exactly one finished key? An npub only: hex pasted after `from:` stays unfinished,
     * so the picker resolves it and writes the npub back in its place.
     */
    fun isKey(value: String?): Boolean {
        val v = value?.trim() ?: return false
        if (!v.startsWith("npub1")) return false
        return when (Nip19Parser.uriToRoute(v)?.entity) {
            is NPub, is NProfile -> true
            else -> false
        }
    }

    /**
     * The token whose prefix ends before [caret] and whose value runs up to it, or null. The
     * caret must be at the end of the value: standing in the middle of a finished token is
     * editing it, not writing it, and a picker there would rewrite text the reader is reading.
     */
    private fun partialAt(
        text: String,
        caret: Int,
        prefixes: List<String>,
    ): PartialToken? {
        val end = caret.coerceIn(0, text.length)
        // Anything but whitespace after the caret means the caret is inside a word, not at the
        // end of one; the token it would name is somebody else's.
        if (end < text.length && !text[end].isWhitespace()) return null

        val head = text.substring(0, end)
        val wordStart = head.indexOfLast { it.isWhitespace() } + 1
        val word = head.substring(wordStart)
        val prefix = prefixes.firstOrNull { word.regionMatches(0, it, 0, it.length, ignoreCase = true) } ?: return null
        return PartialToken(
            field = prefix.dropLast(1).lowercase(),
            partial = word.substring(prefix.length),
            start = wordStart,
            end = end,
            complete = false,
        )
    }

    /**
     * The text with [token] spliced over the partial the caret is in, and where the caret lands
     * after.
     *
     * The caret always ends up past a separating space — the one already there, or one added —
     * because a token is finished the moment it is picked: leaving the caret against it would
     * make the next character typed extend the token rather than start a new word.
     */
    fun replaceToken(
        text: String,
        at: PartialToken,
        token: String,
    ): Pair<String, Int> {
        val end = at.end.coerceAtMost(text.length)
        val tail = if (text.substring(end).startsWith(" ")) "" else " "
        val next = text.substring(0, at.start) + token + tail + text.substring(end)
        return next to (at.start + token.length + 1)
    }
}
