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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.Note
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
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.crypto.aes.AES_GCM_NAME
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip31Alts.alt
import kotlinx.collections.immutable.persistentListOf

@Composable
fun RenderEncryptedFile(
    note: Note,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? ChatMessageEncryptedFileHeaderEvent ?: return

    val algo = noteEvent.algo()
    val key = noteEvent.key()
    val nonce = noteEvent.nonce()
    val mimeType = noteEvent.mimeType()

    if (algo == AES_GCM_NAME && key != null && nonce != null) {
        accountViewModel.account.encryptionKeyCache.add(noteEvent.content, AESGCM(key, nonce), mimeType)

        // Audio is neither an image nor a video, and the image-or-video split
        // below would hand it to the video player: a picture-less surface where
        // the voice UI belongs. Checked before that split rather than inside it,
        // since the whole ZoomableContentView pipeline is about visual media.
        if (RichTextParser.isAudioContent(mimeType, noteEvent.content)) {
            val waveform = remember(noteEvent) { noteEvent.waveform()?.let { WaveformData(it) } }

            RenderAudioWithWaveform(
                mediaUrl = noteEvent.content,
                title = noteEvent.alt(),
                mimeType = mimeType,
                waveform = waveform,
                note = note,
                accountViewModel = accountViewModel,
                nav = nav,
            )
            return
        }

        val content by remember(noteEvent) {
            val isImage = mimeType?.startsWith("image/") == true || RichTextParser.isImageUrl(noteEvent.content)

            mutableStateOf<BaseMediaContent>(
                if (isImage) {
                    EncryptedMediaUrlImage(
                        url = noteEvent.content,
                        description = noteEvent.alt(),
                        hash = noteEvent.originalHash(),
                        blurhash = noteEvent.blurhash(),
                        dim = noteEvent.dimensions(),
                        uri = note.toNostrUri(),
                        mimeType = mimeType,
                        encryptionAlgo = algo,
                        encryptionKey = key,
                        encryptionNonce = nonce,
                        thumbhash = noteEvent.thumbhash(),
                        sizeBytes = noteEvent.size()?.toLong(),
                    )
                } else {
                    EncryptedMediaUrlVideo(
                        url = noteEvent.content,
                        description = noteEvent.alt(),
                        hash = noteEvent.originalHash(),
                        blurhash = noteEvent.blurhash(),
                        dim = noteEvent.dimensions(),
                        uri = note.toNostrUri(),
                        authorName = note.author?.toBestDisplayName(),
                        mimeType = mimeType,
                        encryptionAlgo = algo,
                        encryptionKey = key,
                        encryptionNonce = nonce,
                        thumbhash = noteEvent.thumbhash(),
                        sizeBytes = noteEvent.size()?.toLong(),
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
    } else {
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
}
