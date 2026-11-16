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
package com.vitorpamplona.amethyst.commons.relayClient.home

import com.vitorpamplona.amethyst.commons.relayClient.home.nip01Core.HomePostsBuHashtagsKinds
import com.vitorpamplona.amethyst.commons.relayClient.home.nip01Core.HomePostsByGeohashKinds
import com.vitorpamplona.amethyst.commons.relayClient.home.nip65Follows.HomePostsNewThreadKinds1
import com.vitorpamplona.amethyst.commons.relayClient.home.nip72Communities.HomePostsFromCommunityKinds
import com.vitorpamplona.amethyst.commons.relayClient.home.nip72Communities.HomePostsFromCommunityKindsStr
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.AddressableNormalVideoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The "All Follows" home feed fans out to four REQ builders: authors, hashtags, geohashes and
 * communities. The home DAL renders pictures and every NIP-71 video kind from any of those legs,
 * so a leg that omits a media kind silently hides content the feed would happily show — a
 * kind-22 short tagged `#bitcoin` never arriving while a kind-1 with the same tag does.
 */
class HomeMediaKindCoverageTest {
    private val mediaKinds =
        listOf(
            PictureEvent.KIND,
            VideoNormalEvent.KIND,
            VideoShortEvent.KIND,
            AddressableNormalVideoEvent.KIND,
            AddressableShortVideoEvent.KIND,
        )

    private fun assertCarriesMedia(
        legName: String,
        kinds: List<Int>,
    ) = mediaKinds.forEach {
        assertTrue(it in kinds, "$legName must request kind $it")
    }

    @Test
    fun everyHomeLegRequestsPicturesAndAllVideoKinds() {
        assertCarriesMedia("authors", HomePostsNewThreadKinds1)
        assertCarriesMedia("hashtags", HomePostsBuHashtagsKinds)
        assertCarriesMedia("geohashes", HomePostsByGeohashKinds)
        assertCarriesMedia("communities", HomePostsFromCommunityKinds)
    }

    @Test
    fun communityApprovalKTagsMirrorTheCommunityKinds() {
        assertEquals(HomePostsFromCommunityKinds.map { it.toString() }, HomePostsFromCommunityKindsStr)
    }
}
