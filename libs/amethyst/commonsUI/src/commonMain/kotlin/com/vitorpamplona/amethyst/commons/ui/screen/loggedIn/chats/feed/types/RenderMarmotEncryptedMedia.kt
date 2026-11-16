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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.marmotGroups.marmotGroupIdFor
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.could_not_decrypt_the_message
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.EncryptedMediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.EncryptedMediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.platform.RenderAudioWithWaveform
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.marmot.appComponents.EncryptedMediaPolicyV2
import com.vitorpamplona.marmot.appComponents.EncryptedMediaReferenceV2
import com.vitorpamplona.marmot.appComponents.EncryptedMediaV2
import com.vitorpamplona.marmot.appComponents.EncryptedMediaV2Cipher
import com.vitorpamplona.marmot.foundation.appEvents.MarmotCustomEmoji
import com.vitorpamplona.marmot.mip04EncryptedMedia.Mip04Cipher
import com.vitorpamplona.marmot.mip04EncryptedMedia.Mip04MediaMeta
import com.vitorpamplona.marmot.mip04EncryptedMedia.toMip04MediaMeta
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import kotlinx.collections.immutable.persistentListOf

/**
 * Check if a Note's event has MIP-04 encrypted media imeta tags.
 */
fun hasMip04Media(event: Event?): Boolean {
    if (event == null) return false
    val imetas = event.imetas()
    return imetas.any { it.toMip04MediaMeta() != null }
}

/**
 * The `encrypted-media-v2` reference on an event, or null.
 *
 * Deliberately NOT routed through [imetas]: NIP-92 parsing anchors on a `url`
 * field, and a v2 tag has no `url` — it carries `locator <kind> <value>` pairs
 * instead, because the same ciphertext may live at several places and none of
 * them is privileged. Reading v2 through the NIP-92 parser silently produced
 * nothing, so a v2 attachment rendered as its caption with the image missing
 * and no error.
 */
fun encryptedMediaV2Of(event: Event?): EncryptedMediaReferenceV2? {
    if (event == null) return null
    // Every chat row asks on every recomposition, and almost none has an attachment.
    if (event.tags.none { it.size > 1 && it[0] == "imeta" }) return null
    // A custom emoji's picture is an attachment too (MDK 0.12), named by an
    // `emoji` tag; it is artwork for the text, drawn inline, not the file the
    // message sends. A message whose only attachments are emoji is text.
    val emojiArtwork = MarmotCustomEmoji.matchedEmojiAttachments(event).keys
    return MarmotCustomEmoji.attachments(event).firstOrNull { MarmotCustomEmoji.firstLocator(it) !in emojiArtwork }
}

fun hasEncryptedMediaV2(event: Event?): Boolean = encryptedMediaV2Of(event) != null

/**
 * Renders an `encrypted-media-v2` attachment.
 *
 * Same shape as the MIP-04 path — register a cipher against the URL the blob
 * will be fetched from, then hand a [BaseMediaContent] to [ZoomableContentView]
 * — with two differences that come from the format:
 *
 * - The URL comes from the FIRST `blossom-v1` locator rather than a `url`
 *   field. Later locators are mirrors of the same ciphertext; trying them in
 *   turn would need the download layer to report failure back here, which it
 *   does not, so this renders the first and leaves fallback for when it can.
 * - The nonce and plaintext hash come off the tag, so the cipher is built
 *   through its receiving constructor.
 */
@Composable
fun RenderEncryptedMediaV2(
    note: Note,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event ?: return
    val reference = remember(event) { encryptedMediaV2Of(event) }
    val url =
        remember(reference) {
            reference
                ?.locators
                ?.firstOrNull { it.kind == EncryptedMediaPolicyV2.INITIAL_LOCATOR_KIND }
                ?.value
        }

    val nostrGroupId = remember(note) { findGroupIdForNote(note, accountViewModel) }
    val secretLookup = rememberMessageMediaSecret(note, nostrGroupId, accountViewModel)
    if (secretLookup is MessageMediaSecret.Loading) {
        MediaKeyLoadingSpace(reference?.dim)
        return
    }
    val mediaSecret = (secretLookup as? MessageMediaSecret.Found)?.secret

    if (reference == null || url == null || mediaSecret == null) {
        RenderDecryptionError(note, bgColor, accountViewModel, nav)
        return
    }

    val cipher = remember(reference, mediaSecret) { EncryptedMediaV2Cipher(mediaSecret, reference) }
    accountViewModel.account.encryptionKeyCache.add(url, cipher, reference.mediaType)

    val description = event.alt()

    // Audio would otherwise fall to the video branch below and play on a
    // picture-less video surface. MIP-04 carries no waveform, so the player
    // shows its transport without bars — still the voice UI rather than a
    // video one.
    if (RichTextParser.isAudioContent(reference.mediaType, url)) {
        RenderAudioWithWaveform(
            mediaUrl = url,
            title = description,
            mimeType = reference.mediaType,
            waveform = null,
            note = note,
            accountViewModel = accountViewModel,
            nav = nav,
        )
        return
    }

    val dim = reference.dim?.let { DimensionTag.parse(it) }
    val content by remember(reference) {
        mutableStateOf<BaseMediaContent>(
            if (reference.mediaType.startsWith("image/")) {
                EncryptedMediaUrlImage(
                    url = url,
                    description = description,
                    hash = reference.plaintextSha256.toHexKey(),
                    dim = dim,
                    uri = note.toNostrUri(),
                    mimeType = reference.mediaType,
                    encryptionAlgo = EncryptedMediaV2.VERSION,
                    encryptionKey = mediaSecret,
                    encryptionNonce = reference.nonce,
                    thumbhash = reference.thumbhash,
                )
            } else {
                EncryptedMediaUrlVideo(
                    url = url,
                    description = description,
                    hash = reference.plaintextSha256.toHexKey(),
                    dim = dim,
                    uri = note.toNostrUri(),
                    authorName = note.author?.toBestDisplayName(),
                    mimeType = reference.mediaType,
                    encryptionAlgo = EncryptedMediaV2.VERSION,
                    encryptionKey = mediaSecret,
                    encryptionNonce = reference.nonce,
                    thumbhash = reference.thumbhash,
                )
            },
        )
    }

    ZoomableContentView(
        content,
        persistentListOf(content),
        roundedCorner = true,
        contentScale = ContentScale.FillWidth,
        accountViewModel,
    )
}

/**
 * Renders MIP-04 encrypted media in a Marmot group chat message.
 *
 * Parses the imeta tags from the event, derives the decryption cipher
 * from the MLS exporter secret, registers it in the encryption key cache
 * so the [EncryptedBlobInterceptor] can decrypt on download, and displays
 * the media using the standard [ZoomableContentView].
 */
@Composable
fun RenderMarmotEncryptedMedia(
    note: Note,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event ?: return
    val imetas = remember(event) { event.imetas() }
    val mip04Meta = remember(imetas) { imetas.firstNotNullOfOrNull { it.toMip04MediaMeta() } }

    if (mip04Meta == null) {
        RenderDecryptionError(note, bgColor, accountViewModel, nav)
        return
    }

    // Find the nostrGroupId via the reverse index in MarmotGroupList
    val nostrGroupId = remember(note) { findGroupIdForNote(note, accountViewModel) }
    val secretLookup = rememberMessageMediaSecret(note, nostrGroupId, accountViewModel)
    if (secretLookup is MessageMediaSecret.Loading) {
        MediaKeyLoadingSpace(mip04Meta.dimensions)
        return
    }
    val exporterSecret = (secretLookup as? MessageMediaSecret.Found)?.secret

    if (exporterSecret == null) {
        RenderDecryptionError(note, bgColor, accountViewModel, nav)
        return
    }

    RenderMip04Content(note, mip04Meta, exporterSecret, bgColor, accountViewModel, nav)
}

@Composable
private fun RenderMip04Content(
    note: Note,
    meta: Mip04MediaMeta,
    exporterSecret: ByteArray,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Register the MIP-04 cipher in the encryption key cache so the
    // EncryptedBlobInterceptor can decrypt the downloaded blob.
    val cipher =
        remember(meta) {
            Mip04Cipher(
                exporterSecret = exporterSecret,
                nonce = meta.nonceBytes,
                originalFileHash = meta.originalFileHashBytes,
                mimeType = meta.mimeType,
                filename = meta.filename,
            )
        }
    accountViewModel.account.encryptionKeyCache.add(meta.url, cipher, meta.mimeType)

    val description = note.event?.alt()

    // Same reason as the reference path above.
    if (RichTextParser.isAudioContent(meta.mimeType, meta.url)) {
        RenderAudioWithWaveform(
            mediaUrl = meta.url,
            title = description,
            mimeType = meta.mimeType,
            waveform = null,
            note = note,
            accountViewModel = accountViewModel,
            nav = nav,
        )
        return
    }

    val isImage = meta.mimeType.startsWith("image/") || RichTextParser.isImageUrl(meta.url)
    val dim = meta.dimensions?.let { DimensionTag.parse(it) }

    val content by remember(meta) {
        mutableStateOf<BaseMediaContent>(
            if (isImage) {
                EncryptedMediaUrlImage(
                    url = meta.url,
                    description = description,
                    hash = meta.originalFileHash,
                    blurhash = meta.blurhash,
                    dim = dim,
                    uri = note.toNostrUri(),
                    mimeType = meta.mimeType,
                    encryptionAlgo = meta.version,
                    encryptionKey = exporterSecret,
                    encryptionNonce = meta.nonceBytes,
                    thumbhash = meta.thumbhash,
                )
            } else {
                EncryptedMediaUrlVideo(
                    url = meta.url,
                    description = description,
                    hash = meta.originalFileHash,
                    blurhash = meta.blurhash,
                    dim = dim,
                    uri = note.toNostrUri(),
                    authorName = note.author?.toBestDisplayName(),
                    mimeType = meta.mimeType,
                    encryptionAlgo = meta.version,
                    encryptionKey = exporterSecret,
                    encryptionNonce = meta.nonceBytes,
                    thumbhash = meta.thumbhash,
                )
            },
        )
    }

    ZoomableContentView(
        content,
        persistentListOf(content),
        roundedCorner = true,
        contentScale = ContentScale.FillWidth,
        accountViewModel,
    )
}

@Composable
private fun RenderDecryptionError(
    note: Note,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    TranslatableRichTextViewer(
        content = stringRes(id = Res.string.could_not_decrypt_the_message),
        canPreview = true,
        quotesLeft = 0,
        modifier = Modifier,
        tags = EmptyTagList,
        backgroundColor = bgColor,
        id = note.idHex,
        callbackUri = note.toNostrUri(),
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/** The Marmot group whose room [note] is in, which holds the keys to its media. */
private fun findGroupIdForNote(
    note: Note,
    accountViewModel: AccountViewModel,
): String? = note.marmotGroupIdFor(accountViewModel.account)

/** Where the lookup of a message's media key stands. */
private sealed interface MessageMediaSecret {
    data object Loading : MessageMediaSecret

    data object Missing : MessageMediaSecret

    class Found(
        val secret: ByteArray,
    ) : MessageMediaSecret
}

/**
 * The room the attachment will take while its key is looked up, which is one frame on most
 * entries. Drawing nothing made every media row grow from 0 dp as it scrolled in, so the list
 * jumped; an attachment without dimensions has no size to keep.
 */
@Composable
private fun MediaKeyLoadingSpace(dimensions: String?) {
    val ratio = remember(dimensions) { dimensions?.let { DimensionTag.parse(it) }?.aspectRatioOrNull() } ?: return
    Box(Modifier.fillMaxWidth().aspectRatio(ratio))
}

/**
 * The media key of the epoch that carried [note]. An attachment is keyed to its
 * message's epoch, so the group's current key stops opening it as soon as a
 * commit lands after it was sent.
 */
@Composable
private fun rememberMessageMediaSecret(
    note: Note,
    nostrGroupId: String?,
    accountViewModel: AccountViewModel,
): MessageMediaSecret {
    val state by
        produceState<MessageMediaSecret>(MessageMediaSecret.Loading, note.idHex, nostrGroupId) {
            val secret = nostrGroupId?.let { accountViewModel.marmotMediaSecretForMessage(it, note.idHex) }
            value = if (secret != null) MessageMediaSecret.Found(secret) else MessageMediaSecret.Missing
        }
    return state
}
