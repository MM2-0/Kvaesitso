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

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end regression test for issue #4009: drives the real ContentResolver, so it
 * catches both symptoms of a collection/directory mismatch - Android 10 rejects the
 * insert outright (the quoted rejection lives in [MediaSaverToDisk.MediaStoreTarget]'s
 * KDoc), and later releases accept it and silently misfile the video.
 */
@RunWith(AndroidJUnit4::class)
class MediaSaverToDiskMediaStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver: ContentResolver get() = context.contentResolver

    /** Only rows this test inserted, as item Uris in the collection they went into. */
    private val created = mutableListOf<Uri>()

    @Before
    fun requiresScopedStorage() {
        assumeTrue("saveContentQ only runs on API 29+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    }

    @After
    fun cleanUp() {
        created.forEach { resolver.delete(it, null, null) }
    }

    @Test
    fun savingAVideoLandsInMoviesAndNotPictures() {
        val relativePath = saveAndReadBackRelativePath("video/mp4", MediaStore.Video.Media.EXTERNAL_CONTENT_URI)

        assertEquals("Movies/Amethyst/", relativePath)
    }

    @Test
    fun savingAnImageStillLandsInPictures() {
        val relativePath = saveAndReadBackRelativePath("image/jpeg", MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

        assertEquals("Pictures/Amethyst/", relativePath)
    }

    private fun saveAndReadBackRelativePath(
        mimeType: String,
        collection: Uri,
    ): String? {
        // Anything at or below this id predates the test and must never be read or deleted:
        // this suite is meant to be runnable on a real device holding real media.
        val highWaterMark = maxIdIn(collection)

        MediaSaverTestSupport.saveAndAssertSuccess(context, mimeType)

        return rowInsertedAfter(collection, highWaterMark)
    }

    private fun maxIdIn(collection: Uri): Long {
        resolver
            .query(collection, arrayOf(MediaStore.MediaColumns._ID), null, null, "${MediaStore.MediaColumns._ID} DESC")
            ?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getLong(0)
            }
        return -1L
    }

    /** Reads back the row the save just inserted and records it for cleanup. */
    private fun rowInsertedAfter(
        collection: Uri,
        highWaterMark: Long,
    ): String? {
        resolver
            .query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH),
                "${MediaStore.MediaColumns._ID} > ?",
                arrayOf(highWaterMark.toString()),
                "${MediaStore.MediaColumns._ID} ASC",
            )?.use { cursor ->
                assertTrue("save() reported success but inserted no row into $collection", cursor.moveToFirst())
                created.add(ContentUris.withAppendedId(collection, cursor.getLong(0)))
                return cursor.getString(1)
            }
        return null
    }
}
