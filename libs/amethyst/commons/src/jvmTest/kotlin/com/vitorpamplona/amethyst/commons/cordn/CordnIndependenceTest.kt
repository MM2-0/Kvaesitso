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
package com.vitorpamplona.amethyst.commons.cordn

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The app half of the Marmot/cordn boundary.
 *
 * The protocol layers are two libraries, marmot-kmp and cordn-kmp, and neither
 * depends on the other, so the build graph holds them apart. This holds the
 * layer above them apart, and the rule is the same and deliberate: **Marmot is
 * frozen, and neither binding may acquire a reason to change when the other
 * does.** The two protocols do not interoperate and neither is a layer of the
 * other, so a shared model, state holder or chatroom type would be a coupling
 * with nothing to justify it.
 *
 * What cordn *may* share is what any feature shares: the design system, the
 * theme, and generic components — buttons, avatars, text fields, QR, media
 * viewers. Those are app furniture, not group chat. The line this test draws is
 * `marmot`-named code, because that is where the coupling would actually land.
 */
class CordnIndependenceTest {
    private val commonsRoot: File by lazy {
        // Resolved rather than assumed: a wrong root makes an architecture test
        // pass for the wrong reason, which is worse than not having one.
        generateSequence(File(".").absoluteFile) { it.parentFile }
            .map { if (it.name == "commons") it else File(it, "commons") }
            .firstOrNull { File(it, "src/commonMain/kotlin/com/vitorpamplona/amethyst/commons").isDirectory }
            ?: fail("cannot locate the commons module from ${File(".").absolutePath}")
    }

    /** Main source sets only; interop tests may legitimately drive both. */
    private fun kotlinFilesIn(pkg: String): List<File> =
        File(commonsRoot, "src")
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && !it.name.endsWith("Test") }
            .map { File(it, "kotlin/com/vitorpamplona/amethyst/commons/$pkg") }
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList() }

    private fun importsMatching(
        pkg: String,
        forbidden: Regex,
    ): List<String> =
        kotlinFilesIn(pkg).flatMap { file ->
            file.readLines().withIndex().mapNotNull { (i, line) ->
                val trimmed = line.trimStart()
                if (trimmed.startsWith("import ") && forbidden.containsMatchIn(trimmed)) {
                    "${file.relativeTo(commonsRoot)}:${i + 1}  $trimmed"
                } else {
                    null
                }
            }
        }

    /**
     * Plain substring, NOT `\bmarmot\b`.
     *
     * The word-boundary form cannot match the imports that matter: in
     * `…model.marmotGroups.MarmotGroupChatroom` the character after `marmot`
     * is a word character both times, so `\b` fails and the guard waves
     * through exactly the dependency it exists to stop. This guard shipped
     * with that bug and was only caught by trying to break it on purpose —
     * which is why [the matcher itself is tested] below.
     *
     * A substring is safe here because only `import` lines are scanned: the
     * word "marmot" inside an import IS a Marmot dependency.
     */
    private val marmotImport = Regex("marmot", RegexOption.IGNORE_CASE)

    private val cordnImport = Regex("cordn", RegexOption.IGNORE_CASE)

    @Test
    fun `the matcher catches the import it exists to catch`() {
        // The meta-test. Without it, a guard that can never fire looks exactly
        // like a codebase that never violates the rule.
        val realImport = "import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom"
        assertTrue(
            marmotImport.containsMatchIn(realImport),
            "the Marmot matcher would not flag $realImport — the guard cannot fire",
        )
        assertTrue(
            cordnImport.containsMatchIn("import com.vitorpamplona.cordn.client.CordnGroupManager"),
            "the cordn matcher cannot fire either",
        )
    }

    @Test
    fun `cordn does not reach into Marmot`() {
        val offences = importsMatching("cordn", marmotImport)
        assertTrue(
            offences.isEmpty(),
            "cordn must not import Marmot — the two are independent and Marmot is frozen\n  " +
                offences.joinToString("\n  "),
        )
    }

    @Test
    fun `Marmot does not reach into cordn`() {
        // The direction that protects the shipped feature: Marmot must not gain
        // a dependency on the newer, less settled binding.
        val offences = importsMatching("marmot", cordnImport)
        assertTrue(
            offences.isEmpty(),
            "Marmot must not import cordn — Marmot is frozen and cordn moves\n  " + offences.joinToString("\n  "),
        )
    }

    @Test
    fun `the scan sees real files and real imports`() {
        listOf("cordn", "marmot").forEach {
            assertTrue(kotlinFilesIn(it).size > 3, "found ${kotlinFilesIn(it).size} files under $it — the scan is broken, not the code")
        }
        assertTrue(
            importsMatching("cordn", Regex("quartz", RegexOption.IGNORE_CASE)).isNotEmpty(),
            "the scanner found no quartz imports in cordn, so it would not find a marmot one either",
        )
    }
}
