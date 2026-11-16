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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.send

import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Sends uploaded encrypted media as Marmot group messages: each upload becomes
 * its own kind:9 carrying an `encrypted-media-v2` reference. The MIP-04 shape
 * survives only on the read side, for messages older builds already sent.
 *
 * Each message is sent pinned to [sourceEpoch], the epoch whose key encrypted the files: if a
 * commit landed during the upload, the send is refused rather than delivering attachments no
 * member can open.
 */
class MarmotFileSender(
    val nostrGroupId: HexKey,
    val sourceEpoch: Long,
    val accountViewModel: AccountViewModel,
) {
    suspend fun send(uploads: List<MarmotUploadResult>) {
        for (upload in uploads) {
            accountViewModel.sendMarmotGroupEncryptedMediaV2(
                nostrGroupId = nostrGroupId,
                reference = upload.reference,
                caption = upload.caption.orEmpty(),
                sourceEpoch = sourceEpoch,
            )
        }
    }
}
