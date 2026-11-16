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
package com.vitorpamplona.amethyst.commons.ui.note

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.fail

/**
 * Every class Quartz's `EventFactory` can build gets a card of its own in the feed.
 *
 * A kind with no branch in `RenderNoteRow` silently falls through to `RenderTextEvent`, which
 * draws the event's `content` as a note: a blank card for tag-only kinds, raw JSON or ciphertext
 * for others. Nothing else notices, so this test reads both sources and checks that each class
 * the factory constructs is matched by an `is` branch of `RenderNoteRow` or `AcceptableNote`,
 * directly or through a supertype (resolved by reflection, so a base-class branch like
 * `is DvmRequestEvent` covers its subclasses).
 *
 * Adding a kind to Quartz therefore needs a card here, or an entry in [TEXT_CARD_KINDS] saying
 * why the text card is the right one.
 */
class NoteComposeKindCoverageTest {
    companion object {
        /** Kinds whose card IS the text card: `RenderTextEvent` is written for them. */
        @Suppress("DEPRECATION") // GitReplyEvent: replaced by NIP-22, still rendered for old threads
        private val TEXT_CARD_KINDS: Set<Class<out Event>> =
            setOf(
                TextNoteEvent::class.java,
                CommentEvent::class.java,
                GitReplyEvent::class.java,
            )

        /** EventFactory.kt within quartz's sources: a `commonMain/kotlin` dir, or a sources jar (`commonMain/…`). */
        private const val EVENT_FACTORY = "com/vitorpamplona/quartz/utils/EventFactory.kt"
        private const val NOTE_COMPOSE = "commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/note/NoteCompose.kt"

        /** The functions whose `when (event)` decide which card a feed row draws. */
        private val DISPATCH_FUNCTIONS = listOf("RenderNoteRow", "AcceptableNote")
    }

    private val repoRoot: File by lazy {
        // Resolved rather than assumed: a wrong root would read nothing and pass for the wrong reason.
        generateSequence(File(".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, NOTE_COMPOSE).isFile }
            ?: fail("cannot locate the repository root from ${File(".").absolutePath}")
    }

    /** quartz lives in its own repo; commonsUI's build passes its sources (checkout dir or sources jar). */
    private val eventFactorySource: String by lazy {
        val path = System.getProperty("quartz.sources") ?: fail("quartz.sources is not set; run this test through Gradle")
        val sources = File(path)
        if (sources.isDirectory) {
            File(sources, EVENT_FACTORY).readText()
        } else {
            ZipFile(sources).use { zip ->
                val entry = zip.getEntry("commonMain/$EVENT_FACTORY") ?: fail("$path has no commonMain/$EVENT_FACTORY")
                zip.getInputStream(entry).use { it.readBytes().decodeToString() }
            }
        }
    }

    private val loader = javaClass.classLoader

    @Test
    fun everyFactoryClassHasACard() {
        val factoryClasses = factoryClasses()
        val branchClasses = dispatchBranchClasses()

        // Guards against a parser that silently stopped matching: both lists run to the hundreds.
        assertTrue("only ${factoryClasses.size} EventFactory classes found", factoryClasses.size > 300)
        assertTrue("only ${branchClasses.size} dispatch branches found", branchClasses.size > 300)

        val missing =
            factoryClasses
                .filter { it !in TEXT_CARD_KINDS }
                .filter { kind -> branchClasses.none { it.isAssignableFrom(kind) } }
                .map { it.name }
                .sorted()

        if (missing.isNotEmpty()) {
            fail(
                "These EventFactory classes have no branch in ${DISPATCH_FUNCTIONS.joinToString()} " +
                    "and would render as a plain text note. Give them a card, or add them to TEXT_CARD_KINDS:\n  " +
                    missing.joinToString("\n  "),
            )
        }
    }

    @Test
    fun textCardKindsAreStillNeeded() {
        val factoryClasses = factoryClasses()
        val branchClasses = dispatchBranchClasses()

        TEXT_CARD_KINDS.forEach { kind ->
            assertTrue("${kind.name} is no longer built by EventFactory; drop it from TEXT_CARD_KINDS", kind in factoryClasses)
            val branch = branchClasses.firstOrNull { it.isAssignableFrom(kind) }
            assertTrue("${kind.name} now has a card (is ${branch?.simpleName}); drop it from TEXT_CARD_KINDS", branch == null)
        }
    }

    /** The classes `EventFactory.create` constructs: every `Foo(id, …)` in its body, `Event` itself excluded. */
    private fun factoryClasses(): Set<Class<*>> {
        val source = eventFactorySource
        val start = source.indexOf("fun <T : Event> create(")
        val end = source.indexOf("fun isKnownKind(", start)
        assertTrue("EventFactory.create not found", start >= 0 && end > start)

        val imports = imports(source)
        return Regex("""\b([A-Z]\w*)\(id\b""")
            .findAll(stripComments(source.substring(start, end)))
            .map { it.groupValues[1] }
            .filter { it != "Event" }
            .toSet()
            .map { name ->
                val fqn = imports[name] ?: "com.vitorpamplona.quartz.utils.$name"
                load(fqn) ?: fail("EventFactory builds $name but $fqn does not load")
            }.toSet()
    }

    /** The classes named by `is Foo` (not `!is`) in the dispatch functions of NoteCompose.kt. */
    private fun dispatchBranchClasses(): List<Class<*>> {
        val source = File(repoRoot, NOTE_COMPOSE).readText()
        val imports = imports(source)
        val bodies = DISPATCH_FUNCTIONS.joinToString("\n") { functionBody(source, it) }

        return Regex("""(?<![!\w])is\s+([A-Z]\w*)""")
            .findAll(stripComments(bodies))
            .mapNotNull { imports[it.groupValues[1]] }
            .distinct()
            .mapNotNull(::load)
            .toList()
    }

    private fun load(fqn: String): Class<*>? =
        try {
            Class.forName(fqn, false, loader)
        } catch (_: ClassNotFoundException) {
            null
        }

    /** Simple name (or alias) to fully-qualified name. */
    private fun imports(source: String): Map<String, String> =
        Regex("""^import\s+([\w.]+)(?:\s+as\s+(\w+))?""", RegexOption.MULTILINE)
            .findAll(source)
            .associate { m ->
                val fqn = m.groupValues[1]
                (m.groupValues[2].ifEmpty { fqn.substringAfterLast('.') }) to fqn
            }

    /** The `{ … }` body of the top-level function [name], found by brace matching. */
    private fun functionBody(
        source: String,
        name: String,
    ): String {
        val declaration = Regex("""\bfun $name\(""").find(source) ?: fail("fun $name not found in NoteCompose.kt")

        // Skip the parameter list, whose default values may hold braces of their own.
        var i = declaration.range.last
        var parens = 1
        while (parens > 0) {
            i++
            when (source[i]) {
                '(' -> parens++
                ')' -> parens--
            }
        }

        val open = source.indexOf('{', i)
        var depth = 0
        for (j in open until source.length) {
            when (source[j]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(open, j + 1)
            }
        }
        fail("unbalanced braces in fun $name")
        return ""
    }

    // `(?<!:)` keeps the `//` of a URL inside a string literal.
    private fun stripComments(code: String): String =
        code
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?<!:)//[^\n]*"""), "")
}
