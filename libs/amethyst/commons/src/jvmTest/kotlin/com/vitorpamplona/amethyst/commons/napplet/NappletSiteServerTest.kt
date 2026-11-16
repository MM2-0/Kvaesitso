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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobCache
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobStore
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The serving rules both hosts share, from a cache that already holds the files (no network). */
class NappletSiteServerTest {
    private val dir = createTempDirectory("site-server-test").toFile()
    private val cache = NappletBlobCache(dir)
    private val origin = NappletWebContract.appOrigin("nsite1")
    private val shellHtml = "<!doctype html><html><head><title>shell</title></head><body>__APP_SANDBOX__ __APP_BOOTSTRAP__</body></html>".encodeToByteArray()

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun file(
        path: String,
        content: String,
    ): PathTag {
        val bytes = content.encodeToByteArray()
        val hash = sha256(bytes).toHexKey()
        cache.put(hash, bytes)
        return PathTag(path, hash)
    }

    private fun server(
        profile: HostProfile,
        paths: List<PathTag>,
    ) = NappletSiteServer(
        blobs = NappletBlobStore(cache, OkHttpClient(), paths, emptyList()),
        shellHtml = shellHtml,
        shimJs = "/*shim*/",
        appOrigin = origin,
        profile = profile,
        declaredDomains = profile.declaredCapabilities(listOf("storage")).map { it.name.lowercase() },
        shellHead = "<script>/*bridge*/</script>",
    )

    private val site by lazy {
        server(
            HostProfile.WEBSITE,
            listOf(file("/index.html", "<p>HOME</p>"), file("/app.js", "console.log(1)"), file("/style.css", "p{}")),
        )
    }

    @Test
    fun encodedPathsReachTheirFiles() =
        runBlocking {
            // Browsers send paths percent-encoded; the event lists them as written. A plus is a plus.
            val server = server(HostProfile.WEBSITE, listOf(file("/my file.js", "SPACE"), file("/café.js", "ACCENT"), file("/a+b.js", "PLUS")))
            assertEquals("SPACE", server.serve("GET", "$origin/my%20file.js", acceptsHtml = false)?.readBody()?.decodeToString())
            assertEquals("ACCENT", server.serve("GET", "$origin/caf%C3%A9.js", acceptsHtml = false)?.readBody()?.decodeToString())
            assertEquals("PLUS", server.serve("GET", "$origin/a+b.js", acceptsHtml = false)?.readBody()?.decodeToString())
            assertEquals(404, server.serve("GET", "$origin/my%2", acceptsHtml = false)?.status)
        }

    @Test
    fun aListedPageNoServerHasIsAnErrorNotTheHomePage() =
        runBlocking {
            val missing = "<p>about</p>".encodeToByteArray()
            val server = server(HostProfile.WEBSITE, listOf(file("/index.html", "<p>HOME</p>"), PathTag("/about.html", sha256(missing).toHexKey())))
            assertEquals(502, server.serve("GET", "$origin/about.html", acceptsHtml = true)?.status)
            // A client-side route the event doesn't list still gets the page.
            assertContains(server.serve("GET", "$origin/some/route", acceptsHtml = true)!!.readBody().decodeToString(), "HOME")
        }

    @Test
    fun aDuplicatedPathServesItsFirstTag() =
        runBlocking {
            // As quartz's resolver reads it, so both agree on what the site is.
            val server = server(HostProfile.WEBSITE, listOf(file("/x.js", "FIRST"), file("/x.js", "SECOND")))
            assertEquals("FIRST", server.serve("GET", "$origin/x.js", acceptsHtml = false)?.readBody()?.decodeToString())
        }

    @Test
    fun aSiteShellFramesItsOwnOrigin() =
        runBlocking {
            val shell = assertNotNull(site.serve("GET", NappletWebContract.SHELL_URL, acceptsHtml = true))
            val html = shell.readBody().decodeToString()
            assertContains(html, "<head><script>/*bridge*/</script>")
            assertContains(html, "iframe.src = '$origin/';")
            assertEquals(
                "frame-src $origin;",
                shell.headers
                    .getValue("Content-Security-Policy")
                    .split(" ")
                    .let { it[it.indexOf("frame-src") + 1].let { src -> "frame-src $src" } },
            )
            assertEquals("no-store", shell.headers["Cache-Control"])
        }

    @Test
    fun aSiteServesItsFilesWithThePreludeInItsPages() =
        runBlocking {
            val page = assertNotNull(site.serve("GET", "$origin/", acceptsHtml = true))
            assertEquals(200, page.status)
            assertEquals("text/html; charset=utf-8", page.contentType)
            val html = page.readBody().decodeToString()
            assertContains(html, "window.__nappletNip07=true")
            assertContains(html, "<p>HOME</p>")
            assertFalse(page.headers.containsKey("Content-Security-Policy"), "a site sets its own CSP")

            val script = assertNotNull(site.serve("GET", "$origin/app.js?v=1", acceptsHtml = false))
            assertEquals("console.log(1)", script.readBody().decodeToString(), "scripts are served as they are")
        }

    @Test
    fun aClientRouteFallsBackToThePageButAMissingFileIsA404() =
        runBlocking {
            val route = assertNotNull(site.serve("GET", "$origin/posts/42", acceptsHtml = true))
            assertContains(route.readBody().decodeToString(), "<p>HOME</p>")

            assertEquals(404, assertNotNull(site.serve("GET", "$origin/missing.js", acceptsHtml = false)).status)
        }

    @Test
    fun theBrowserHandlesWhatIsNotOurs() =
        runBlocking {
            assertNull(site.serve("GET", "https://relay.example/", acceptsHtml = false), "a site talks to other hosts itself")
            assertNull(site.serve("POST", "https://relay.example/api", acceptsHtml = false), "and sends anything but GETs there itself")
            // Its own host has no server behind it: a POST there would only go out to look it up.
            assertEquals(405, assertNotNull(site.serve("POST", "$origin/api", acceptsHtml = false)).status)
        }

    @Test
    fun aNappletRunsItsArtifactAsASrcdocAndHasNoNetwork() =
        runBlocking {
            val napplet = server(HostProfile.NAPPLET, listOf(file("/index.html", "<p>GAME</p>")))
            val shell = assertNotNull(napplet.serve("GET", NappletWebContract.SHELL_URL, acceptsHtml = true))
            val html = shell.readBody().decodeToString()
            assertContains(html, "iframe.srcdoc=")
            assertContains(html, "allow-scripts")
            assertFalse(html.contains("allow-same-origin"))

            assertEquals(404, assertNotNull(napplet.serve("GET", "https://relay.example/", acceptsHtml = false)).status)
        }

    @Test
    fun aNappletWithoutItsArtifactGetsNoShell() =
        runBlocking {
            val missing = server(HostProfile.NAPPLET, listOf(PathTag("/index.html", "b".repeat(64))))
            assertEquals(404, assertNotNull(missing.serve("GET", NappletWebContract.SHELL_URL, acceptsHtml = true)).status)
        }

    @Test
    fun contentTypesSplitIntoMimeAndCharset() {
        assertEquals("text/css" to "utf-8", NappletSiteServer.splitContentType("text/css"))
        assertEquals("application/javascript" to "utf-8", NappletSiteServer.splitContentType("application/javascript"))
        assertEquals("image/png" to null, NappletSiteServer.splitContentType("image/png"))
        assertEquals("text/html" to "iso-8859-1", NappletSiteServer.splitContentType("text/html; charset=iso-8859-1"))
        assertTrue(NappletSiteServer.notFound().status == 404)
    }
}
