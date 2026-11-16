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
package com.vitorpamplona.amethyst.cordn

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The screen half of the Marmot/cordn boundary.
 *
 * `CordnIndependenceTest` in `commons` guards the models and the protocol
 * code; this guards the Android screens, which is where the coupling would
 * actually be convenient. A cordn chat screen that imported a Marmot composable
 * would work — and would then make Marmot's UI, which is frozen, unable to
 * change without breaking cordn.
 *
 * What cordn may share is what any feature shares: the design system, the
 * theme, navigation, and generic components. The line is drawn at
 * `marmotGroup`-named code, because that is where a real coupling would land.
 *
 * Third of the three guards §3.1 of `amethyst/plans/2026-09-19-cordn-ui.md`
 * asks for, added with the first screen rather than after the fifth.
 */
class CordnScreenIndependenceTest {
    /**
     * The chats screen packages, in the app and in `commonsUI`: screens move from the first to the
     * second one by one, and the guard has to follow them.
     */
    private val screensRoots: List<File> by lazy {
        // Resolved, not assumed: a wrong root makes an architecture test pass
        // for the wrong reason, which is worse than not having one.
        val repo =
            generateSequence(File(".").absoluteFile) { it.parentFile }
                .firstOrNull { File(it, "commonsUI").isDirectory && File(it, "amethyst").isDirectory }
                ?: error("cannot locate the repository root from ${File(".").absolutePath}")
        listOf(
            "amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/chats",
            "commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/screen/loggedIn/chats",
        ).map { File(repo, it) }
            .filter { it.isDirectory }
            .ifEmpty { error("cannot locate the chats screen packages under $repo") }
    }

    private fun kotlinFilesIn(pkg: String): List<File> =
        screensRoots.flatMap { root ->
            File(root, pkg)
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .toList()
        }

    private fun importsMatching(
        pkg: String,
        forbidden: Regex,
    ): List<String> =
        kotlinFilesIn(pkg).flatMap { file ->
            file.readLines().withIndex().mapNotNull { (i, line) ->
                val trimmed = line.trimStart()
                if (trimmed.startsWith("import ") && forbidden.containsMatchIn(trimmed)) {
                    "${file.name}:${i + 1}  $trimmed"
                } else {
                    null
                }
            }
        }

    /**
     * Plain substring, NOT `\bmarmot\b`.
     *
     * The word-boundary form cannot match `…marmotGroups.MarmotGroupChatroom`
     * — a word character follows `marmot` both times, so `\b` fails and the
     * guard passes exactly the import it exists to stop. Only `import` lines
     * are scanned, so a substring is both safe and the correct test.
     */
    private val marmotImport = Regex("marmot", RegexOption.IGNORE_CASE)

    private val cordnImport = Regex("cordn", RegexOption.IGNORE_CASE)

    @Test
    fun `the matcher catches the import it exists to catch`() {
        // A guard that can never fire is indistinguishable from a codebase
        // that never violates the rule. This tells them apart.
        assertTrue(
            "the Marmot matcher would not flag a real Marmot import — the guard cannot fire",
            marmotImport.containsMatchIn("import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom"),
        )
        assertTrue(
            "the cordn matcher cannot fire either",
            cordnImport.containsMatchIn("import com.vitorpamplona.amethyst.commons.model.cordnGroups.CordnGroupChatroom"),
        )
    }

    @Test
    fun `the cordn screens do not reach into Marmot's`() {
        val offences = importsMatching("cordnGroup", marmotImport)
        assertTrue(
            "cordn screens must not import Marmot — Marmot's UI is frozen and cordn's is not\n  " +
                offences.joinToString("\n  "),
            offences.isEmpty(),
        )
    }

    @Test
    fun `Marmot's screens do not reach into cordn's`() {
        val offences = importsMatching("marmotGroup", cordnImport)
        assertTrue(
            "Marmot screens must not import cordn — cordn moves and Marmot is frozen\n  " +
                offences.joinToString("\n  "),
            offences.isEmpty(),
        )
    }

    @Test
    fun `the scan sees real files and real imports`() {
        // Without this the two tests above pass just as happily when the
        // scanner is pointed at nothing at all.
        listOf("cordnGroup", "marmotGroup").forEach {
            assertTrue(
                "found ${kotlinFilesIn(it).size} files under $it — the scan is broken, not the code",
                kotlinFilesIn(it).size >= 2,
            )
        }
        assertTrue(
            "the scanner found no amethyst imports in cordnGroup, so it would not find a marmot one either",
            importsMatching("cordnGroup", Regex("amethyst", RegexOption.IGNORE_CASE)).isNotEmpty(),
        )
    }
}
