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
package com.vitorpamplona.amethyst.napplet

import android.content.Context
import android.content.Intent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertNull
import org.junit.Test

class NappletLauncherTest {
    private val context = mockk<Context>(relaxed = true)
    private val manifest = mockk<NappletManifest>()
    private val author = "aa".repeat(32)

    @Test
    fun manifestLaunchRejectsNonEventManifest() {
        NappletLauncher.launch(context, manifest, author, "demo")

        verify(exactly = 0) { context.startActivity(any<Intent>()) }
    }

    @Test
    fun embeddedBuildLaunchParamsRejectsNonEventManifest() {
        assertNull(NappletLauncher.buildLaunchParams(context, manifest, author, "demo"))
    }

    // Every refusal below happens before buildLaunchParamsTrusted, which is the first step that
    // needs the main-process app (Amethyst.instance).
    private val signer = NostrSignerSync()
    private val artifact = "ab".repeat(32)

    private fun named(
        identifier: String = "demo",
        description: String = "A napplet",
        required: List<String> = emptyList(),
    ): NamedNappletEvent = signer.sign(NamedNappletEvent.build(identifier, artifact, description, required = required))

    @Test
    fun refusesAForgedSignature() {
        val signed = named()
        val forged = NamedNappletEvent(signed.id, signed.pubKey, signed.createdAt, signed.tags, "A different description", signed.sig)
        assertNull(NappletLauncher.buildLaunchParams(context, forged, forged.pubKey, "demo"))
    }

    @Test
    fun refusesAnotherAuthor() {
        assertNull(NappletLauncher.buildLaunchParams(context, named(), author, "demo"))
    }

    @Test
    fun refusesAnIdentifierMismatch() {
        val event = named()
        assertNull(NappletLauncher.buildLaunchParams(context, event, event.pubKey, "other"))
    }

    @Test
    fun refusesAnInvalidManifest() {
        val event = named(description = " ")
        assertNull(NappletLauncher.buildLaunchParams(context, event, event.pubKey, "demo"))
    }

    @Test
    fun refusesAnUnsupportedRequiredDomain() {
        val event = named(required = listOf("relay", "mesh"))
        assertNull(NappletLauncher.buildLaunchParams(context, event, event.pubKey, "demo"))
    }
}
