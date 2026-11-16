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
package com.vitorpamplona.amethyst.commons.appupdate

/** The platform half of an update: fetch the file, check it, stage it, and hand over to it. */
interface AppUpdateInstaller {
    /**
     * Downloads [update]'s asset, checks its SHA-256 and size against the signed release, and stages
     * it next to the install. Throws [UpdateInstallException] with what went wrong.
     */
    suspend fun prepare(
        update: AvailableUpdate,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit,
    )

    /**
     * Starts what finishes the update once this process is gone (a helper that waits for it, swaps
     * the files and relaunches), then quits the app.
     */
    fun applyAndRestart(update: AvailableUpdate)
}

class UpdateInstallException(
    val failure: UpdateFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
