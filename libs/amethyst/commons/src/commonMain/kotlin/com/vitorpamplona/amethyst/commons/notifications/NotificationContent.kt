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
package com.vitorpamplona.amethyst.commons.notifications

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip71Video.selectVideoTrack
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoEvent
import com.vitorpamplona.quartz.utils.Log
import kotlin.coroutines.cancellation.CancellationException

/**
 * Content-extraction helpers shared by the per-kind notification renderers:
 * decrypting private payloads, pulling a clean one-line excerpt, and resolving
 * the media URL to show as a notification's big picture.
 */
object NotificationContent {
    /** First non-blank line of [content], trimmed to [max] chars, or "" if none. */
    fun excerpt(
        content: String?,
        max: Int = 280,
    ): String =
        content
            ?.split("\n")
            ?.firstOrNull { it.isNotBlank() }
            ?.take(max)
            ?: ""

    /**
     * The result of [resolveMentions]: the excerpt with every `nostr:npub` /
     * `nostr:nprofile` token swapped for the cited user's `@DisplayName`, plus the
     * [User]s that were cited so a renderer can add them to its enrichment window
     * and re-render as their metadata loads.
     */
    data class ResolvedText(
        val text: String,
        val citedUsers: List<User>,
    )

    /**
     * Like [excerpt], but rewrites inline user mentions to readable names: each
     * `nostr:npub1…` / `nostr:nprofile1…` (optionally `@`-prefixed) becomes
     * `@<best display name>` for the cited user, and that user is returned in
     * [ResolvedText.citedUsers]. Event/address references (`nevent`, `note`,
     * `naddr`, …) are left untouched — they aren't people and have no name to show.
     *
     * Called from the build closure on every re-render, so as a cited user's
     * kind:0 arrives the notification text updates in place from `@npub1abc…` to
     * `@RealName`.
     */
    fun resolveMentions(
        content: String?,
        max: Int = 280,
    ): ResolvedText {
        val line =
            content
                ?.split("\n")
                ?.firstOrNull { it.isNotBlank() }
                ?: return ResolvedText("", emptyList())

        // Cheap opt-out: no mention tokens means no work and no allocations.
        if (!line.contains("npub1", ignoreCase = true) && !line.contains("nprofile1", ignoreCase = true)) {
            return ResolvedText(line.take(max), emptyList())
        }

        val cited = mutableListOf<User>()
        val rewritten =
            Nip19Parser.nip19regex.replace(line) { match ->
                val type = match.groups[3]?.value ?: match.groups[5]?.value
                val key = match.groups[4]?.value ?: match.groups[6]?.value
                val trailing = match.groups[7]?.value ?: ""

                val hex =
                    when (val entity = Nip19Parser.parseComponents(type ?: "", key, null)?.entity) {
                        is NPub -> entity.hex
                        is NProfile -> entity.hex
                        else -> null
                    }

                if (hex != null) {
                    val user = LocalCache.getOrCreateUser(hex)
                    cited.add(user)
                    "@${user.toBestDisplayName()}$trailing"
                } else {
                    // nevent / note / naddr / parse failure — leave as-is.
                    match.value
                }
            }

        return ResolvedText(rewritten.take(max), cited)
    }

    /**
     * The result of [renderNoteText]: [ResolvedText] plus [imageUrl], the first
     * inline image link found anywhere in the note. When present, a renderer shows
     * it as the notification's big picture and the link is stripped from [text] —
     * so a mention whose body is "look at this https://…/cat.jpg" shows the photo
     * instead of the raw URL.
     */
    data class RenderedText(
        val text: String,
        val citedUsers: List<User>,
        val imageUrl: String?,
    )

    private val whitespace = Regex("\\s+")
    private val trailingPunctuation = charArrayOf('.', ',', ')', '(', '!', '?', ';', ':', '"', '\'')

    /**
     * The first `http(s)` image URL in [content] (anywhere, not only the first
     * line — images are usually on their own line), or null. Trailing sentence
     * punctuation is trimmed so "…/cat.jpg." still resolves. Videos are ignored:
     * Coil can't load them as a still, so their link stays in the text.
     */
    fun firstImageUrl(content: String?): String? {
        if (content == null) return null
        content.split(whitespace).forEach { raw ->
            if (!raw.startsWith("http://", ignoreCase = true) && !raw.startsWith("https://", ignoreCase = true)) {
                return@forEach
            }
            val url = raw.trimEnd(*trailingPunctuation)
            if (RichTextParser.isImageUrl(url)) return url
        }
        return null
    }

    /**
     * [resolveMentions] plus inline-image extraction: resolves `@npub` mentions,
     * pulls the first image link out as [RenderedText.imageUrl], and removes that
     * link from the excerpt text (collapsing the whitespace it leaves behind).
     */
    fun renderNoteText(
        content: String?,
        max: Int = 280,
    ): RenderedText {
        val resolved = resolveMentions(content, max = Int.MAX_VALUE)
        val imageUrl = firstImageUrl(content)

        val text =
            if (imageUrl != null) {
                resolved.text
                    .replace(imageUrl, "")
                    .replace(whitespace, " ")
                    .trim()
                    .take(max)
            } else {
                resolved.text.take(max)
            }

        return RenderedText(text, resolved.citedUsers, imageUrl)
    }

    /**
     * A private zap request is signed by a one-time key; the real zapper and message are encrypted
     * for the zapped author. Decrypts them when [signer] is that recipient, otherwise (or when the
     * signer can't) returns the request as-is.
     */
    suspend fun decryptZapContentAuthor(
        event: ZapRequestEvent,
        signer: NostrSigner,
    ): Event? {
        if (!event.isPrivateZap() || signer.pubKey !in event.zappedAuthor()) return event
        return try {
            signer.decryptZapEvent(event)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("NotificationContent", "Could not decrypt private zap ${event.id}", e)
            event
        }
    }

    suspend fun decryptContent(
        note: Note,
        signer: NostrSigner,
    ): String? =
        when (val event = note.event) {
            is EncryptedDmEvent -> event.decryptContent(signer)
            is ZapRequestEvent -> decryptZapContentAuthor(event, signer)?.content
            else -> event?.content
        }

    /**
     * The primary image/thumbnail URL to render as a notification's big picture,
     * or null if the event carries no displayable media. Pictures use their first
     * imeta url; videos prefer the poster-frame `image`, falling back to the video
     * url only when no poster is present.
     */
    fun mediaImageUrl(event: Event?): String? =
        when (event) {
            is PictureEvent -> event.imetaTags().firstOrNull()?.url
            is VideoEvent -> {
                // selectVideoTrack fills the poster in from sibling imetas, so a ladder that
                // declares `image` on only some of its rungs still gets a big picture instead of
                // falling back to a playlist URL Coil cannot decode.
                val meta = event.selectVideoTrack()
                meta?.image?.firstOrNull() ?: meta?.url
            }
            else -> null
        }
}
