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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.upload

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.IMetaAttachments
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip92IMeta.imetas

class ChatFileSender(
    val chatroom: ChatroomKey,
    val account: Account,
) {
    suspend fun sendNIP17(uploads: List<SuccessfulUploads>) {
        uploads.forEach {
            if (it.cipher != null) {
                sendNIP17(it.result, it.caption, it.contentWarningReason, it.cipher)
            } else {
                sendNIP17AsHiddenLink(it.result, it.caption, it.contentWarningReason)
            }
        }
    }

    suspend fun sendNIP17(
        result: UploadOrchestrator.OrchestratorResult.ServerResult,
        caption: String?,
        contentWarningReason: String?,
        cipher: AESGCM,
    ) {
        account.sendNip17EncryptedFile(
            ChatMessageEncryptedFileHeaderEvent.build(
                url = result.url,
                to = chatroom.users.map { LocalCache.getOrCreateUser(it).toPTag() },
                cipher = cipher,
                mimeType = result.mimeTypeBeforeEncryption,
                originalHash = result.hashBeforeEncryption,
                hash = result.fileHeader.hash,
                size = result.fileHeader.size,
                dimension = result.fileHeader.dim,
                blurhash = result.fileHeader.blurHash?.blurhash,
                thumbhash = result.fileHeader.thumbHash?.thumbhash,
            ) {
                // NIP-94 accessibility description of the file (kept; the deprecated
                // generic NIP-31 boilerplate alt is not written).
                if (!caption.isNullOrEmpty()) {
                    alt(caption)
                }
                contentWarningReason?.let { contentWarning(it) }
            },
        )
    }

    suspend fun sendNIP17AsHiddenLink(
        result: UploadOrchestrator.OrchestratorResult.ServerResult,
        caption: String?,
        contentWarningReason: String?,
    ) {
        val iMetaAttachments = IMetaAttachments()
        iMetaAttachments.add(result, caption, contentWarningReason)

        val toUsers = chatroom.users.map { LocalCache.getOrCreateUser(it).toPTag() }

        val template =
            ChatMessageEvent.build(result.url, toUsers) {
                references(listOf(result.url))

                contentWarningReason?.let { contentWarning(it) }

                imetas(iMetaAttachments.filterIsIn(setOf(result.url)))
            }

        account.sendNip17PrivateMessage(template)
    }

    suspend fun sendAll(uploads: List<SuccessfulUploads>) {
        sendNIP17(uploads)
    }
}
