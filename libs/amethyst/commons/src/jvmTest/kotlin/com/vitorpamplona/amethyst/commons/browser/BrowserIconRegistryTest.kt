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
package com.vitorpamplona.amethyst.commons.browser

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The favicon store's disk behaviour. Untestable while it was an Android `object` taking a `Context`
 * for its `filesDir`; taking `iconDir: () -> Path` is what opens it up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserIconRegistryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var seq = 0

    private fun newDir(): File = folder.newFolder("icons_${seq++}")

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    /** One app "session" over [dir], on the test scheduler so [advanceUntilIdle] drives its disk work. */
    private fun TestScope.registryOver(dir: File) = BrowserIconRegistry({ dir.toOkioPath() }, CoroutineScope(coroutineContext + Job()))

    @Test
    fun aRecordedIconBecomesAvailableAndReachesDisk() =
        runTest {
            val dir = newDir()
            val registry = registryOver(dir)
            registry.init()
            advanceUntilIdle()

            registry.record("example.com", png)
            advanceUntilIdle()

            assertEquals("the host is announced", setOf("example.com"), registry.keys.value)
            assertEquals(
                "and the model points at the file",
                "file://" + File(dir, "example.com.png").absolutePath,
                registry.iconModelFor("example.com"),
            )
            assertTrue("which exists", File(dir, "example.com.png").exists())
            assertEquals("with the bytes given", png.toList(), File(dir, "example.com.png").readBytes().toList())
        }

    /** A cold start has to find what earlier sessions stored, or every icon redownloads on first paint. */
    @Test
    fun iconsAlreadyOnDiskAreIndexedByInit() =
        runTest {
            val dir = newDir()
            File(dir, "already.example.png").writeBytes(png)

            val registry = registryOver(dir)
            assertTrue("nothing is known before init", registry.keys.value.isEmpty())

            registry.init()
            advanceUntilIdle()

            assertEquals("the stored icon is indexed", setOf("already.example"), registry.keys.value)
        }

    /**
     * [BrowserIconRegistry.iconModelFor] is read from composition, so it must answer from [keys] rather
     * than touch the filesystem — a host with no icon is null, not a path to a file that is not there.
     */
    @Test
    fun aHostWithNoStoredIconHasNoModel() =
        runTest {
            val registry = registryOver(newDir())
            registry.init()
            advanceUntilIdle()

            assertNull(registry.iconModelFor("never-visited.example"))
        }

    /** Host keys become one flat filename, so a port or an uppercase host cannot escape the directory. */
    @Test
    fun hostsAreSanitizedIntoASingleFlatFilename() =
        runTest {
            val dir = newDir()
            val registry = registryOver(dir)
            registry.init()
            advanceUntilIdle()

            registry.record("Example.COM:8080/../etc", png)
            advanceUntilIdle()

            assertEquals(
                "lowercased, and everything but letters/digits/dot/dash replaced",
                setOf("example.com_8080_.._etc"),
                registry.keys.value,
            )
            assertEquals(
                "one file, directly in the icon dir",
                listOf("example.com_8080_.._etc.png"),
                dir.listFiles()?.map { it.name },
            )
        }

    /** Lookups are sanitized the same way, so the caller passes the raw host and still finds it. */
    @Test
    fun aLookupSanitizesTheHostTheSameWay() =
        runTest {
            val dir = newDir()
            val registry = registryOver(dir)
            registry.init()
            advanceUntilIdle()

            registry.record("Example.COM", png)
            advanceUntilIdle()

            assertEquals(
                "the raw host resolves to the sanitized file",
                "file://" + File(dir, "example.com.png").absolutePath,
                registry.iconModelFor("Example.COM"),
            )
        }

    @Test
    fun aBlankHostOrEmptyBytesAreIgnored() =
        runTest {
            val dir = newDir()
            val registry = registryOver(dir)
            registry.init()
            advanceUntilIdle()

            registry.record("   ", png)
            registry.record("example.com", ByteArray(0))
            advanceUntilIdle()

            assertTrue("nothing announced", registry.keys.value.isEmpty())
            assertEquals("nothing written", emptyList<String>(), dir.listFiles()?.map { it.name })
        }

    @Test
    fun aRecordedIconSurvivesARestart() =
        runTest {
            val dir = newDir()

            val first = registryOver(dir)
            first.init()
            advanceUntilIdle()
            first.record("example.com", png)
            advanceUntilIdle()

            val second = registryOver(dir)
            second.init()
            advanceUntilIdle()

            assertEquals("indexed again from disk", setOf("example.com"), second.keys.value)
        }

    /** The icon dir need not exist yet: a first run must create it rather than drop the icon. */
    @Test
    fun aMissingIconDirectoryIsCreated() =
        runTest {
            val dir = File(folder.root, "not_yet_${seq++}")
            val registry = registryOver(dir)
            registry.init()
            advanceUntilIdle()

            registry.record("example.com", png)
            advanceUntilIdle()

            assertTrue("the directory was created", dir.isDirectory)
            assertEquals("and the icon landed in it", setOf("example.com"), registry.keys.value)
        }
}
