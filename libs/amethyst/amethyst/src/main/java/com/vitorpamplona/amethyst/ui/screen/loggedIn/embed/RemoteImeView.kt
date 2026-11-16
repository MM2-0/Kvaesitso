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

import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The main-app-window home for the soft keyboard when a field is focused inside an embedded WebView. The
 * embedded surface is a cross-process [android.view.SurfaceControlViewHost] window that can't be an IME
 * target, so this invisible [EditText] takes the keyboard in the main window instead and relays editing
 * to the page.
 *
 * Modeled on Flutter's `TextInputPlugin`/`InputConnectionAdaptor`: a real local [Editable] is the source
 * of truth (the platform handles composing regions, suggestions, selection, spell-check, and answers
 * `getTextBeforeCursor`/`getExtractedText` for free), edits are **coalesced across batch boundaries**,
 * and the whole **editing state** — text, selection, composing region — is shipped to the page (not
 * individual ops). Going beyond Flutter (whose consumer is a Dart widget), the shim then synthesizes the
 * matching DOM `input`/composition events so web frameworks react.
 *
 * It covers the soft keyboard, hardware keyboards, autofill, paste, and context-menu edits uniformly,
 * because every one of them mutates the same [Editable] and we flush the resulting state.
 */
@Suppress("ViewConstructor", "AppCompatCustomView")
class RemoteImeView(
    context: Context,
) : EditText(context) {
    private var bridge: EmbeddedImeBridge? = null

    // True while we apply a page-side update, so the resulting local edits aren't echoed back (loop).
    private var applyingRemote = false

    // IME batch-edit depth (Flutter's batchEditNestDepth): coalesce a batch into one state flush.
    private var batchDepth = 0

    // The last state we sent, so we never ship a no-op (avoids feedback churn with the page): the text, and the
    // selection + composing region. Kept apart so a caret move ships only the selection — the shim reads a
    // message without `text` as selection-only — instead of the whole field (20 KB per tap in a long draft).
    private var lastSentText: String? = null
    private var lastSentSel: String? = null

    // True while this view mirrors a live page field, i.e. between a page focus and the blur that releases it.
    // Distinct from [hasFocus]: clearing focus on a View can hand it straight back (a lone focusable in the
    // hierarchy re-takes it), and window focus comes and goes on its own. Only this flag says the buffer still
    // belongs to the page's field — without it, a lingering focus would let a re-focus skip the re-seed and
    // ship the PREVIOUS tab's text to the page on the first keystroke.
    private var mirroring = false

    // Whether the keyboard is *meant* to be up for the field we mirror — our own intent, not the window's
    // current state. Deliberately not derived from the IME insets or [hasFocus]: by the time a tab switch
    // tears this view down, `WindowInsets.imeAnimationTarget` has already snapped to 0 and the view has
    // already lost focus, so anything sampled then reports "no keyboard" for a tab the user left mid-typing.
    // Set when we raise the keyboard, cleared when the user dismisses it or the page field blurs.
    private var keyboardWanted = false

    // The mirrored field takes line breaks, so the keyboard shows Enter rather than an action, and its Enter
    // must still reach the page as a key (see [sendEnter]).
    private var fieldMultiline = false

    // The mirrored field is `readonly`. Kept here rather than checked at each call site so every raise path —
    // a fresh focus, the tap doorbell, and a tab restore — is covered by the one guard in [raiseKeyboard].
    private var fieldReadOnly = false

    private val imm get() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    private val flush = Runnable { flushState() }

    init {
        // Invisible: the IME needs a laid-out, visible target, but the user must never see this field or its
        // cursor/selection handles — only the embedded page. Focusable only while it mirrors a page field
        // (see [setFocusTarget]).
        setFocusTarget(false)
        alpha = 0f
        background = null
        setTextColor(0x00000000)
        setCursorVisible(false)
        setPadding(0, 0, 0, 0)
        addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {
                    // No-op: TextWatcher requires this override, but only afterTextChanged drives our flush.
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    // No-op: TextWatcher requires this override, but only afterTextChanged drives our flush.
                }

                override fun afterTextChanged(s: Editable?) {
                    if (!applyingRemote) onEdited?.invoke()
                    schedule()
                }
            },
        )
        // The IME's action key. Next/Previous move focus between the form's fields, as in Chrome; every other
        // action (Go/Search/Send/Done) is an Enter the page handles (single-line has no newline).
        setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_NEXT -> sendFocusMove("ime.next")
                EditorInfo.IME_ACTION_PREVIOUS -> sendFocusMove("ime.prev")
                else -> sendEnter()
            }
        }
    }

    /** Binds the controller of whatever embedded tab is active; null unbinds (no relay target). */
    fun bind(bridge: EmbeddedImeBridge?) {
        this.bridge = bridge
    }

    /**
     * Notified when the mirrored selection becomes (true) or stops being (false) a non-empty range. The
     * embedded WebView can't present Chrome's copy/paste toolbar in its cross-process surface, so the host
     * shows its own over the page and routes the actions back through [copy]/[cut]/[paste]/[selectAll],
     * which run on this EditText's Editable (and so relay to the page through the normal edit path).
     */
    var onRangeSelectionChanged: ((Boolean) -> Unit)? = null
    private var hadRange = false

    // Off-window Chrome abandons a selection by momentarily collapsing the caret to an endpoint, which we (or
    // the page shim) re-assert right back — so the mirrored selection flickers range→caret→range within a few
    // ms during a long-press/double-tap. The host-drawn handles/toolbar are gated on [hadRange], so reporting
    // every flip blinks them off and back on, in lock-step with each collapse cycle. Native never shows that
    // churn. So we DEFER the "range lost" signal by [RANGE_LOSS_DEBOUNCE_MS]: a re-assert that restores the
    // range first cancels the pending hide, and only a selection that truly STAYS collapsed hides the overlays.
    // Gaining a range is always reported immediately.
    private val reportRangeLost =
        Runnable {
            if (selectionStart == selectionEnd && hadRange) {
                hadRange = false
                onRangeSelectionChanged?.invoke(false)
            }
        }

    /** Fired when the user edits text via the keyboard (not on programmatic page-state applies). The host
     *  hides the insertion handle while typing, the way Android does. */
    var onEdited: (() -> Unit)? = null

    fun copySelection(): Boolean = onTextContextMenuItem(android.R.id.copy)

    // Cut and paste mutate the field, so they are refused on a readonly one: the page would reject the edit
    // and the mirror would drift out of sync with it. The toolbar already hides them there — this is the
    // backstop, kept next to the ops themselves so a future call site can't reintroduce the divergence.
    fun cutSelection(): Boolean = !fieldReadOnly && onTextContextMenuItem(android.R.id.cut)

    fun pasteClipboard(): Boolean = !fieldReadOnly && onTextContextMenuItem(android.R.id.paste)

    fun selectAllText(): Boolean = onTextContextMenuItem(android.R.id.selectAll)

    /**
     * A page field focused: configure the keyboard, seed the buffer, and — unless [withKeyboard] is false —
     * raise the IME. A restored tab passes false: its field is focused again in this mirror (so a tap can put
     * the keyboard straight back) without a keyboard the user had dismissed popping up over the page.
     */
    fun onPageFocus(
        focus: ImeEvent.Focus,
        withKeyboard: Boolean = true,
    ) {
        configureFor(focus)
        mirroring = true
        setFocusTarget(true)
        fieldReadOnly = focus.readOnly
        // Focus the EditText BEFORE seeding text/selection. An EditText jumps its caret to the end when it
        // gains focus; if we seed first, that end-position then overrides the seed and gets shipped to the
        // page — so a tap mid-text lands the caret at the end of the field. Seeding AFTER focus makes the
        // tap position the final state (the focus-induced end-position only schedules a flush that then
        // coalesces to this seed, a no-op).
        requestFocus()
        imm.restartInput(this)
        applyRemote(focus.text, focus.selStart, focus.selEnd)
        if (withKeyboard) raiseKeyboard()
    }

    /**
     * The page re-announced a field that is **already** focused there: the user tapped inside it, or the tab
     * came back on screen and answered our resync.
     *
     * Page focus never moved, so when this view is still the field's mirror there is nothing to re-seed —
     * restarting the input would drop a live composing region mid-word — and the only thing left to do is put
     * the keyboard back if it was dismissed. When the mirror was released (a tab switch clears it, see
     * [onPageBlur]) this is the ONLY way back: the page will never fire another focus event for a field it
     * never blurred, so re-take it here from the re-announced state.
     */
    fun onPageReFocus(
        focus: ImeEvent.Focus,
        withKeyboard: Boolean,
    ) {
        if (isMirroringPageField()) {
            if (withKeyboard) raiseKeyboard()
        } else {
            onPageFocus(focus, withKeyboard)
        }
    }

    /** True while the keyboard this view holds belongs to a page field — see [mirroring]. */
    fun isMirroringPageField() = mirroring && hasFocus()

    /**
     * Whether this tab should come back with its keyboard up: we mirror a page field and the keyboard was
     * meant to be showing when we were asked. Safe to call while the view is being torn down, which is the
     * whole point — see [keyboardWanted].
     */
    fun wantsKeyboardForPageField() = mirroring && keyboardWanted

    /**
     * Put the keyboard back on the field this view already mirrors — the user tapped it after dismissing the
     * keyboard, which leaves the page's focus (and this mirror) untouched, so there is nothing to re-seed.
     *
     * Post the show so it runs after focus/attachment has settled (showSoftInput can no-op otherwise).
     */
    @Suppress("DEPRECATION") // InputMethodManager.SHOW_IMPLICIT is deprecated; no equivalent flag on the newer API.
    fun raiseKeyboard() {
        // A readonly field takes focus and can be selected/copied, but nothing can be typed into it — native
        // Chrome shows no keyboard for one, so neither do we.
        if (fieldReadOnly) return
        keyboardWanted = true
        post {
            if (hasFocus()) imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /**
     * The user put the keyboard away (BACK, or the IME's own hide affordance) while still on this field: a
     * deliberate "I'm done typing", so returning to this tab must NOT pop the keyboard back up.
     *
     * Called by the layer when the IME insets collapse while this view still mirrors the page field. That
     * condition is what separates a dismissal from a tab switch — on a switch the view has already lost focus
     * by the time the insets collapse, so [isMirroringPageField] is false and this never fires. (Note there is
     * no usable key hook for this: Android 13+ routes the IME's back-dismiss through OnBackInvokedCallback, so
     * `onKeyPreIme` is never called.)
     */
    fun noteKeyboardDismissed() {
        keyboardWanted = false
    }

    // When the current selection first became a range, and how many of its collapse-abandonments we've
    // re-asserted. Chrome abandons a selection *immediately* (~60ms); a deliberate user tap-to-collapse comes
    // later — so we only re-assert within a short window of the range forming, bounded for safety.
    private var rangeBecameAt = 0L
    private var reassertCount = 0

    /** The page changed the field itself (its JS, autofill): mirror it without echoing back. */
    fun onPageState(state: ImeEvent.State) {
        val curText = text?.toString() ?: ""
        // Chrome can't present selection handles in the embedded surface, so it abandons a selection by
        // collapsing the caret to one of the range's endpoints, right after it forms. We hold the
        // authoritative selection here, so a collapse to an endpoint of our current range (same text) within
        // the window is that abandonment, not a user action: re-assert our range instead of accepting it.
        val collapsedToEndpoint =
            selectionStart != selectionEnd &&
                state.selStart == state.selEnd &&
                state.text == curText &&
                (state.selStart == selectionStart || state.selStart == selectionEnd)
        if (collapsedToEndpoint && reassertCount < MAX_REASSERT &&
            SystemClock.uptimeMillis() - rangeBecameAt < REASSERT_WINDOW_MS
        ) {
            reassertCount++
            // Keep the window alive across the fight: Chrome re-abandons the selection every ~600ms while the
            // gesture is held, so each re-assert restarts the clock — bounded by reassertCount so a page that
            // truly keeps the caret collapsed still wins.
            rangeBecameAt = SystemClock.uptimeMillis()
            lastSentSel = null // force a non-no-op flush so the range re-ships
            flushState()
            return
        }
        reassertCount = 0
        if (curText == state.text && selectionStart == state.selStart && selectionEnd == state.selEnd) return
        applyRemote(state.text, state.selStart, state.selEnd)
    }

    /** The page field blurred: drop the keyboard. */
    fun onPageBlur() {
        mirroring = false
        keyboardWanted = false
        // [fieldReadOnly] deliberately NOT cleared here: it describes the buffer this mirror still holds,
        // and that buffer outlives the blur. Clearing it let a flush scheduled by this very method (see
        // below) ship a readonly field's text after the flag had been reset. The next [onPageFocus] sets it
        // for whatever field takes over, which is the only point the buffer's identity actually changes.
        removeCallbacks(reportRangeLost)
        if (hadRange) {
            hadRange = false
            onRangeSelectionChanged?.invoke(false)
        }
        // clearFocus() moves the caret, which fires onSelectionChanged → schedule(). That flush would be
        // delivered asynchronously, landing after the page has already moved focus — and the shim applies
        // whatever arrives to the field focused THEN, not the one it was computed from. Nothing this mirror
        // holds belongs to the page once the field has blurred, so suppress the echo and drop the queue.
        applyingRemote = true
        // Not focusable again until the next page focus. Dropping the flag clears our focus as well, so no
        // separate clearFocus() is needed (and one alone could hand focus straight back to us).
        setFocusTarget(false)
        applyingRemote = false
        removeCallbacks(flush)
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    /**
     * Whether this view can take focus. It lives in the main window whether or not a browser tab is open, so
     * while it is focusable it is the fallback focus target for the whole window: when a Compose text field
     * elsewhere loses focus (clearFocus(), or its screen being popped), Android hands focus to this EditText.
     * It then becomes the IME's target, so the keyboard stays up over a screen with no field, and it comes
     * back every time the app resumes. So it is focusable only while it actually mirrors a page field.
     * Both flags are set: `isFocusable` alone would still let it take focus outside touch mode (a hardware
     * keyboard or D-pad).
     */
    private fun setFocusTarget(enabled: Boolean) {
        isFocusable = enabled
        isFocusableInTouchMode = enabled
    }

    /**
     * Hands the keyboard's Enter to the page instead of editing the mirror. A page decides what Enter means —
     * a chat composer sends on it and cancels the key, a plain textarea takes a line break — and it can only
     * decide if it sees the key, so the shim replays it as keydown/keypress and inserts the line break itself
     * when nothing cancelled it (reporting the new text back). Typing a "\n" into this mirror instead shipped
     * the text with the newline already in it: Enter could never send.
     *
     * Flushes first, synchronously, so the word just typed (often still composing, or in a posted flush)
     * reaches the page before the Enter that submits it.
     */
    private fun sendEnter(): Boolean {
        if (!mirroring || fieldReadOnly) return false
        removeCallbacks(flush)
        flushState()
        bridge?.sendImeOp(buildJsonObject { put("type", "ime.action") }.toString())
        return true
    }

    private fun sendFocusMove(type: String): Boolean {
        if (!mirroring) return false
        removeCallbacks(flush)
        flushState()
        bridge?.sendImeOp(buildJsonObject { put("type", type) }.toString())
        return true
    }

    /**
     * Paste and cut (the host toolbar, a hardware Ctrl+V/Ctrl+X) go to the page as what they are, not as an edit of
     * this buffer: the page sees a `paste`/`cut` event first, as in Chrome, and may handle it itself — clean the
     * pasted text, turn it into chips, refuse it — so the field, not this mirror, decides the result and reports
     * it back. Copy stays local: the selection is already here. A keyboard's clipboard chip types the text
     * instead (commitText), which Chrome treats as typing too.
     */
    override fun onTextContextMenuItem(id: Int): Boolean {
        if (!mirroring || fieldReadOnly) return super.onTextContextMenuItem(id)
        return when (id) {
            android.R.id.paste, android.R.id.pasteAsPlainText -> {
                val clip = clipboard.primaryClip
                val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() else null
                if (text.isNullOrEmpty()) return false
                removeCallbacks(flush)
                flushState()
                bridge?.sendImeOp(
                    buildJsonObject {
                        put("type", "ime.paste")
                        put("text", text)
                    }.toString(),
                )
                true
            }
            android.R.id.cut -> {
                if (selectionStart == selectionEnd) return false
                // Copy it ourselves (the page's synthetic cut event can't reach the system clipboard), then let
                // the page remove it.
                super.onTextContextMenuItem(android.R.id.copy)
                removeCallbacks(flush)
                flushState()
                bridge?.sendImeOp(buildJsonObject { put("type", "ime.cut") }.toString())
                true
            }
            else -> super.onTextContextMenuItem(id)
        }
    }

    private val clipboard get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    // A hardware (or IME-synthesized) Enter key on a multi-line field. Shift+Enter keeps the native line
    // break; single-line fields already route Enter through the editor action above.
    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (fieldMultiline && !event.isShiftPressed && (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            if (sendEnter()) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun applyRemote(
        newText: String,
        selStart: Int,
        selEnd: Int,
    ) {
        applyingRemote = true
        // Only replace the buffer when the text actually changed. A page that rewrites its field's
        // selection on a timer (without changing the text) would otherwise force a full setText on every
        // update — clearing the composing region and restarting the IME — which needlessly churns the
        // keyboard and contends with the user's own typing. Reposition the cursor without touching the buffer.
        if (text?.toString() != newText) setText(newText)
        val len = text?.length ?: 0
        setSelection(selStart.coerceIn(0, len), selEnd.coerceIn(0, len))
        applyingRemote = false
        markSent()
        // Start the abandonment window when a fresh range appears, so onPageState can tell Chrome's instant
        // collapse from a later user tap-to-collapse.
        if (selectionStart != selectionEnd) rangeBecameAt = SystemClock.uptimeMillis()
    }

    private fun schedule() {
        if (applyingRemote || batchDepth > 0) return
        removeCallbacks(flush)
        post(flush)
    }

    override fun onSelectionChanged(
        selStart: Int,
        selEnd: Int,
    ) {
        super.onSelectionChanged(selStart, selEnd)
        val isRange = selStart != selEnd
        if (isRange) {
            // A range is back (or still here): cancel any pending hide and show immediately.
            removeCallbacks(reportRangeLost)
            if (!hadRange) {
                hadRange = true
                onRangeSelectionChanged?.invoke(true)
            }
        } else if (hadRange) {
            // Collapsed — but this may be Chrome's transient abandonment we're about to re-assert. Defer the
            // hide; if the range returns within the window, the show branch above cancels this.
            removeCallbacks(reportRangeLost)
            postDelayed(reportRangeLost, RANGE_LOSS_DEBOUNCE_MS)
        }
        schedule()
    }

    private fun selectionKey(): String {
        val editable = text
        val composingStart = if (editable != null) BaseInputConnection.getComposingSpanStart(editable) else -1
        val composingEnd = if (editable != null) BaseInputConnection.getComposingSpanEnd(editable) else -1
        return "$selectionStart:$selectionEnd:$composingStart:$composingEnd"
    }

    /** Records the current buffer as what the page already has (it was applied from the page). */
    private fun markSent() {
        lastSentText = text?.toString() ?: ""
        lastSentSel = selectionKey()
    }

    private fun stateJson(withText: Boolean): JsonObject {
        val editable = text
        val composingStart = if (editable != null) BaseInputConnection.getComposingSpanStart(editable) else -1
        val composingEnd = if (editable != null) BaseInputConnection.getComposingSpanEnd(editable) else -1
        return buildJsonObject {
            put("type", "ime.set")
            // A readonly field's text must never travel back to the page. TYPE_NULL keeps the *user* from
            // typing into the mirror, but the mirror still flushes on selection changes — a long-press
            // select-all, then Chrome's collapse-to-endpoint, both emit one — and that flush is delivered
            // asynchronously, so the page applies it to whatever field is focused by the time it lands.
            // Tapping an editable field right after copying from a readonly one therefore wrote the
            // readonly text into it, with an `input` event no browser would fire.
            //
            // Omitting the key (rather than sending the current text) makes the shim treat the message as
            // selection-only — `var next = (msg.text != null) ? String(msg.text) : prev` — so the
            // host-drawn handles and Copy keep working off a synced selection while nothing can be written.
            // The same omission carries an unchanged text: a caret move doesn't resend the field.
            if (withText && !fieldReadOnly) put("text", editable?.toString() ?: "")
            put("selStart", selectionStart)
            put("selEnd", selectionEnd)
            put("composingStart", composingStart)
            put("composingEnd", composingEnd)
        }
    }

    private fun flushState() {
        if (applyingRemote) return
        val current = text?.toString() ?: ""
        val textChanged = current != lastSentText
        val sel = selectionKey()
        if (!textChanged && sel == lastSentSel) return
        lastSentText = current
        lastSentSel = sel
        bridge?.sendImeOp(stateJson(withText = textChanged).toString())
    }

    private fun configureFor(focus: ImeEvent.Focus) {
        fieldMultiline = focus.multiline && !focus.readOnly
        inputType =
            // A readonly field's mirror must not be typeable AT ALL, not merely keyboard-less. `readonly`
            // stops the *user* editing the field, not scripts — the shim writes through the native value
            // setter, so anything that reaches this Editable is applied to the page and fires an `input`
            // event no native browser would. Refusing cut/paste at the call sites doesn't cover a hardware
            // keyboard (common on tablets/DeX), whose Ctrl+V goes straight to `onTextContextMenuItem`.
            // TYPE_NULL makes `onCheckIsTextEditor()` false, so there is no InputConnection to type through
            // — while selection and Copy, the half native does offer here, keep working.
            if (focus.readOnly) {
                InputType.TYPE_NULL
            } else {
                when (focus.inputType) {
                    "password" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                    "email" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                    "url" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                    "number" -> InputType.TYPE_CLASS_NUMBER
                    "tel" -> InputType.TYPE_CLASS_PHONE
                    else ->
                        InputType.TYPE_CLASS_TEXT or
                            (if (focus.multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_TEXT_VARIATION_NORMAL)
                }
            }
        imeOptions = imeActionFor(focus.enterKeyHint, focus.multiline, focus.inputType, focus.hasNext) or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return BatchAwareConnection(base)
    }

    /**
     * Tracks batch-edit nesting so a multi-op keystroke (e.g. delete-then-insert during composing) is
     * shipped to the page as ONE coalesced state, exactly like Flutter's `InputConnectionAdaptor`.
     */
    private inner class BatchAwareConnection(
        target: InputConnection,
    ) : InputConnectionWrapper(target, true) {
        override fun beginBatchEdit(): Boolean {
            batchDepth++
            return super.beginBatchEdit()
        }

        // Soft keyboards deliver Enter on a multi-line field as a committed "\n" (sometimes with the word
        // before it), not as a key: commit the word, then hand the Enter to the page.
        override fun commitText(
            text: CharSequence?,
            newCursorPosition: Int,
        ): Boolean {
            if (fieldMultiline && text != null && text.endsWith("\n") && text.count { it == '\n' } == 1) {
                val before = text.dropLast(1)
                if (before.isNotEmpty()) super.commitText(before, newCursorPosition)
                return sendEnter() || super.commitText("\n", newCursorPosition)
            }
            return super.commitText(text, newCursorPosition)
        }

        override fun endBatchEdit(): Boolean {
            val result = super.endBatchEdit()
            if (batchDepth > 0) batchDepth--
            // Flush synchronously at the outermost batch close (don't post): a composing keystroke is a
            // delete+insert inside one batch, and shipping the coalesced state now — within the same frame
            // — preserves the composing region. A posted flush would race the next batch and could observe
            // an already-committed (composing-cleared) Editable, breaking the composition lifecycle.
            if (batchDepth == 0) {
                removeCallbacks(flush)
                flushState()
            }
            return result
        }
    }

    private companion object {
        // Chrome re-abandons a held selection repeatedly; cover a long-press hold (each re-assert restarts the
        // window) while still giving up if the page truly keeps re-collapsing forever.
        private const val MAX_REASSERT = 12

        // Wider than Chrome's ~600ms re-collapse interval so consecutive abandonments stay inside the window,
        // yet short enough that a deliberate user tap-to-collapse (well after the gesture) is accepted.
        private const val REASSERT_WINDOW_MS = 800L

        // How long a collapse must persist before we hide the host-drawn selection overlays. The shim/host
        // re-assert restores an abandonment collapse within a frame or two (one IPC hop), so this only needs to
        // outlast that round-trip — comfortably short enough that a genuine tap-to-collapse still feels instant.
        private const val RANGE_LOSS_DEBOUNCE_MS = 250L
    }
}

/**
 * The keyboard action for a page field, as Chrome picks it on Android: the page's `enterkeyhint` when it names
 * one; otherwise a line break for multi-line fields, Search for `type=search`, Next when another field follows
 * in the form (so the action key walks the form instead of submitting it from its first field), and Go.
 */
internal fun imeActionFor(
    enterKeyHint: String,
    multiline: Boolean,
    inputType: String,
    hasNext: Boolean,
): Int =
    when (enterKeyHint) {
        "go" -> EditorInfo.IME_ACTION_GO
        "search" -> EditorInfo.IME_ACTION_SEARCH
        "send" -> EditorInfo.IME_ACTION_SEND
        "next" -> EditorInfo.IME_ACTION_NEXT
        "previous" -> EditorInfo.IME_ACTION_PREVIOUS
        "done" -> EditorInfo.IME_ACTION_DONE
        "enter" -> EditorInfo.IME_ACTION_NONE
        else ->
            when {
                multiline -> EditorInfo.IME_ACTION_NONE
                inputType == "search" -> EditorInfo.IME_ACTION_SEARCH
                hasNext -> EditorInfo.IME_ACTION_NEXT
                else -> EditorInfo.IME_ACTION_GO
            }
    }
