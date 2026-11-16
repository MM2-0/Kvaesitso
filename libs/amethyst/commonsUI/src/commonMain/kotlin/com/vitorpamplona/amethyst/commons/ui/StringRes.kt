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
package com.vitorpamplona.amethyst.commons.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Compose-resources twin of the Android app's `stringRes` family
 * (`amethyst/.../ui/StringResourceCache.kt`). Same call shape, portable
 * backing: a file migrating from the app changes only the import and swaps
 * `R.string.key` for `Res.string.key` — every `stringRes(...)` call site
 * stays as written.
 *
 * No extra cache on purpose: unlike Android's `Resources.getString` (the
 * >1 ms lookups the app-side LruCache exists for), compose-resources parses
 * each locale's string file once into a process-wide cache, so repeat
 * lookups are map hits and the library handles locale changes itself. If
 * profiling ever says otherwise, this is the one place to add it.
 *
 * Format args use the positional `%1$s` form. Compose-resources does NOT
 * understand bare `%s`/`%d` — `tools/strings-migrate` flags those at
 * migration time.
 *
 * Args are nullable because the commonest one is `Throwable.message`. A null
 * becomes an empty string rather than the literal "null" Android's
 * `Resources.getString` would have printed at the user; compose-resources
 * takes a non-null `Any` and would not accept it at all.
 */
private fun Array<out Any?>.orEmptyStrings(): Array<Any> = Array(size) { this[it] ?: "" }

@Composable
fun stringRes(id: StringResource): String = stringResource(id)

@Composable
fun stringRes(
    id: StringResource,
    vararg args: Any?,
): String = stringResource(id, *args.orEmptyStrings())

@Composable
fun pluralStringRes(
    id: PluralStringResource,
    count: Int,
): String = pluralStringResource(id, count)

@Composable
fun pluralStringRes(
    id: PluralStringResource,
    count: Int,
    vararg args: Any?,
): String = pluralStringResource(id, count, *args.orEmptyStrings())

/** Non-composable resolver (launch/onClick scopes) — twin of the `ctx`-taking overloads. */
suspend fun loadStringRes(id: StringResource): String = getString(id)

suspend fun loadStringRes(
    id: StringResource,
    vararg args: Any?,
): String = getString(id, *args.orEmptyStrings())

/** Non-composable plural resolver — twin of the app's `pluralStringRes(ctx, ...)`. */
suspend fun loadPluralStringRes(
    id: PluralStringResource,
    count: Int,
    vararg args: Any?,
): String = if (args.isEmpty()) getPluralString(id, count) else getPluralString(id, count, *args.orEmptyStrings())
