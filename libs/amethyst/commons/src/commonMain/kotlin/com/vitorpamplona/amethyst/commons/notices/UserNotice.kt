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
package com.vitorpamplona.amethyst.commons.notices

import com.vitorpamplona.amethyst.commons.model.User

/**
 * Something headless code needs to tell the user: a typed fact about what happened, never
 * text. The GUI apps turn each one into localized text in `commonsUI`
 * (`commons.notices.ui`), through one exhaustive `when`, so a notice without a rendering does
 * not compile; `cli` can render them its own way.
 *
 * Every subtype lives in this package (a sealed hierarchy has to), which makes it the catalog
 * of everything the app tells the user from outside the UI layer.
 */
sealed interface UserNotice

/** Where headless code reports a [UserNotice]. In the GUI apps this is the toast manager. */
fun interface UserNoticeSink {
    fun notify(notice: UserNotice)
}

/** The text of a [UserNotice], for APIs that already hand plain text to their callers. */
class ResolvedNotice(
    val title: String,
    val message: String,
)

/**
 * Turns a [UserNotice] into text. For headless code whose callers expect a `(title, message)`
 * pair, like the zap rail's `onError`; everything else should hand the notice to a
 * [UserNoticeSink] and let the front end render it.
 */
fun interface UserNoticeResolver {
    suspend fun resolve(notice: UserNotice): ResolvedNotice
}

/** Words [notice] with [resolver] for a caller that takes a title, a message and the [user] it is about. */
suspend fun ((String, String, User?) -> Unit).report(
    resolver: UserNoticeResolver,
    notice: UserNotice,
    user: User?,
) {
    val text = resolver.resolve(notice)
    this(text.title, text.message, user)
}

/** Words [notice] with [resolver] for a caller that takes a title and a message. */
suspend fun ((String, String) -> Unit).report(
    resolver: UserNoticeResolver,
    notice: UserNotice,
) {
    val text = resolver.resolve(notice)
    this(text.title, text.message)
}
