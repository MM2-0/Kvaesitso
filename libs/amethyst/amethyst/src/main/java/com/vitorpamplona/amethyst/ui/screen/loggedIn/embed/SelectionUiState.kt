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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Single source of truth for the host-drawn selection UI over an embedded surface. Folds together what used
 * to be scattered booleans/geometries (`showSelectionToolbar`, `showInsertionHandle`, `fieldGeometry`,
 * `rangeFieldGeometry`, `pageSelection`) so the show/hide rules are expressed in one place.
 *
 * Three mutually-exclusive selection contexts can be active: a bare caret in a field ([insertionGeometry]),
 * a range inside an `<input>`/`<textarea>` ([fieldRange], gated by [fieldHasRange] — the authoritative
 * "is there a selection" signal from the mirror [RemoteImeView]), or a plain page-text selection
 * ([pageSelection]). [dragging] and [scrolling] are the transient modifiers:
 *
 * - **dragging** a handle/caret hides the floating toolbar (and the dragged handle hides itself while the
 *   loupe stands in), matching Android; the other handle stays so you can see the live range.
 * - **scrolling** hides every overlay (its geometry is stale mid-scroll); the shim re-reports geometry on
 *   settle, then clears this, so they reappear in the right place.
 */
@Stable
class SelectionUiState {
    /** Latest field geometry; carries the caret rect when collapsed, positioning the insertion handle. */
    var fieldGeometry by mutableStateOf<SelectionGeometry?>(null)
        private set

    /** Held in-field range geometry (real endpoint feet) — survives Chrome's transient collapse-to-caret. */
    var fieldRange by mutableStateOf<SelectionGeometry?>(null)
        private set

    /** Authoritative "the field selection is a non-empty range", from the mirror EditText. */
    var fieldHasRange by mutableStateOf(false)
        private set

    /** A bare caret handle is showing — set by a tap-placed caret in non-empty text, cleared on typing/blur/timeout. */
    var caretShown by mutableStateOf(false)
        private set

    /**
     * Whether the focused field currently has any text. Native (`Editor.onTouchUpEvent`) only offers the
     * insertion handle when `text.length() > 0`, so an empty field never gets a draggable cursor handle
     * (just the blinking caret). Gating on this is what stops the handle popping up on focus of an empty box.
     */
    var fieldHasText by mutableStateOf(false)
        private set

    /**
     * The focused field is `readonly`. Its text can still be selected and copied — that is what native offers
     * — but nothing can be placed or changed in it, so it gets no insertion caret handle and no editing
     * actions in the toolbar.
     */
    var fieldReadOnly by mutableStateOf(false)
        private set

    /** Plain page-text (non-editable) selection. */
    var pageSelection by mutableStateOf<ImeEvent.PageSelection?>(null)
        private set

    /** A handle/caret is being dragged. */
    var dragging by mutableStateOf(false)

    /** The embedded surface is scrolling. */
    var scrolling by mutableStateOf(false)

    /** Tapping the bare insertion handle opens a small Paste/Select-All popup (native); tap-elsewhere closes it. */
    var insertionPopup by mutableStateOf(false)
        private set

    private val insertionGeometry: SelectionGeometry?
        get() = fieldGeometry?.takeIf { it.caretX != null && it.caretBottom != null && it.viewportWidth > 0f }

    // ---- derived visibility (read in composition; track the backing state) ----

    val insertionHandle: SelectionGeometry?
        get() = if (caretShown && fieldHasText && !fieldReadOnly && !fieldHasRange && !scrolling) insertionGeometry else null

    /** Geometry for the insertion-handle Paste/Select-All popup, or null when it shouldn't show. */
    val insertionPopupAt: SelectionGeometry?
        get() = if (insertionPopup && !dragging) insertionHandle else null

    val fieldHandles: SelectionGeometry?
        get() = if (fieldHasRange && !scrolling) fieldRange?.takeIf { it.viewportWidth > 0f } else null

    val fieldToolbar: SelectionGeometry?
        get() = if (fieldHasRange && !dragging && !scrolling) (fieldRange ?: fieldGeometry) else null

    private val pageGeom: SelectionGeometry?
        get() = pageSelection?.takeIf { it.active }?.geometry?.takeIf { it.viewportWidth > 0f }

    val pageHandles: SelectionGeometry?
        get() = if (!scrolling) pageGeom else null

    val pageToolbar: SelectionGeometry?
        get() = if (!dragging && !scrolling) pageGeom else null

    // ---- signal sinks ----

    fun onFieldGeometry(
        g: SelectionGeometry?,
        hasText: Boolean,
    ) {
        fieldHasText = hasText
        if (g == null) return
        fieldGeometry = g
        if (g.isRange) fieldRange = g
        // Only a tap-placed caret in NON-empty text gets the handle — like native. (Typing suppresses the
        // echo, so this fires for taps, not keystrokes; see RemoteImeView / shim __nappletIme guard.)
        if (g.caretX != null && hasText) caretShown = true
    }

    /** From [RemoteImeView.onRangeSelectionChanged]: the field gained/lost a non-empty selection. */
    fun onFieldRangeToggle(hasRange: Boolean) {
        fieldHasRange = hasRange
        if (!hasRange) fieldRange = null
        if (hasRange) insertionPopup = false // a selection formed → no insertion popup
    }

    /**
     * The user tapped the focused field (placing a caret) — (re-)show the insertion handle even if the caret
     * didn't move. Gated to non-empty text by the [insertionHandle] getter, exactly like native.
     */
    fun onCaretTap(g: SelectionGeometry?) {
        if (g != null) fieldGeometry = g
        caretShown = true
        insertionPopup = false // a tap in the text dismisses the popup (and moves the caret)
    }

    /** The user typed: hide the insertion handle, like Android. */
    fun onEdited() {
        caretShown = false
        insertionPopup = false
    }

    /** Native auto-hides the insertion handle after ~a few seconds of inactivity; a later tap re-shows it. */
    fun hideCaret() {
        caretShown = false
        insertionPopup = false
    }

    /** Tap on the bare insertion handle toggles its Paste/Select-All popup. */
    fun toggleInsertionPopup() {
        insertionPopup = !insertionPopup
    }

    fun hideInsertionPopup() {
        insertionPopup = false
    }

    fun onBlur() {
        fieldGeometry = null
        fieldRange = null
        fieldHasRange = false
        fieldHasText = false
        fieldReadOnly = false
        caretShown = false
        insertionPopup = false
    }

    /** Records whether the newly focused field is `readonly` — see [fieldReadOnly]. */
    fun onFieldReadOnly(readOnly: Boolean) {
        fieldReadOnly = readOnly
    }

    fun onPageSelection(sel: ImeEvent.PageSelection?) {
        pageSelection = sel
    }

    /** Full teardown when the active tab/controller changes. */
    fun reset() {
        onBlur()
        pageSelection = null
        dragging = false
        scrolling = false
    }
}
