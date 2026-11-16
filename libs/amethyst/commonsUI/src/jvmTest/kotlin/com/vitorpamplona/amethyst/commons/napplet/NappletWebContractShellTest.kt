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

import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.runBlocking
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class NappletWebContractShellTest {
    private val author = "a".repeat(64)
    private val shellHtml = runBlocking { NappletWebResources.shellHtml() }

    @Test
    fun theAppIdKeepsTheOriginAndroidAlwaysDerived() {
        // Moving the derivation must not move a site's origin, or every nSite would lose its storage.
        val before = "n" + sha256("$author:blog".encodeToByteArray()).toHexKey().take(31)
        assertEquals(before, NappletWebContract.appId(author, "blog"))
        assertEquals(32, before.length)
    }

    @Test
    fun aSiteNamedLikeAWebxdcAppNeverGetsItsOrigin() {
        // A site's d tag is free text: one spelling a webxdc instance must not share the app's storage.
        val identifier = NappletLaunch.WEBXDC_IDENTIFIER_PREFIX + "chess"
        assertNotEquals(
            NappletWebContract.appId(author, identifier, profile = HostProfile.WEBSITE),
            NappletWebContract.appId(author, identifier, profile = HostProfile.WEBXDC),
        )
        assertNotEquals(
            NappletWebContract.appId(author, identifier, partition = author, profile = HostProfile.WEBSITE),
            NappletWebContract.appId(author, identifier, partition = author, profile = HostProfile.WEBXDC),
        )
    }

    @Test
    fun aPartitionGivesEachAccountItsOwnOrigin() {
        val alice = NappletWebContract.appId(author, "blog", partition = "alice")
        val bob = NappletWebContract.appId(author, "blog", partition = "bob")
        assertNotEquals(alice, bob)
        assertNotEquals(NappletWebContract.appId(author, "blog"), alice)
    }

    @Test
    fun aNappletRunsItsVerifiedBytesAsAnOpaqueSrcdoc() {
        val origin = NappletWebContract.appOrigin("n1")
        val shell = NappletWebContract.shell(shellHtml, HostProfile.NAPPLET, origin, listOf("<p>game</p>".encodeToByteArray()))
        val html = shell.html.decodeToString()
        assertContains(html, "sandbox=\"allow-scripts\"")
        assertContains(html, "iframe.srcdoc=")
        assertContains(html, Base64.getEncoder().encodeToString("<p>game</p>".encodeToByteArray()))
        assertFalse(html.contains("iframe.src = "))
        assertContains(shell.csp, "frame-src 'self';")
    }

    @Test
    fun aWebsiteNavigatesItsOwnOrigin() {
        val origin = NappletWebContract.appOrigin("n1")
        val shell = NappletWebContract.shell(shellHtml, HostProfile.WEBSITE, origin, null)
        val html = shell.html.decodeToString()
        assertContains(html, "sandbox=\"allow-scripts allow-same-origin\"")
        assertContains(html, "iframe.src = '$origin/';")
        assertFalse(html.contains("srcdoc="))
        assertContains(shell.csp, "frame-src $origin;")
    }

    /** The shell as it was built before it moved to bytes: base64 of the joined artifact, then string replaces. */
    private fun oldShell(
        origin: String,
        artifact: ByteArray,
        head: String,
    ): String {
        val encoded = Base64.getEncoder().encodeToString(artifact)
        val bootstrap =
            "bindOneDocument();var b=atob('$encoded'),u=new Uint8Array(b.length);" +
                "for(var j=0;j<b.length;j++)u[j]=b.charCodeAt(j);" +
                "iframe.srcdoc=new TextDecoder('utf-8').decode(u);"
        return shellHtml
            .decodeToString()
            .replace(NappletWebContract.APP_ORIGIN_PLACEHOLDER, origin)
            .replace(NappletWebContract.APP_SANDBOX_PLACEHOLDER, "allow-scripts")
            .replace(NappletWebContract.APP_BOOTSTRAP_PLACEHOLDER, bootstrap)
            .replaceFirst("<head>", "<head>$head")
    }

    @Test
    fun theByteBuiltShellMatchesTheOldOneForEveryCarry() {
        val origin = NappletWebContract.appOrigin("n1")
        val random = java.util.Random(7)
        // Every split of 0-2 leftover bytes between the prelude and the artifact, and both empty.
        for (preludeLength in 0..5) {
            for (artifactLength in 0..7) {
                val prelude = ByteArray(preludeLength).also(random::nextBytes)
                val artifact = ByteArray(artifactLength).also(random::nextBytes)
                val built = NappletWebContract.shell(shellHtml, HostProfile.NAPPLET, origin, listOf(prelude, artifact), "<script>b</script>")
                assertEquals(oldShell(origin, prelude + artifact, "<script>b</script>"), built.html.decodeToString(), "prelude $preludeLength, artifact $artifactLength")
            }
        }
    }

    @Test
    fun thePreludeIsAPrefixOfTheInjectedPage() {
        val page = "<p>ünïcode</p>".encodeToByteArray()
        val injected = NappletWebContract.injectPrelude(page, "/*shim*/", listOf("storage"), locked = true)
        val prefix = NappletWebContract.preludePrefix("/*shim*/", listOf("storage"), locked = true)
        assertEquals((prefix + page).toList(), injected.toList())
        assertContains(prefix.decodeToString(), NappletWebContract.APP_CSP)
    }
}
