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
package com.vitorpamplona.amethyst.commons.devtools

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils

/** A relative `since` the builder offers as one tap. */
enum class SinceWindow(
    val seconds: Long,
) {
    HOUR(TimeUtils.ONE_HOUR.toLong()),
    DAY(TimeUtils.ONE_DAY.toLong()),
    WEEK(TimeUtils.ONE_WEEK.toLong()),
    MONTH(TimeUtils.ONE_MONTH.toLong()),
}

enum class FilterField {
    KINDS,
    AUTHORS,
    IDS,
    TAG,
    LIMIT,
}

/**
 * The relay console's REQ/COUNT builder, as typed: free text per field, so the form can hold
 * half-written input. Lists split on commas or whitespace. Keys and ids take hex or any NIP-19
 * form a developer has on the clipboard (npub, nprofile, note, nevent), and a `#p`/`#e` value
 * written as npub/note is converted too.
 */
@Immutable
data class FilterDraft(
    val kinds: String = "",
    val authors: String = "",
    val ids: String = "",
    val tagName: String = "",
    val tagValues: String = "",
    val since: SinceWindow? = null,
    val limit: String = "",
    val search: String = "",
) {
    @Immutable
    sealed interface Result {
        data class Ok(
            val filter: Filter,
        ) : Result

        data class Invalid(
            val field: FilterField,
            val token: String,
        ) : Result
    }

    fun build(now: Long = TimeUtils.now()): Result {
        val kinds = tokens(kinds).map { it.toIntOrNull()?.takeIf { k -> k in 0..65535 } ?: return Result.Invalid(FilterField.KINDS, it) }
        val authors = tokens(authors).map { pubKeyOf(it) ?: return Result.Invalid(FilterField.AUTHORS, it) }
        val ids = tokens(ids).map { eventIdOf(it) ?: return Result.Invalid(FilterField.IDS, it) }

        val name = tagName.trim().removePrefix("#")
        val values = tokens(tagValues).map { valueFor(name, it) }
        if (name.isEmpty() != values.isEmpty() || name.length > 1) {
            return Result.Invalid(FilterField.TAG, tagName.ifBlank { tagValues })
        }

        val limit =
            limit.trim().ifEmpty { null }?.let {
                it.toIntOrNull()?.takeIf { l -> l >= 0 } ?: return Result.Invalid(FilterField.LIMIT, it)
            }

        return Result.Ok(
            Filter(
                ids = ids.ifEmpty { null },
                authors = authors.ifEmpty { null },
                kinds = kinds.ifEmpty { null },
                tags = if (name.isNotEmpty()) mapOf(name to values) else null,
                since = since?.let { now - it.seconds },
                limit = limit,
                search = search.trim().ifEmpty { null },
            ),
        )
    }

    companion object {
        private val SEPARATORS = Regex("[\\s,]+")

        private fun tokens(text: String) = text.split(SEPARATORS).filter { it.isNotBlank() }

        private fun isHexKey(text: String) = text.length == 64 && Hex.isHex(text)

        fun pubKeyOf(text: String): HexKey? {
            if (isHexKey(text)) return text.lowercase()
            return when (val entity = Nip19Parser.uriToRoute(text)?.entity) {
                is NPub -> entity.hex
                is NProfile -> entity.hex
                else -> null
            }
        }

        fun eventIdOf(text: String): HexKey? {
            if (isHexKey(text)) return text.lowercase()
            return when (val entity = Nip19Parser.uriToRoute(text)?.entity) {
                is NNote -> entity.hex
                is NEvent -> entity.hex
                else -> null
            }
        }

        /** `#p` and `#e` values convert from NIP-19; `#a` from naddr; anything else is taken as typed. */
        private fun valueFor(
            tag: String,
            text: String,
        ): String =
            when (tag) {
                "p" -> pubKeyOf(text) ?: text
                "e" -> eventIdOf(text) ?: text
                "a" -> (Nip19Parser.uriToRoute(text)?.entity as? NAddress)?.let { "${it.kind}:${it.author}:${it.dTag}" } ?: text
                else -> text
            }
    }
}
