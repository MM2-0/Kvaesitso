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
package com.vitorpamplona.amethyst.ui.actions

import com.vitorpamplona.amethyst.ui.actions.MediaSaverToDisk.MediaStoreTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSaverToDiskTest {
    @Test
    fun normalizesVideoXM4vToVideoMp4() {
        assertEquals("video/mp4", MediaSaverToDisk.normalizeMimeTypeForMediaStore("video/x-m4v"))
    }

    @Test
    fun normalizationIsCaseInsensitive() {
        assertEquals("video/mp4", MediaSaverToDisk.normalizeMimeTypeForMediaStore("Video/X-M4V"))
    }

    @Test
    fun passesThroughSupportedVideoTypes() {
        assertEquals("video/mp4", MediaSaverToDisk.normalizeMimeTypeForMediaStore("video/mp4"))
        assertEquals("video/webm", MediaSaverToDisk.normalizeMimeTypeForMediaStore("video/webm"))
        assertEquals("video/quicktime", MediaSaverToDisk.normalizeMimeTypeForMediaStore("video/quicktime"))
    }

    @Test
    fun passesThroughImageAndAudioTypes() {
        assertEquals("image/jpeg", MediaSaverToDisk.normalizeMimeTypeForMediaStore("image/jpeg"))
        assertEquals("image/png", MediaSaverToDisk.normalizeMimeTypeForMediaStore("image/png"))
        assertEquals("audio/mpeg", MediaSaverToDisk.normalizeMimeTypeForMediaStore("audio/mpeg"))
    }

    @Test
    fun routesEachMediaKindToItsOwnCollection() {
        assertEquals(MediaStoreTarget.IMAGES, MediaStoreTarget.of("image/jpeg"))
        assertEquals(MediaStoreTarget.AUDIO, MediaStoreTarget.of("audio/mpeg"))
        assertEquals(MediaStoreTarget.VIDEO, MediaStoreTarget.of("video/mp4"))
    }

    @Test
    fun routesPdfsAndUnknownTypesToDownloads() {
        assertEquals(MediaStoreTarget.DOWNLOADS, MediaStoreTarget.of("application/pdf"))
        assertEquals(MediaStoreTarget.DOWNLOADS, MediaStoreTarget.of("application/zip"))
        assertEquals(MediaStoreTarget.DOWNLOADS, MediaStoreTarget.of(""))
    }

    @Test
    fun routingIsCaseInsensitive() {
        assertEquals(MediaStoreTarget.IMAGES, MediaStoreTarget.of("Image/PNG"))
        assertEquals(MediaStoreTarget.AUDIO, MediaStoreTarget.of("Audio/MPEG"))
        assertEquals(MediaStoreTarget.VIDEO, MediaStoreTarget.of("Video/MP4"))
    }

    @Test
    fun eachCollectionIsPairedWithADirectoryMediaProviderAcceptsForIt() {
        // Video content filed under "Pictures" is the rejection reported in #4009.
        assertEquals("Pictures", MediaStoreTarget.IMAGES.relativeDirectory)
        assertEquals("Music", MediaStoreTarget.AUDIO.relativeDirectory)
        assertEquals("Movies", MediaStoreTarget.VIDEO.relativeDirectory)
        assertEquals("Download", MediaStoreTarget.DOWNLOADS.relativeDirectory)
    }
}
