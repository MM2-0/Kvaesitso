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

import com.vitorpamplona.amethyst.commons.util.booleanOrNull
import com.vitorpamplona.amethyst.commons.util.doubleOrNull
import com.vitorpamplona.amethyst.commons.util.intOrNull
import com.vitorpamplona.amethyst.commons.util.objectOrNull
import com.vitorpamplona.amethyst.commons.util.parseJsonObjectOrNull
import com.vitorpamplona.amethyst.commons.util.stringOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Parses the `geom` object of an `ime.pagesel` payload into a [SelectionGeometry], or null if absent. */
fun parseSelectionGeometry(o: JsonObject?): SelectionGeometry? {
    if (o == null) return null
    return SelectionGeometry(
        left = (o.doubleOrNull("l") ?: 0.0).toFloat(),
        top = (o.doubleOrNull("t") ?: 0.0).toFloat(),
        right = (o.doubleOrNull("r") ?: 0.0).toFloat(),
        bottom = (o.doubleOrNull("b") ?: 0.0).toFloat(),
        startX = (o.doubleOrNull("sx") ?: 0.0).toFloat(),
        startBottom = (o.doubleOrNull("sb") ?: 0.0).toFloat(),
        endX = (o.doubleOrNull("ex") ?: 0.0).toFloat(),
        endBottom = (o.doubleOrNull("eb") ?: 0.0).toFloat(),
        viewportWidth = (o.doubleOrNull("vw") ?: 0.0).toFloat(),
        caretX = o.doubleOrNull("cx")?.toFloat(),
        caretTop = o.doubleOrNull("ct")?.toFloat(),
        caretBottom = o.doubleOrNull("cb")?.toFloat(),
        isRange = o.booleanOrNull("rng") ?: false,
    )
}

/**
 * A cross-process editable: the focused field lives in the embedded WebView (a different process/window
 * that can't host the soft keyboard), so the main app keeps the keyboard and relays editing across this
 * bridge. The host view ([RemoteImeView]) drives the keyboard from [ImeEvent]s and forwards every edit
 * op back via [sendImeOp]. Implemented by the controllers whose surface can take text input.
 */
interface EmbeddedImeBridge {
    /** Page → host: a field gained focus, the field blurred, or its text changed outside the keyboard. */
    var onImeEvent: ((ImeEvent) -> Unit)?

    /** Host → page: a single IME op as a JSON envelope (`{type:"ime.commit", ...}`). */
    fun sendImeOp(json: String)
}

/**
 * Asks the page to re-announce its focused field (as an [ImeEvent.ReFocus]). A warm tab keeps the page's DOM
 * focus while it sits off-screen, so no `focusin` ever fires for it again and this is the only way the host
 * learns there is a field to put the keyboard back on. Sent when a tab becomes the active one again, and when
 * an [ImeEvent.WantKeyboard] tap arrives for a field this host no longer mirrors.
 */
fun EmbeddedImeBridge.requestImeResync() = sendImeOp(buildJsonObject { put("type", "ime.resync") }.toString())

/** Parses one page → host `ime.*` envelope into an [ImeEvent], or null for anything unrecognized. */
fun parseImeEvent(payload: String): ImeEvent? {
    val o = parseJsonObjectOrNull(payload) ?: return null
    return when (o.stringOrNull("type")) {
        "ime.focus" -> parseFocus(o)
        "ime.wantkb" -> ImeEvent.WantKeyboard
        "ime.refocus" -> ImeEvent.ReFocus(parseFocus(o))
        "ime.blur" -> ImeEvent.Blur
        "ime.state" ->
            ImeEvent.State(
                text = o.stringOrNull("text") ?: "",
                selStart = o.intOrNull("selStart") ?: 0,
                selEnd = o.intOrNull("selEnd") ?: 0,
                geometry = parseSelectionGeometry(o.objectOrNull("geom")),
            )
        "ime.pagesel" ->
            ImeEvent.PageSelection(
                active = o.booleanOrNull("active") ?: false,
                text = o.stringOrNull("text") ?: "",
                geometry = parseSelectionGeometry(o.objectOrNull("geom")),
            )
        "ime.scroll" -> ImeEvent.Scroll(active = o.booleanOrNull("active") ?: false)
        "ime.carettap" -> ImeEvent.CaretTap(geometry = parseSelectionGeometry(o.objectOrNull("geom")))
        else -> null
    }
}

private fun parseFocus(o: JsonObject) =
    ImeEvent.Focus(
        inputType = o.stringOrNull("inputType") ?: "text",
        enterKeyHint = o.stringOrNull("enterKeyHint") ?: "",
        multiline = o.booleanOrNull("multiline") ?: false,
        readOnly = o.booleanOrNull("readOnly") ?: false,
        hasNext = o.booleanOrNull("hasNext") ?: false,
        text = o.stringOrNull("text") ?: "",
        selStart = o.intOrNull("selStart") ?: 0,
        selEnd = o.intOrNull("selEnd") ?: 0,
        geometry = parseSelectionGeometry(o.objectOrNull("geom")),
    )

/** What the focused page field reports up to the host keyboard. */
sealed interface ImeEvent {
    /** A field took focus; carries enough to configure the keyboard and seed the editing buffer. */
    data class Focus(
        val inputType: String,
        val enterKeyHint: String,
        val multiline: Boolean,
        /**
         * The field is `readonly`: focusable and selectable, but not typeable. Native Chrome focuses such a
         * field without raising the keyboard, so the host must not either — otherwise the user gets a keyboard
         * whose keystrokes the page discards.
         */
        val readOnly: Boolean = false,
        /**
         * Another text field follows this one in its form. Chrome then gives a single-line field the keyboard's
         * "Next" action, which moves focus there instead of pressing Enter (which would submit the form).
         */
        val hasNext: Boolean = false,
        val text: String,
        val selStart: Int,
        val selEnd: Int,
        val geometry: SelectionGeometry? = null,
    ) : ImeEvent

    /**
     * The user tapped inside a field that is **already** focused in the page: put the keyboard back up. No
     * focus event follows a tap on a field that never blurred, so this is the only signal that the user wants
     * the keyboard back after dismissing it — or after a tab switch released the host's mirror.
     *
     * Deliberately payload-free (it fires on every tap in a field): a host that no longer mirrors the field
     * answers it with an [requestImeResync] and takes the state from the [ReFocus] that comes back.
     */
    data object WantKeyboard : ImeEvent

    /**
     * The page's answer to [requestImeResync]: the editing state of a field that is already focused there.
     * Distinct from [Focus] because page focus never changed — the host must not restart the input on a field
     * it is already mirroring (that would kill a live composing region mid-word).
     *
     * Carries no geometry: measuring a caret rect forces a synchronous layout in the page, and the host has no
     * use for it here (the `ime.carettap` that rides along with a tap carries fresh geometry).
     */
    data class ReFocus(
        val focus: Focus,
    ) : ImeEvent

    /** The field lost focus — dismiss the keyboard. */
    data object Blur : ImeEvent

    /** The page changed the field's text/selection itself; resync the keyboard's buffer. */
    data class State(
        val text: String,
        val selStart: Int,
        val selEnd: Int,
        val geometry: SelectionGeometry? = null,
    ) : ImeEvent

    /**
     * Ordinary page text (not an input) gained or lost a selection. The embedded WebView can't present
     * Chrome's copy toolbar/handles in its cross-process surface, so the host draws its own over the page;
     * [text] is the selected text to put on the clipboard, [geometry] places the toolbar + drag handles.
     */
    data class PageSelection(
        val active: Boolean,
        val text: String,
        val geometry: SelectionGeometry?,
    ) : ImeEvent

    /**
     * The embedded page started ([active] true) or finished ([active] false) scrolling. Host-drawn selection
     * UI hides while scrolling (its geometry is stale mid-scroll) and reappears, repositioned, on settle —
     * the shim re-reports geometry just before the `active=false` so the overlays land in the right place.
     */
    data class Scroll(
        val active: Boolean,
    ) : ImeEvent

    /**
     * The user tapped the focused field, placing a bare caret. Re-shows the insertion handle even when the
     * tap didn't move the caret (so no [State] fired) — native shows the handle on every tap. [geometry]
     * carries the caret rect. Gated host-side to non-empty fields, like `Editor`.
     */
    data class CaretTap(
        val geometry: SelectionGeometry?,
    ) : ImeEvent
}

/**
 * Selection geometry in the page's CSS px (viewport coords). The host scales by surface-width / [viewportWidth]
 * to screen px. [left]/[top]/[right]/[bottom] is the bounding box (toolbar anchors above it); ([startX],
 * [startBottom]) and ([endX], [endBottom]) are the caret feet where the two drag handles sit.
 */
data class SelectionGeometry(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val startX: Float,
    val startBottom: Float,
    val endX: Float,
    val endBottom: Float,
    val viewportWidth: Float,
    // Present only when the field's selection is a bare caret (no range): the caret rect, so the host can
    // show a draggable insertion handle below it. Null for a range or page-text selection.
    val caretX: Float? = null,
    val caretTop: Float? = null,
    val caretBottom: Float? = null,
    // True when this is an in-field *range* selection whose [startX]/[startBottom]/[endX]/[endBottom] are the
    // real selection-endpoint feet (not the field-box placeholders). Lets the host hold the range geometry
    // through Chrome's transient collapse-to-caret reports during the re-assert fight.
    val isRange: Boolean = false,
)
