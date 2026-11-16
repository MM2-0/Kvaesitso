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
package com.vitorpamplona.amethyst

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID

/**
 * Shared helpers for AVIF instrumented tests.
 *
 * [copyAssetToCache] always uses a UUID-prefixed filename so concurrent or
 * back-to-back test runs cannot collide on a shared cache path.
 *
 * [contentUriFor] wraps a file with FileProvider so `ContentResolver.getType()`
 * returns the correct MIME — it returns null for `file://` URIs, which makes
 * `MetadataStripper` skip the AVIF branch entirely.
 */
object AvifInstrumentedTestSupport {
    val appContext: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    val testContext: Context get() = InstrumentationRegistry.getInstrumentation().context

    fun copyAssetToCache(assetPath: String): File {
        val cacheDir = appContext.cacheDir.also { it.mkdirs() }
        val out = File(cacheDir, "${UUID.randomUUID()}-${assetPath.substringAfterLast('/')}")
        testContext.assets.open(assetPath).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    fun contentUriFor(file: File): Uri =
        FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.provider",
            file,
        )
}
