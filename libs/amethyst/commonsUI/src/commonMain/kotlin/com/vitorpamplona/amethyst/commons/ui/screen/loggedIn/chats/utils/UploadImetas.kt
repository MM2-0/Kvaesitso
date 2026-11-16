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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils

import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.IMetaAttachments
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.upload.SuccessfulUploads
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag

/**
 * Turns an encrypted upload into the Armada-shaped encrypted `imeta` (via
 * [ChannelChat.encryptedImageImeta]). Returns null when the upload carried no cipher — so a
 * non-encrypted blob is never sent as an encrypted image (fails closed, protecting the end-to-end
 * guarantee of Concord channels and their minichat threads).
 */
fun SuccessfulUploads.toConcordImeta(): IMetaTag? {
    val cipher = cipher ?: return null
    return ChannelChat.encryptedImageImeta(
        url = result.url,
        mimeType = result.mimeTypeBeforeEncryption,
        dim = result.fileHeader.dim?.toString(),
        blurhash = result.fileHeader.blurHash?.blurhash,
        cipher = cipher,
        originalHash = result.hashBeforeEncryption,
        thumbhash = result.fileHeader.thumbHash?.thumbhash,
    )
}

/**
 * Turns a list of plaintext (unencrypted) uploads into their NIP-92 `imeta` tags — the shape a
 * public-chat (NIP-28/NIP-29) message carries. Reuses [IMetaAttachments.add] so the tag content
 * (hash, size, mime, dims, blurhash, thumbhash, magnet, alt, content-warning) matches every other
 * plaintext composer.
 */
fun List<SuccessfulUploads>.toPlainImetas(): List<IMetaTag> {
    val attachments = IMetaAttachments()
    forEach { attachments.add(it.result, it.caption, it.contentWarningReason) }
    return attachments.iMetaAttachments
}
