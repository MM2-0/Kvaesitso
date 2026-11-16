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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * The shell page's own contents, asserted where the resource can actually be read.
 *
 * This lives in `jvmTest` rather than `commonTest` because [NappletWebResources.shellHtml] goes
 * through Compose Resources, and that reader needs an initialised Android `Context` on the
 * android target. A host unit test has none, so running this in `commonTest` failed
 * `:commons:testAndroidHostTest` with `MissingResourceException` while passing `:commons:jvmTest`
 * — the same assertion, red or green depending only on which target ran it.
 *
 * Nothing about the assertions is platform-specific: `shell.html` is one shared file, so reading
 * it once on the JVM checks its contents everywhere. What is *not* covered here is the Android
 * resource plumbing itself, which needs a `Context` — an instrumented test, or Robolectric, which
 * commons does not use.
 *
 * The rest of the contract's tests stay in `commonTest` and still run on both targets; they never
 * touch a resource.
 */
class NappletShellResourceTest {
    @Test
    fun shellTemplateKeepsTheOpaqueIframeAndSourceChecks() =
        runTest {
            val shell = NappletWebResources.shellHtml().decodeToString()

            assertContains(shell, "sandbox=\"${NappletWebContract.APP_SANDBOX_PLACEHOLDER}\"")
            assertContains(shell, NappletWebContract.APP_BOOTSTRAP_PLACEHOLDER)
            assertContains(shell, "e.source !== iframe.contentWindow")
            // NAP rides the port handed over in napplet.hello, never the frame's Window.
            assertContains(shell, "e.data.type === 'napplet.hello' && e.ports && e.ports.length === 1")
            assertContains(shell, "port.postMessage(msg)")
            assertFalse(shell.contains("iframe.contentWindow.postMessage"))
            assertContains(shell, "function bindOneDocument()")
            assertFalse(shell.contains("allow-same-origin"))
        }
}
