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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.commons.qrcode.ScanFrame
import com.vitorpamplona.amethyst.commons.qrcode.ScanResult
import com.vitorpamplona.amethyst.commons.qrcode.ScannedPayload
import com.vitorpamplona.amethyst.commons.qrcode.StructuredAppendAccumulator

/**
 * Everything the scanner UI draws, and the decision of what a frame means.
 *
 * Kept out of the composable so the accept/dedupe/multi-code rules are one readable block rather
 * than conditions scattered through a camera callback.
 */
@Stable
class QrScannerState {
    private val sequence = StructuredAppendAccumulator()

    /** The size of the image the decoder saw, for mapping [ScanResult.bounds] onto the preview. */
    var frame by mutableStateOf(ScanFrame(0, 0))
        private set

    /**
     * Codes currently visible. One entry means we take it; several mean we draw them all and wait
     * for a tap, because silently picking one of several codes is how you scan the poster next to
     * the one you meant.
     */
    var candidates by mutableStateOf<List<ScanResult>>(emptyList())
        private set

    /** Set while a Structured Append payload is half-captured: captured count to total. */
    var sequenceProgress by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    /** True when the scene has been too dark for [DARK_DWELL_MS]; the UI offers the torch. */
    var isDark by mutableStateOf(false)
        private set

    var torchOn by mutableStateOf(false)
    var torchAvailable by mutableStateOf(false)

    var zoomRatio by mutableFloatStateOf(1f)
    var maxZoomRatio by mutableFloatStateOf(1f)

    /** A payload we decoded but the caller could not use; drives the explain-what-happened sheet. */
    var rejected by mutableStateOf<ScannedPayload?>(null)

    /** A transient message (no code in that picture, empty clipboard, decoder unavailable). */
    var notice by mutableStateOf<String?>(null)

    /**
     * The codes found in one imported picture, when it held more than one.
     *
     * The camera answers this case by drawing every code it can see and waiting for a tap. An
     * imported picture is not on screen to tap, so the choice moves to a list; empty means there
     * is nothing to choose and the scanner behaves as before.
     */
    var imageCodes by mutableStateOf<List<ScannedPayload>>(emptyList())

    /** A line shown with [imageCodes] about anything else the picture held, such as a partial sequence. */
    var imageCodesNote by mutableStateOf<String?>(null)

    private var lastSubmittedText: String? = null
    private var lastSubmittedAt = 0L
    private var darkSinceMs = 0L

    /** The last frame that held more than one code, and when; see [MULTI_HOLD_MS]. */
    private var lastMulti: List<ScanResult> = emptyList()
    private var lastMultiAtMs = 0L

    /** The code the last thorough frame found alone in view, and when; see [onFrame]. */
    private var thoroughLoneText: String? = null
    private var thoroughLoneAtMs = 0L

    /**
     * True while a sheet over the camera is waiting on the user. The analyzer reads it from its
     * own thread to skip decoding frames whose result would be thrown away.
     */
    val isAwaitingUser: Boolean get() = rejected != null || imageCodes.isNotEmpty()

    fun resetZoom() {
        zoomRatio = 1f
    }

    /**
     * Folds one analysed frame into the UI state and decides whether we have an answer.
     *
     * Returns the payload to hand back to the caller, or null to keep scanning. Returning null is
     * the normal case — nothing in frame, several codes in frame, a half-finished multi-part
     * code, or the same code we just submitted.
     */
    fun onFrame(
        scan: FrameScan,
        nowMs: Long,
    ): String? {
        frame = scan.frame
        updateDarkness(scan.brightness, nowMs)

        // Checked on every frame, not just on empty ones: an abandoned half-capture is abandoned
        // whether or not the camera is busy reading something else.
        if (sequence.dropIfStale(nowMs)) sequenceProgress = null

        // Nothing is decided while a sheet is up: the "we can't open this" sheet, because the user
        // is reading it and the offending code is very probably still in front of the lens; the
        // picture's code chooser, because a frame accepted now would close the scanner under it.
        if (isAwaitingUser) {
            candidates = emptyList()
            return null
        }

        val found = scan.results.distinctBy { it.text }
        if (found.isEmpty()) {
            candidates = emptyList()
            return null
        }

        if (found.size > 1) {
            lastMulti = found
            lastMultiAtMs = nowMs
        }
        if (scan.thorough) {
            thoroughLoneText = found.singleOrNull()?.text
            thoroughLoneAtMs = nowMs
        }

        // A multi-part code is never complete on its first part, so it can't be a single answer.
        // Every part in view is fed, not just the first: a poster prints its parts side by side,
        // and the decoder reports them in the same order every frame, so feeding one per frame
        // fed the same one forever and the sequence never got past "1 of N". Only one sequence's
        // parts, though -- the one being collected, else the first in view's: two posters' parts
        // fed in turn restart each other and neither completes.
        val parts = found.filter { it.isPartOfSequence }
        if (parts.isNotEmpty()) {
            candidates = found
            val batch =
                parts.filter { sequence.isCurrentSequence(it) }.ifEmpty {
                    val first = parts.first()
                    parts.filter { it.sequenceId == first.sequenceId && it.sequenceSize == first.sequenceSize }
                }
            var joined: String? = null
            for (part in batch) {
                joined = sequence.add(part, nowMs)
                if (joined != null) break
            }
            sequenceProgress = if (joined == null) sequence.captured to sequence.total else null
            return joined?.let { accept(it, nowMs) }
        }

        if (found.size > 1) {
            candidates = found
            return null
        }

        // One code now, but more than one a moment ago. Decoding is not all-or-nothing per frame:
        // an easy code next to a marginal one reads alone on most frames and with its neighbour
        // only on the thorough ones, so taking the lone read would pick one of two codes for the
        // user -- the exact thing the tap exists to prevent. Keep showing the whole set, with
        // the fresh outline for the one just read, until the other has been gone a while.
        val only = found.first()
        if (lastMultiAtMs != 0L && nowMs - lastMultiAtMs < MULTI_HOLD_MS) {
            candidates =
                if (lastMulti.any { it.text == only.text }) {
                    lastMulti.map { if (it.text == only.text) only else it }
                } else {
                    found
                }
            return null
        }

        candidates = found

        // Alone on a fast frame is not proof of alone: the neighbour may simply be too hard for the
        // fast pass, and no frame has yet shown both. Wait for a thorough pass (every
        // QrFrameAnalyzer.THOROUGH_EVERY frames, so a few hundred milliseconds at most) to agree.
        val confirmedAlone = thoroughLoneText == only.text && nowMs - thoroughLoneAtMs < MULTI_HOLD_MS
        if (!confirmedAlone) return null

        return accept(only.text, nowMs)
    }

    /** Taking one of several visible codes, because the user tapped it. */
    fun onCandidateTapped(
        result: ScanResult,
        nowMs: Long,
    ): String? {
        // A part of a multi-part code is a fragment of something else, not an answer.
        if (result.isPartOfSequence) return null
        candidates = emptyList()
        // Deliberately bypasses the dedupe window. That window exists to stop ONE code decoding
        // thirty times a second from firing the caller thirty times; a tap is one decision by a
        // person, and swallowing it makes the highlight a target that can be tapped with nothing
        // happening. The latch is still armed, so the camera frames that follow -- the tapped
        // code is very probably still in view -- do not fire it again.
        lastSubmittedText = result.text
        lastSubmittedAt = nowMs
        return result.text
    }

    /**
     * Debounced hand-off.
     *
     * A code held in front of the lens decodes ~30 times a second. Without this the caller's
     * handler fires 30 times, which for a navigation target means 30 stacked screens.
     */
    private fun accept(
        text: String,
        nowMs: Long,
    ): String? {
        if (text == lastSubmittedText && nowMs - lastSubmittedAt < DEDUPE_MS) return null
        lastSubmittedText = text
        lastSubmittedAt = nowMs
        return text
    }

    /** Called when the caller rejects a payload, so the same code does not re-fire immediately. */
    fun onRejected(payload: ScannedPayload) {
        rejected = payload
        candidates = emptyList()
    }

    /**
     * Dismissing the sheet resumes scanning.
     *
     * The dedupe latch is deliberately LEFT in place. Clearing it - so the user could retry the
     * very same code - meant that the offending code, still sitting in front of the lens, decoded
     * again on the next frame and re-opened the sheet immediately: "Scan again" became a button
     * that could not be escaped. Keeping the latch lets the camera run; pointing at the same code
     * again after [DEDUPE_MS] still re-triggers it, which is the retry that was actually wanted.
     */
    fun dismissRejection(nowMs: Long) {
        rejected = null
        // Restart the latch from now, so the grace period is measured from when the user dismissed
        // the sheet rather than from when the code was first read.
        lastSubmittedAt = nowMs
    }

    private fun updateDarkness(
        brightness: Float,
        nowMs: Long,
    ) {
        if (brightness > DARK_THRESHOLD) {
            darkSinceMs = 0
            isDark = false
            return
        }
        if (darkSinceMs == 0L) darkSinceMs = nowMs
        isDark = nowMs - darkSinceMs >= DARK_DWELL_MS
    }

    companion object {
        /** Long enough that one steady code fires once; short enough to rescan on purpose. */
        const val DEDUPE_MS = 1_500L

        /**
         * How long a lone code waits after several were in view. Thorough passes run about six
         * times a second, so a code that only those can read shows up well inside this.
         */
        const val MULTI_HOLD_MS = 750L

        /** Mean luminance below this reads as "the torch would help". */
        const val DARK_THRESHOLD = 0.18f

        /** Don't offer the torch for a thumb over the lens or a moment of shadow. */
        const val DARK_DWELL_MS = 1_000L
    }
}
