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
package com.vitorpamplona.amethyst.commons.nipsOnNostr

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.podcasts.fetchPreviewText
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One line of the nips repo's index: `- [NIP-04: Encrypted Direct Message](04.md) --- **unrecommended**: …`. */
@Immutable
data class OfficialNipEntry(
    val number: String,
    val title: String,
    /** Why the repo no longer recommends it (`deprecated in favor of NIP-17`), or null. */
    val unrecommended: String?,
)

/**
 * The official NIPs, read from the nips repo: its index (the README's list) and each NIP's text.
 * Fetched through the preview HTTP client, so the user's Tor settings for link previews apply, and
 * kept in memory for the session: a NIP changes rarely and the reader opens many of them.
 */
object OfficialNips {
    private val mutex = Mutex()
    private var index: List<OfficialNipEntry>? = null
    private val texts = LinkedHashMap<String, String>()
    private const val MAX_TEXTS = 64

    /** The text of NIP [number], or null when it can't be fetched (offline, no such NIP). */
    suspend fun text(
        number: String,
        http: IRoleBasedHttpClientBuilder,
    ): String? {
        mutex.withLock { texts[number] }?.let { return it }
        val text = http.fetchPreviewText(NipLinks.rawUrl(number))?.takeIf { it.isNotBlank() } ?: return null
        mutex.withLock {
            texts[number] = text
            if (texts.size > MAX_TEXTS) texts.remove(texts.keys.first())
        }
        return text
    }

    /** The repo's list of NIPs, or null when it can't be fetched. */
    suspend fun index(http: IRoleBasedHttpClientBuilder): List<OfficialNipEntry>? {
        mutex.withLock { index }?.let { return it }
        val readme = http.fetchPreviewText(NipLinks.INDEX_RAW_URL) ?: return null
        val parsed = parseIndex(readme).ifEmpty { return null }
        mutex.withLock { index = parsed }
        return parsed
    }

    private val ENTRY = Regex("^\\s*-\\s+(~~)?\\[NIP-([0-9A-Fa-f]{2}):\\s*(.+?)\\]\\(([0-9A-Fa-f]{2})\\.md\\)(.*?)(~~)?\\s*$")
    private val UNRECOMMENDED = Regex("\\*\\*unrecommended\\*\\*:?\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val INLINE_LINK = Regex("\\[([^\\]]*)\\]\\([^)]*\\)")

    /** The entries of the README's `## List` section, in its order. */
    fun parseIndex(readme: String): List<OfficialNipEntry> {
        val result = ArrayList<OfficialNipEntry>()
        var inList = false
        for (line in readme.lines()) {
            if (line.startsWith("## ")) {
                if (inList) break
                inList = line.removePrefix("## ").trim().equals("List", ignoreCase = true)
                continue
            }
            if (!inList) continue
            val match = ENTRY.matchEntire(line) ?: continue
            val number = NipLinks.normalize(match.groupValues[2]) ?: continue
            val title = match.groupValues[3].replace("`", "").trim()
            val reason =
                UNRECOMMENDED
                    .find(match.groupValues[5])
                    ?.groupValues
                    ?.get(1)
                    ?.replace(INLINE_LINK) { it.groupValues[1] }
                    ?.trim()
                    ?.ifBlank { null }
            val struck = match.groupValues[1].isNotEmpty()
            result.add(OfficialNipEntry(number, title, reason ?: if (struck) "" else null))
        }
        return result
    }
}
