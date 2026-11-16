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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.description
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.image
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip51Lists.tags.DescriptionTag
import com.vitorpamplona.quartz.nip51Lists.tags.ImageTag
import com.vitorpamplona.quartz.nip51Lists.tags.NameTag
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * NIP-30 kind:30030 emoji packs owned by the signer: build and sign; the caller publishes.
 *
 * Emojis live either in the public `emoji` tags or, NIP-51 style, NIP-44 encrypted in the
 * `content` ("private" emojis). A shortcode is unique within each of those two buckets: adding
 * one that is already there is skipped or replaces it in place, never appended a second time.
 * The same shortcode may still appear once publicly and once privately.
 */
object EmojiPackActions {
    enum class OnConflict { SKIP, REPLACE }

    /**
     * What [addEmojis] did, by shortcode. [event] is null when nothing changed. [skipped] holds
     * shortcodes already in the pack (with [OnConflict.SKIP], or with the same url) and repeats
     * within the request; [invalid] those NIP-30 does not allow.
     */
    class AddResult(
        val event: EmojiPackEvent?,
        val added: List<String>,
        val replaced: List<String>,
        val skipped: List<String>,
        val invalid: List<String>,
    )

    /** What [removeEmojis] did; [event] is null when none of the shortcodes was in the pack. */
    class RemoveResult(
        val event: EmojiPackEvent?,
        val removed: List<String>,
        val missing: List<String>,
    )

    @OptIn(ExperimentalUuidApi::class)
    suspend fun create(
        signer: NostrSigner,
        title: String,
        description: String? = null,
        image: String? = null,
        dTag: String = Uuid.random().toString(),
    ): EmojiPackEvent {
        require(title.isNotBlank()) { "An emoji pack needs a title" }
        val template =
            EmojiPackEvent.build(name = title, dTag = dTag) {
                if (!description.isNullOrBlank()) description(description)
                if (!image.isNullOrBlank()) image(image)
            }
        return signer.sign(template)
    }

    /** Replaces the title, description and cover image of [pack]; null or blank removes the field. */
    suspend fun updateMetadata(
        signer: NostrSigner,
        pack: EmojiPackEvent,
        title: String,
        description: String?,
        image: String?,
    ): EmojiPackEvent {
        require(title.isNotBlank()) { "An emoji pack needs a title" }
        val metadataTags = setOf(NameTag.TAG_NAME, TitleTag.TAG_NAME, DescriptionTag.TAG_NAME, ImageTag.TAG_NAME)
        val kept = pack.tags.filter { it.isEmpty() || it[0] !in metadataTags }
        val metadata =
            listOfNotNull(
                TitleTag.assemble(title),
                description?.takeIf { it.isNotBlank() }?.let { DescriptionTag.assemble(it) },
                image?.takeIf { it.isNotBlank() }?.let { ImageTag.assemble(it) },
            )
        // Metadata goes right after the `d` tag, where EmojiPackEvent.build puts it.
        val dIndex = kept.indexOfFirst { it.isNotEmpty() && it[0] == "d" }
        val newTags = kept.toMutableList().apply { addAll(dIndex + 1, metadata) }.toTypedArray()
        return signer.sign(nextCreatedAt(pack), pack.kind, newTags, pack.content)
    }

    suspend fun addEmojis(
        signer: NostrSigner,
        pack: EmojiPackEvent,
        emojis: List<EmojiUrlTag>,
        isPrivate: Boolean,
        onConflict: OnConflict = OnConflict.SKIP,
    ): AddResult {
        val current = if (isPrivate) privateTagsOf(signer, pack) else pack.tags

        val tags = current.toMutableList()
        val indexByCode = HashMap<String, Int>()
        tags.forEachIndexed { index, tag -> EmojiUrlTag.parseCode(tag)?.let { indexByCode.getOrPut(it) { index } } }

        val added = mutableListOf<String>()
        val replaced = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val invalid = mutableListOf<String>()
        val seen = HashSet<String>()

        for (emoji in emojis) {
            when {
                !EmojiUrlTag.isValidShortcode(emoji.code) || emoji.url.isBlank() -> invalid.add(emoji.code)
                !seen.add(emoji.code) -> skipped.add(emoji.code)
                else -> {
                    val existing = indexByCode[emoji.code]
                    if (existing == null) {
                        indexByCode[emoji.code] = tags.size
                        tags.add(emoji.toTagArray())
                        added.add(emoji.code)
                    } else if (onConflict == OnConflict.REPLACE && EmojiUrlTag.parse(tags[existing]) != emoji) {
                        tags[existing] = emoji.toTagArray()
                        replaced.add(emoji.code)
                    } else {
                        skipped.add(emoji.code)
                    }
                }
            }
        }

        if (added.isEmpty() && replaced.isEmpty()) return AddResult(null, added, replaced, skipped, invalid)

        val event = signWith(signer, pack, tags.toTypedArray(), isPrivate)
        return AddResult(event, added, replaced, skipped, invalid)
    }

    suspend fun removeEmojis(
        signer: NostrSigner,
        pack: EmojiPackEvent,
        shortcodes: Collection<String>,
        isPrivate: Boolean,
    ): RemoveResult {
        val current = if (isPrivate) privateTagsOf(signer, pack) else pack.tags
        val toRemove = shortcodes.toSet()

        val removed = LinkedHashSet<String>()
        val kept =
            current.filter { tag ->
                val code = EmojiUrlTag.parseCode(tag)
                if (code != null && code in toRemove) {
                    removed.add(code)
                    false
                } else {
                    true
                }
            }
        val missing = toRemove.filter { it !in removed }

        if (removed.isEmpty()) return RemoveResult(null, emptyList(), missing)

        val event = signWith(signer, pack, kept.toTypedArray(), isPrivate)
        return RemoveResult(event, removed.toList(), missing)
    }

    private suspend fun privateTagsOf(
        signer: NostrSigner,
        pack: EmojiPackEvent,
    ): TagArray = pack.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()

    /** Signs [pack] with [newTags] as its public tags, or as its encrypted private tags. */
    private suspend fun signWith(
        signer: NostrSigner,
        pack: EmojiPackEvent,
        newTags: TagArray,
        isPrivate: Boolean,
    ): EmojiPackEvent =
        if (isPrivate) {
            signer.sign(nextCreatedAt(pack), pack.kind, pack.tags, PrivateTagsInContent.encryptNip44(newTags, signer))
        } else {
            signer.sign(nextCreatedAt(pack), pack.kind, newTags, pack.content)
        }

    /**
     * Strictly after [pack]: two edits within the same second would otherwise tie on `created_at`,
     * and relays keep the lower id of a tie, which may be the older version.
     */
    private fun nextCreatedAt(pack: EmojiPackEvent) = maxOf(TimeUtils.now(), pack.createdAt + 1)
}
