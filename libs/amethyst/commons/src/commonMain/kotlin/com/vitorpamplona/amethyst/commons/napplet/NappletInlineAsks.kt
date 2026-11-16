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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The inline napplets (running inside a note) that wanted to ask the user something. The broker
 * refuses such a request without a dialog, so scrolling the feed never pops one, and records it
 * here so the note can offer to open the app, where the question can be asked.
 *
 * Keyed by the instance id the launcher gave the inline run.
 */
class NappletInlineAsks {
    private val asked = MutableStateFlow<Set<String>>(emptySet())

    /** [app] wanted the user. An app without an instance id can't be told apart, so it isn't recorded. */
    fun record(app: NappletIdentity) {
        val id = app.instanceId ?: return
        asked.update { if (id in it) it else it + id }
    }

    /** Whether the inline run [instanceId] has wanted the user. */
    fun asked(instanceId: String): Flow<Boolean> = asked.map { instanceId in it }.distinctUntilChanged()

    /** The inline run [instanceId] ended. */
    fun forget(instanceId: String) = asked.update { it - instanceId }

    companion object {
        /** The app's one record: the broker and the notes share it. */
        val shared = NappletInlineAsks()
    }
}
