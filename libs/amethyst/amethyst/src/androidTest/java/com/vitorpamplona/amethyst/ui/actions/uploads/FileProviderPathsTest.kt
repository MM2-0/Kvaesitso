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
package com.vitorpamplona.amethyst.ui.actions.uploads

import android.os.Environment
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.getPhotoUri
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.getVideoUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Pins what `res/xml/file_paths.xml` is allowed to hand out.
 *
 * The provider root used to be `<external-path path=".">`, i.e. the whole of
 * `Environment.getExternalStorageDirectory()`. It is now the app-specific
 * `<external-files-path>`, which is the only external location Amethyst ever
 * shares from (camera/video capture). These tests fail if either half of that
 * regresses: the capture paths must still resolve, and the external-storage
 * root must not.
 */
@RunWith(AndroidJUnit4::class)
class FileProviderPathsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val authority = "${context.packageName}.provider"

    @Test
    fun photoCaptureUriResolves() {
        val uri = getPhotoUri(context)
        assertEquals("content", uri.scheme)
        assertEquals(authority, uri.authority)
        assertTrue("expected the external_files root, got $uri", uri.path!!.startsWith("/external_files/"))
    }

    @Test
    fun videoCaptureUriResolves() {
        val uri = getVideoUri(context)
        assertEquals("content", uri.scheme)
        assertEquals(authority, uri.authority)
        assertTrue("expected the external_files root, got $uri", uri.path!!.startsWith("/external_files/"))
    }

    @Test
    fun cacheDirStillResolves() {
        val file = File(context.cacheDir, "amethyst_share_probe.png")
        val uri = FileProvider.getUriForFile(context, authority, file)
        assertEquals(authority, uri.authority)
        assertTrue("expected the cache root, got $uri", uri.path!!.startsWith("/cache/"))
    }

    @Test
    fun externalStorageRootIsNoLongerShareable() {
        @Suppress("DEPRECATION")
        val outside = File(Environment.getExternalStorageDirectory(), "Download/not-ours.pdf")
        try {
            val uri = FileProvider.getUriForFile(context, authority, outside)
            fail("FileProvider should not map $outside, but produced $uri")
        } catch (expected: IllegalArgumentException) {
            // Correct: no configured root contains it.
        }
    }
}
