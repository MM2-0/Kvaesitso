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
package com.vitorpamplona.amethyst.commons.model.privateChats

import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub

/**
 * The words a one-line preview puts in place of things that only make sense rendered: a photo, a
 * video, a quoted note. Resolved from string resources by the UI and handed in, so the cleanup itself
 * stays a pure function.
 */
data class ChatPreviewLabels(
    val photo: String,
    val video: String,
    val note: String,
)

/**
 * Turns a raw message body into what a Messages-list preview line should say.
 *
 * The bubble renders a message; the preview line only has one line of plain text, so anything that
 * relies on rendering comes out as machine text there — `nostr:nevent1qqs…`, a 90-char blob URL,
 * markdown backticks. Those strings are the harshest thing on a list screen, so the preview swaps
 * each for what a reader would call it:
 *
 * - `nostr:npub…` / `nprofile…` → `@Name` (via [nameOf]; a short npub when the name is unknown)
 * - `nostr:nevent…` / `note…` / `naddr…` / `nembed…` → [ChatPreviewLabels.note]
 * - an image URL → [ChatPreviewLabels.photo], a video URL → [ChatPreviewLabels.video],
 *   any other URL → its host
 * - markdown `[label](url)` → `label`; emphasis markers, backticks, heading and quote markers dropped
 * - line breaks and runs of spaces → one space, so the line shows as much of the message as it can
 */
object ChatPreviewText {
    private val whitespace = Regex("\\s+")
    private val markdownLink = Regex("\\[([^\\]\\n]+)]\\((https?://[^)\\s]+)\\)")
    private val url = Regex("https?://[^\\s<>\"]+", RegexOption.IGNORE_CASE)
    private val lineMarkers = Regex("(?m)^[ \\t]*(#{1,6}[ \\t]+|>[ \\t]?)")
    private val emphasis = Regex("\\*\\*|__|~~|`")
    private val trailingPunctuation = charArrayOf('.', ',', ')', '!', '?', ';', ':', '"', '\'')
    private val nip19Prefixes = arrayOf("npub1", "nprofile1", "nevent1", "note1", "naddr1", "nembed1")

    fun tidy(
        text: String,
        labels: ChatPreviewLabels,
        nameOf: (HexKey) -> String?,
    ): String {
        if (text.isEmpty()) return text

        var out = text
        if (out.contains("](")) out = markdownLink.replace(out) { it.groupValues[1] }
        if (out.contains('#') || out.contains('>')) out = lineMarkers.replace(out, "")
        if (nip19Prefixes.any { out.contains(it, ignoreCase = true) }) out = replaceNip19(out, labels, nameOf)
        if (out.contains("://")) out = replaceUrls(out, labels)
        out = emphasis.replace(out, "")

        return whitespace.replace(out, " ").trim()
    }

    private fun replaceNip19(
        text: String,
        labels: ChatPreviewLabels,
        nameOf: (HexKey) -> String?,
    ): String =
        Nip19Parser.nip19regex.replace(text) { match ->
            val type = match.groups[3]?.value ?: match.groups[5]?.value
            val key = match.groups[4]?.value ?: match.groups[6]?.value
            val trailing = match.groups[7]?.value ?: ""

            when (val entity = Nip19Parser.parseComponents(type ?: "", key, null)?.entity) {
                is NPub -> "@${nameOf(entity.hex) ?: shortBech32(type + key)}$trailing"
                is NProfile -> "@${nameOf(entity.hex) ?: shortBech32(type + key)}$trailing"
                is NEvent, is NNote, is NAddress, is NEmbed -> "${labels.note}$trailing"
                else -> match.value
            }
        }

    private fun replaceUrls(
        text: String,
        labels: ChatPreviewLabels,
    ): String =
        url.replace(text) { match ->
            val raw = match.value
            val link = raw.trimEnd(*trailingPunctuation)
            val trailing = raw.substring(link.length)
            val replacement =
                when {
                    RichTextParser.isImageUrl(link) -> labels.photo
                    RichTextParser.isVideoUrl(link) -> labels.video
                    else -> hostOf(link) ?: link
                }
            replacement + trailing
        }

    private fun hostOf(link: String): String? {
        val afterScheme = link.substringAfter("://", "")
        val host =
            afterScheme
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .substringAfter('@')
        return host.removePrefix("www.").ifBlank { null }
    }

    private fun shortBech32(bech32: String): String = if (bech32.length > 16) bech32.take(10) + "…" + bech32.takeLast(4) else bech32
}
