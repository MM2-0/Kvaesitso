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

import com.vitorpamplona.marmot.appComponents.EncryptedMediaPolicyV2
import com.vitorpamplona.marmot.appComponents.EncryptedMediaReferenceV2
import com.vitorpamplona.marmot.appComponents.MediaLocatorV2
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * An uploaded emoji picture is reused only in the group and epoch whose key encrypted it: any
 * other epoch's members derive a different key and could not open it.
 */
class MarmotEmojiUploadCacheTest {
    private val group = "a".repeat(64)
    private val other = "b".repeat(64)
    private val pack = "https://example.com/pack/smile.png"

    private fun reference(blob: String) =
        EncryptedMediaReferenceV2(
            locators = listOf(MediaLocatorV2(EncryptedMediaPolicyV2.INITIAL_LOCATOR_KIND, "https://blossom.example/$blob")),
            ciphertextSha256 = ByteArray(32),
            plaintextSha256 = ByteArray(32),
            nonce = ByteArray(12),
            mediaType = "image/png",
            filename = "smile.png",
        )

    @Test
    fun aPictureIsReusedInItsOwnGroupAndEpochOnly() =
        runTest {
            val cache = MarmotEmojiUploadCache()
            val uploaded = reference("e3")
            cache.remember(group, 3, pack, uploaded)

            assertSame(uploaded, cache.get(group, 3, pack))
            assertNull(cache.get(group, 4, pack))
            assertNull(cache.get(group, 2, pack))
            assertNull(cache.get(other, 3, pack))
        }

    @Test
    fun aNewEpochDropsTheOldOnesUploads() =
        runTest {
            val cache = MarmotEmojiUploadCache()
            cache.remember(group, 3, pack, reference("e3"))
            val next = reference("e4")
            cache.remember(group, 4, pack, next)

            assertNull(cache.get(group, 3, pack))
            assertSame(next, cache.get(group, 4, pack))
        }

    @Test
    fun anUploadThatFinishesAfterACommitIsNotKept() =
        runTest {
            val cache = MarmotEmojiUploadCache()
            val current = reference("e4")
            cache.remember(group, 4, pack, current)
            cache.remember(group, 3, pack, reference("e3"))

            assertNull(cache.get(group, 3, pack))
            assertSame(current, cache.get(group, 4, pack))
        }

    @Test
    fun leavingTheGroupForgetsItsUploads() =
        runTest {
            val cache = MarmotEmojiUploadCache()
            cache.remember(group, 3, pack, reference("e3"))
            cache.forget(group)

            assertNull(cache.get(group, 3, pack))
        }
}
