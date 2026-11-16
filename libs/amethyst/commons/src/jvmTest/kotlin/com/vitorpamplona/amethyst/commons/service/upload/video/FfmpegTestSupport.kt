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
package com.vitorpamplona.amethyst.commons.service.upload.video

import com.vitorpamplona.amethyst.commons.moderation.notifications.HostOs
import com.vitorpamplona.amethyst.commons.moderation.notifications.detectHostOs
import java.io.File

/** Shared by the tests that drive real child processes without an ffmpeg. */
internal object FfmpegTestSupport {
    val isWindows: Boolean get() = detectHostOs() == HostOs.WINDOWS

    /** A stand-in binary: an executable `sh` script with [body], deleted when the JVM exits. Not for Windows. */
    fun fakeBinary(body: String): File =
        File.createTempFile("fake-ffmpeg", ".sh").apply {
            writeText("#!/bin/sh\n$body\n")
            setExecutable(true)
            deleteOnExit()
        }
}
