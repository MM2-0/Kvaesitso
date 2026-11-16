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
package com.vitorpamplona.amethyst.commons.service.upload

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The pure path-mapping that decides the absolute web path each uploaded file is served under — it
 * must match how the host resolves manifest paths (leading slash, forward slashes, nested dirs).
 */
class StaticSitePublisherTest {
    @Test
    fun singleFileMapsToItsName() {
        val tmp = File.createTempFile("napplet", ".html")
        try {
            assertEquals("/" + tmp.name, StaticSitePublisher.webPath(tmp, tmp))
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun nestedFilesMapToAbsoluteForwardSlashPaths() {
        val root = createTempDirectory("nsite").toFile()
        try {
            val index = File(root, "index.html").apply { writeText("<html>") }
            val nested =
                File(root, "assets/app.js").apply {
                    parentFile.mkdirs()
                    writeText("//")
                }

            assertEquals("/index.html", StaticSitePublisher.webPath(root, index))
            assertEquals("/assets/app.js", StaticSitePublisher.webPath(root, nested))
        } finally {
            root.deleteRecursively()
        }
    }
}
