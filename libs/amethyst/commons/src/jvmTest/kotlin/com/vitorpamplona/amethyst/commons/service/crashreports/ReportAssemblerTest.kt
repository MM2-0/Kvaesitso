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
package com.vitorpamplona.amethyst.commons.service.crashreports

import org.junit.Assert.assertTrue
import org.junit.Test

class ReportAssemblerTest {
    @Test
    fun keepsAHugeExceptionMessageFromSwallowingTheReport() {
        // A navigation failure quotes the whole route it could not match, and that route can hold
        // the previous report: left uncapped, every crash report would embed the last one.
        val hugeMessage = "Navigation destination that matches route " + "%20".repeat(50_000)
        val e = IllegalArgumentException(hugeMessage)
        e.stackTrace = arrayOf(StackTraceElement("com.example.Foo", "bar", "Foo.kt", 42))

        val report = ReportAssembler().buildReport(e, "main")

        assertTrue(report.length < 2_000)
        assertTrue(report.startsWith("java.lang.IllegalArgumentException: "))
        assertTrue(report.contains("Navigation destination that matches route"))
        assertTrue(report.trimEnd().endsWith("```"))
    }

    @Test
    fun keepsShortMessagesAndTheStackTraceWhole() {
        val e = RuntimeException("boom", IllegalStateException("root cause"))
        e.stackTrace = arrayOf(StackTraceElement("com.example.Foo", "bar", "Foo.kt", 42))

        val report = ReportAssembler().buildReport(e, "main")

        assertTrue(report.contains("java.lang.RuntimeException: boom"))
        // The "at " prefix is load-bearing: retrace only rewrites frames it recognises.
        assertTrue(report.contains("    at com.example.Foo.bar(Foo.kt:42)"))
        assertTrue(report.contains("java.lang.IllegalStateException: root cause"))
    }

    @Test
    fun headlineNamesTheExceptionClassInFullSoItCanBeRetraced() {
        // The headline is the line a maintainer skims and dedups on. Release builds are
        // obfuscated, and `retrace` can only restore a class name it can resolve — a bare
        // simple name has no package, so the headline has to carry the fully qualified one.
        val e = IllegalStateException("boom")
        e.stackTrace = arrayOf(StackTraceElement("com.example.Foo", "bar", "Foo.kt", 42))

        val report = ReportAssembler().buildReport(e, "main")

        assertTrue(report.startsWith("java.lang.IllegalStateException: "))
    }
}
