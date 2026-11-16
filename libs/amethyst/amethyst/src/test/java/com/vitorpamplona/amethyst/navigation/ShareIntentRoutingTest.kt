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
package com.vitorpamplona.amethyst.navigation

import com.vitorpamplona.amethyst.commons.ui.navigation.ShareIntentRouting
import com.vitorpamplona.amethyst.commons.ui.navigation.ShareTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareIntentRoutingTest {
    @Test
    fun detectsAliasByExactClassName() {
        assertEquals(ShareTarget.DIRECT_MESSAGE, ShareIntentRouting.targetOf("com.vitorpamplona.amethyst.ui.ShareAsDMAlias"))
    }

    @Test
    fun detectsAliasRegardlessOfPackagePrefix() {
        // Flavors can change the resolved package prefix; match on the simple name.
        assertEquals(ShareTarget.DIRECT_MESSAGE, ShareIntentRouting.targetOf("com.example.fork.ui.ShareAsDMAlias"))
    }

    @Test
    fun detectsEveryAlias() {
        assertEquals(ShareTarget.HIGHLIGHT, ShareIntentRouting.targetOf("com.vitorpamplona.amethyst.ui.ShareAsHighlightAlias"))
        assertEquals(ShareTarget.PICTURE, ShareIntentRouting.targetOf("com.vitorpamplona.amethyst.ui.ShareAsPictureAlias"))
        assertEquals(ShareTarget.SHORT_VIDEO, ShareIntentRouting.targetOf("com.vitorpamplona.amethyst.ui.ShareAsShortVideoAlias"))
        assertEquals(ShareTarget.VIDEO, ShareIntentRouting.targetOf("com.vitorpamplona.amethyst.ui.ShareAsVideoAlias"))
        assertEquals(ShareTarget.SCAN_QR, ShareIntentRouting.targetOf("com.vitorpamplona.amethyst.ui.ScanQrCodeAlias"))
    }

    @Test
    fun mainActivityIsTheDefaultNewPostTarget() {
        assertEquals(ShareTarget.NEW_POST, ShareIntentRouting.targetOf("com.vitorpamplona.amethyst.ui.MainActivity"))
    }

    @Test
    fun nullIsTheDefaultNewPostTarget() {
        assertEquals(ShareTarget.NEW_POST, ShareIntentRouting.targetOf(null))
    }

    @Test
    fun rejectsSuffixThatIsNotASimpleName() {
        assertEquals(ShareTarget.NEW_POST, ShareIntentRouting.targetOf("com.evil.XShareAsDMAlias"))
        assertEquals(ShareTarget.NEW_POST, ShareIntentRouting.targetOf("com.evil.XShareAsVideoAlias"))
    }

    /**
     * "ShareAsShortVideoAlias" also ends with "VideoAlias", so a sloppier match would route every
     * short to the plain video composer (kind 21 instead of 22 — the wrong feed).
     */
    @Test
    fun shortVideoAliasDoesNotCollideWithTheVideoAlias() {
        assertEquals(ShareTarget.SHORT_VIDEO, ShareIntentRouting.targetOf("com.vitorpamplona.amethyst.ui.ShareAsShortVideoAlias"))
    }
}
