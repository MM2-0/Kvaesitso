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

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.io.File
import java.util.UUID

/**
 * Shared harness for the MediaSaverToDisk instrumented tests: writes a small payload
 * file, drives [MediaSaverToDisk.save] with the given MIME type, and asserts the save
 * reported success. Package-level support object per the AvifInstrumentedTestSupport
 * precedent.
 */
object MediaSaverTestSupport {
    /** Drives one save and fails the test if it reported an error or never succeeded. */
    fun saveAndAssertSuccess(
        context: Context,
        mimeType: String,
    ) {
        val localFile = File(context.cacheDir, "media-saver-${UUID.randomUUID()}.bin")
        localFile.writeBytes(ByteArray(2048) { it.toByte() })

        var failure: Throwable? = null
        var succeeded = false

        try {
            runBlocking {
                MediaSaverToDisk.save(
                    localFile = localFile,
                    mimeType = mimeType,
                    context = context,
                    onSuccess = { succeeded = true },
                    onError = { failure = it },
                )
            }
        } finally {
            localFile.delete()
        }

        // Surfaces e.g. the #4009 IllegalArgumentException as the test failure message.
        assertNull("save() reported an error: ${failure?.message}", failure)
        assertTrue("save() never reported success", succeeded)
    }
}
