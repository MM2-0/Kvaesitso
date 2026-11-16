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
package com.vitorpamplona.amethyst.commons.preview

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How much of a page [UrlPreview] reads before giving up on its `<head>`.
 */
class UrlPreviewSizeTest {
    private val contentType = "text/html; charset=utf-8"

    private fun serving(html: String): (String) -> OkHttpClient =
        { _ ->
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", contentType)
                        .body(html.toResponseBody(contentType.toMediaType()))
                        .build()
                }.build()
        }

    /** `<head>` opens with [scriptBytes] of inline script before the page's own meta tags. */
    private fun pageWithScriptHeavyHead(scriptBytes: Int): String =
        buildString {
            append("<!DOCTYPE html><html><head><script>var ytcfg = {")
            while (length < scriptBytes) append("\"k\":\"vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv\",")
            append("};</script>")
            append("<title>Goals vs. Systems - YouTube</title>")
            append("<meta property=\"og:title\" content=\"Goals vs. Systems\">")
            append("<meta property=\"og:image\" content=\"https://i.ytimg.com/vi/fwcKTYvupJw/maxresdefault.jpg\">")
            append("<meta property=\"og:video:url\" content=\"https://www.youtube.com/embed/fwcKTYvupJw\">")
            append("<meta property=\"og:video:type\" content=\"text/html\">")
            append("</head><body></body></html>")
        }

    @Test
    fun readsOgTagsBehindAYouTubeSizedHead() =
        runTest {
            // A YouTube watch page (youtu.be/fwcKTYvupJw, 2026-09) is 1.29 MB, and its og: block
            // starts 708,776 bytes in, still inside <head> (which closes at 716,956). A 512 KB read
            // stopped short of it, so every YouTube link lost its card.
            val url = "https://youtu.be/fwcKTYvupJw"
            val info = UrlPreview().getDocument(url, serving(pageWithScriptHeavyHead(708_000)))

            assertEquals("Goals vs. Systems", info.title)
            assertEquals("https://i.ytimg.com/vi/fwcKTYvupJw/maxresdefault.jpg", info.image)
            assertTrue(info.fetchComplete())
        }

    /** Serves [html] and counts in [pulled] how many of its bytes are read off the "network". */
    private fun servingCounted(
        html: String,
        pulled: LongArray,
    ): (String) -> OkHttpClient {
        val counting: Source =
            object : ForwardingSource(Buffer().writeUtf8(html)) {
                override fun read(
                    sink: Buffer,
                    byteCount: Long,
                ): Long = super.read(sink, minOf(byteCount, 8192L)).also { if (it > 0) pulled[0] += it }
            }
        val body: ResponseBody = counting.buffer().asResponseBody(contentType.toMediaType())
        return { _ ->
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", contentType)
                        .body(body)
                        .build()
                }.build()
        }
    }

    @Test
    fun stopsReadingOnceTheHeadCloses() =
        runTest {
            // A GitHub page (github.com/vitorpamplona/amethyst, 2026-10) is 495 KB and closes its
            // <head> 33,639 bytes in. Reading on to the end cost a slow mobile link seconds before
            // the card's image could even start loading.
            val head =
                "<!DOCTYPE html><html><head><title>vitorpamplona/amethyst</title>" +
                    "<meta property=\"og:image\" content=\"https://opengraph.githubassets.com/x/vitorpamplona/amethyst\">" +
                    "</head><body>"
            val pulled = LongArray(1)
            val client = servingCounted(head + "<div>" + "x".repeat(1_500_000) + "</div></body></html>", pulled)

            val info = UrlPreview().getDocument("https://github.com/vitorpamplona/amethyst", client)

            assertEquals("https://opengraph.githubassets.com/x/vitorpamplona/amethyst", info.image)
            assertTrue(pulled[0] < 128 * 1024, "read ${pulled[0]} bytes of a page whose head ends at ${head.length}")
        }

    @Test
    fun aHeadEndInsideAScriptOrACommentDoesNotStopTheRead() =
        runTest {
            // A head front-loaded with inline script, as YouTube's is: a `</head>` in a JS string or
            // a comment is not the end of the head, and the og: tags come well after it.
            val head =
                "<html><head><script>var tpl = \"</head>\";" + "/*" + "x".repeat(60_000) + "*/</script>" +
                    "<!-- old layout: </head> -->" + "<style>" + "y".repeat(40_000) + "</style>" +
                    "<meta property=\"og:image\" content=\"https://i.ytimg.com/vi/x/maxresdefault.jpg\">" +
                    "</head><body>"
            val pulled = LongArray(1)
            val client = servingCounted(head + "z".repeat(1_000_000) + "</body></html>", pulled)

            val info = UrlPreview().getDocument("https://www.youtube.com/watch?v=x", client)

            assertEquals("https://i.ytimg.com/vi/x/maxresdefault.jpg", info.image)
            assertTrue(pulled[0] < head.length + 64 * 1024, "read ${pulled[0]} bytes of a page whose head ends at ${head.length}")
        }
}
