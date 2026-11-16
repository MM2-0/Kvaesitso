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
package com.vitorpamplona.amethyst

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Prints every coroutine failure that reaches the JVM-wide handler during a unit-test run,
 * together with the thread and coroutine context it came from.
 *
 * Registered through `META-INF/services/kotlinx.coroutines.CoroutineExceptionHandler`, so
 * `kotlinx.coroutines` hands it any exception no `CoroutineExceptionHandler` in the failing
 * coroutine's own context claimed — i.e. exactly the ones leaked by a scope built as
 * `CoroutineScope(Dispatchers.IO + SupervisorJob())` and never cancelled.
 *
 * Why this is worth a file: every test class in this module shares one JVM, and
 * `kotlinx-coroutines-test` installs a global handler of its own that *collects* such
 * exceptions. One arriving while no test is running is replayed into the next `runTest` to
 * start — which fails as `UncaughtExceptionsBeforeTest` naming a test that had nothing to do
 * with it, and passes when run alone. Without this reporter the only record of the real
 * culprit is a suppressed exception buried in one report; with it, the leak is printed at the
 * moment it happens, under the class that was actually running.
 *
 * Deliberately only reports: it does not signal the exception as handled, so
 * `kotlinx-coroutines-test` still surfaces it and no genuine failure is swallowed.
 *
 * `ServiceLoader` order decides whether this runs before or after that collector, which only
 * matters while a test is running — there the collector claims the exception and fails that very
 * test with its own stack trace, so nothing is lost either way. Between tests the collector
 * claims nothing, which is precisely the case this reporter exists for.
 */
class LeakedCoroutineReporter :
    AbstractCoroutineContextElement(CoroutineExceptionHandler),
    CoroutineExceptionHandler {
    override fun handleException(
        context: CoroutineContext,
        exception: Throwable,
    ) {
        System.err.println(
            "LEAKED COROUTINE EXCEPTION — a coroutine failed outside any test's scope.\n" +
                "  thread:  ${Thread.currentThread().name}\n" +
                "  context: $context\n" +
                "  The test class printed alongside this is the one that was running, NOT " +
                "necessarily the one that started the coroutine. Whichever test built the " +
                "scope in the stack trace below must cancel it before it finishes.",
        )
        exception.printStackTrace()
    }
}
