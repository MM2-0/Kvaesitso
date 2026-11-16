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
package com.vitorpamplona.amethyst.napplet

import android.content.Context
import android.net.Uri

/**
 * Runs a NAP-FS picker (`fs.pickFile`, `pickFiles`, `pickSaveFile`) for the broker, which lives in a
 * Service and has no Activity to start one from: [PickerHandoff] gives the request to
 * [NappletFsPickerActivity], which shows the system's document picker.
 *
 * The pickers are the Storage Access Framework's (`ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`):
 * their grant belongs to the app, not to the Activity that asked, so the files stay readable from the
 * broker's Service after the picker host has finished.
 */
object NappletFsPickerCoordinator {
    enum class Kind {
        OPEN,
        OPEN_MULTIPLE,
        SAVE,
    }

    /**
     * One picker to show: [accept] holds the napplet's advisory filters as HTML `accept` tokens (MIME
     * types or `.ext`; empty shows everything), [title] names a new file.
     */
    class Ask(
        val kind: Kind,
        val accept: List<String>,
        val title: String?,
        val wantsWrite: Boolean,
    )

    val handoff = PickerHandoff<Ask, List<Uri>>(NappletFsPickerActivity::class.java, "NappletFsPicker")

    /** Shows the picker and returns what the user chose, or null when they cancelled or no picker could run. */
    suspend fun pick(
        context: Context,
        kind: Kind,
        accept: List<String>,
        title: String?,
        wantsWrite: Boolean,
    ): List<Uri>? = handoff.await(context, Ask(kind, accept, title, wantsWrite), START_TIMEOUT_MS)

    /** How long the picker host may take to come up before the pick ends as nothing chosen. */
    private const val START_TIMEOUT_MS = 10_000L
}
