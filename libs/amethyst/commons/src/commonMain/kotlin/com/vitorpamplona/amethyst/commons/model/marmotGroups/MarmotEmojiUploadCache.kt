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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import com.vitorpamplona.marmot.appComponents.EncryptedMediaReferenceV2
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Custom-emoji pictures already uploaded to a group, by (group, epoch, pack URL). Sending one
 * again in the same epoch points at the same blob: no new download, encryption or upload, and
 * every receiver derives the same key. Only one epoch is kept per group, the newest seen, since a
 * message can only be sent in the current one.
 */
class MarmotEmojiUploadCache {
    private val byGroup = mutableMapOf<HexKey, Pair<Long, MutableMap<String, EncryptedMediaReferenceV2>>>()
    private val lock = Mutex()

    suspend fun get(
        nostrGroupId: HexKey,
        epoch: Long,
        url: String,
    ): EncryptedMediaReferenceV2? = lock.withLock { byGroup[nostrGroupId]?.takeIf { it.first == epoch }?.second?.get(url) }

    suspend fun remember(
        nostrGroupId: HexKey,
        epoch: Long,
        url: String,
        reference: EncryptedMediaReferenceV2,
    ) = lock.withLock {
        val current = byGroup[nostrGroupId]
        when {
            current == null || current.first < epoch -> byGroup[nostrGroupId] = epoch to mutableMapOf(url to reference)
            current.first == epoch -> current.second[url] = reference
            // An upload that finished after the group moved on: nothing can send it any more.
            else -> {}
        }
    }

    suspend fun forget(nostrGroupId: HexKey) = lock.withLock { byGroup.remove(nostrGroupId) }
}
