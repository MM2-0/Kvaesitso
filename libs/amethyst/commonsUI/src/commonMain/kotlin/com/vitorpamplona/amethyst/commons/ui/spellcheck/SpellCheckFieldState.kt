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
package com.vitorpamplona.amethyst.commons.ui.spellcheck

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.service.spellcheck.Misspelling
import com.vitorpamplona.amethyst.commons.service.spellcheck.SpellCheckService
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The misspellings found in [text], the stored (not drawn) text of the field. */
@Immutable
internal data class CheckedText(
    val text: String,
    val misspellings: List<Misspelling>,
) {
    /**
     * Carries the misspellings across an edit until the next check: those before the edit stay,
     * those after it move with the text, and those it touched are dropped. Keeps the underlines
     * from flickering away while the user types somewhere else in the post.
     */
    fun followEdit(newText: String): CheckedText {
        if (newText == text) return this
        val edit = EditRegion.between(text, newText)
        val delta = newText.length - text.length

        val kept =
            misspellings.mapNotNull { m ->
                when {
                    m.end < edit.start -> m
                    m.start > edit.oldEnd -> m.copy(start = m.start + delta, end = m.end + delta)
                    else -> null
                }
            }
        return CheckedText(newText, kept)
    }

    companion object {
        val Empty = CheckedText("", emptyList())
    }
}

/** What changed between two texts: `[start, oldEnd)` of the old one became `[start, newEnd)` of the new one. */
internal class EditRegion(
    val start: Int,
    val oldEnd: Int,
    val newEnd: Int,
) {
    companion object {
        fun between(
            old: String,
            new: String,
        ): EditRegion {
            val max = minOf(old.length, new.length)
            var prefix = 0
            while (prefix < max && old[prefix] == new[prefix]) prefix++
            var suffix = 0
            while (suffix < max - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
            return EditRegion(prefix, old.length - suffix, new.length - suffix)
        }
    }
}

/** A misspelling and where it is drawn. */
internal class DrawnMisspelling(
    val misspelling: Misspelling,
    val drawn: TextRange,
)

/**
 * Spell checking for one text field: checks the text after each pause in typing, draws the wavy
 * underlines, and remembers which misspelled word was right-clicked so the context menu can offer
 * its suggestions.
 */
@Stable
class SpellCheckFieldState internal constructor(
    val service: SpellCheckService,
    private val field: TextFieldState,
    private val scope: CoroutineScope,
) {
    /** The last finished check. Edits since then are followed in [current]. */
    internal var checked by mutableStateOf(CheckedText.Empty)

    /** Reads the field's current layout. The getter reads snapshot state, so a draw that calls it redraws on relayout. */
    internal var layout: (() -> TextLayoutResult?)? = null

    internal var mapping: VisualOffsetMapping? = null

    /** The misspelled word under the last right-click, or null when the click missed every one. */
    var menuTarget by mutableStateOf<Misspelling?>(null)
        private set

    /** Suggestions for [menuTarget]; null while they are still being looked up. */
    var menuSuggestions by mutableStateOf<List<String>?>(null)
        private set

    // Caches for the UI thread, keyed by identity: the field's text object changes on every edit.
    private var seenText: CharSequence? = null
    private var seenString = ""
    private var followedFrom: CheckedText? = null
    private var followed = CheckedText.Empty

    /** Where the last edit ended, while the cursor is still there: the word being typed. -1 once it moves. */
    private var typingAt = -1

    internal val onTextLayout: Density.(getResult: () -> TextLayoutResult?) -> Unit = { getResult -> layout = getResult }

    internal suspend fun checkContinuously() {
        combine(snapshotFlow { field.text.toString() }, service.revision) { text, revision -> text to revision }
            .collectLatest { (text, _) ->
                // Wait for a pause in typing; the next keystroke cancels this check.
                delay(TYPING_PAUSE_MS)
                checked = CheckedText(text, service.findMisspellings(text))
            }
    }

    /**
     * The last check carried over to the field's current text. Recomputed only when either one
     * changes, so a draw between a keystroke and the next check still has every underline in place.
     */
    private fun current(): CheckedText {
        val text = field.text
        if (text !== seenText) {
            val string = text.toString()
            if (seenText != null && string != seenString) {
                typingAt = EditRegion.between(seenString, string).newEnd
            }
            seenText = text
            seenString = string
        }
        val base = checked
        if (base !== followedFrom || followed.text != seenString) {
            followed = base.followEdit(seenString)
            followedFrom = base
        }
        return followed
    }

    /** The misspellings that are in the text now, with where each is drawn. */
    internal fun drawnMisspellings(layout: TextLayoutResult): List<DrawnMisspelling> {
        val current = current()
        if (current.misspellings.isEmpty()) return emptyList()

        val drawn = layout.layoutInput.text.text
        val replacements = replacements(current.text)

        return current.misspellings.mapNotNull { m ->
            val start = replacements.toVisual(m.start) ?: return@mapNotNull null
            val end = start + (m.end - m.start)
            if (end > drawn.length || !drawn.regionMatches(start, m.word, 0, m.word.length)) return@mapNotNull null
            DrawnMisspelling(m, TextRange(start, end))
        }
    }

    /**
     * The misspelling the cursor is still typing, left without an underline until the cursor moves
     * on, as browsers do. Kept apart from [drawnMisspellings] so moving the cursor or dragging a
     * selection only redraws, without rebuilding the underlines.
     */
    internal fun wordBeingTyped(): Misspelling? {
        val current = current()
        val selection = field.selection
        val cursor = if (selection.collapsed) selection.start else -1
        if (cursor != typingAt) typingAt = -1
        if (typingAt < 0) return null
        return current.misspellings.firstOrNull { typingAt in it.start..it.end }
    }

    internal fun onSecondaryPress(positionInText: Offset) {
        val layout = layout?.invoke()
        val target =
            if (layout == null) {
                null
            } else {
                val offset = layout.getOffsetForPosition(positionInText)
                // Even the word being typed: the user is asking about it.
                drawnMisspellings(layout)
                    .firstOrNull { offset in it.drawn.start..it.drawn.end }
                    ?.misspelling
            }

        menuTarget = target
        menuSuggestions = null
        if (target != null) {
            scope.launch {
                val suggestions = service.suggestions(target.word)
                if (menuTarget == target) menuSuggestions = suggestions
            }
        }
    }

    internal fun forgetMenuTarget() {
        menuTarget = null
        menuSuggestions = null
    }

    fun replace(
        misspelling: Misspelling,
        replacement: String,
    ) {
        val text = field.text
        if (misspelling.end > text.length || !text.regionMatches(misspelling.start, misspelling.word, 0, misspelling.word.length)) return
        field.edit {
            replace(misspelling.start, misspelling.end, replacement)
            selection = TextRange(misspelling.start + replacement.length)
        }
        menuTarget = null
    }

    fun addToDictionary(misspelling: Misspelling) {
        menuTarget = null
        scope.launch { service.addToDictionary(misspelling.word) }
    }

    // The transformation remembers its last answer, so this is the very mapping the field drew with.
    private fun replacements(text: String): List<TextReplacement> = mapping?.replacements(text) ?: emptyList()

    companion object {
        private const val TYPING_PAUSE_MS = 250L
    }
}

/**
 * Spell checking for [field] when the platform offers it, the user has not turned it off, and
 * [enabled] (false for read-only and password-like fields). Null otherwise.
 */
@Composable
fun rememberSpellCheckFieldState(
    field: TextFieldState,
    enabled: Boolean,
    mapping: VisualOffsetMapping?,
): SpellCheckFieldState? {
    // Fields that did not ask for spell checking must not wake the engine up (~0.4 s to load).
    if (!enabled) return null
    val service = LocalAppPlatform.current.spellCheck ?: return null
    LaunchedEffect(service) { service.activate() }
    val engine by service.activeEngine.collectAsState()
    if (engine == null) return null

    val scope = rememberCoroutineScope()
    val state = remember(service, field, scope) { SpellCheckFieldState(service, field, scope) }
    state.mapping = mapping
    LaunchedEffect(state) { state.checkContinuously() }
    return state
}

/**
 * Wraps a text field's inner text (the `innerTextField` a decorator receives) to draw the
 * underlines over it and to notice right-clicks. [scrollState] must be the one given to the
 * `BasicTextField`, because the text inside scrolls under this box.
 *
 * Takes a null [spell] while the engine loads (or when it is off) and keeps the same box either
 * way: swapping the box in later would dispose and rebuild the text field under the user's fingers.
 */
@Composable
fun SpellCheckedInnerTextField(
    spell: SpellCheckFieldState?,
    scrollState: ScrollState,
    singleLine: Boolean,
    innerTextField: @Composable () -> Unit,
) {
    val color = MaterialTheme.colorScheme.error
    Box(
        propagateMinConstraints = true,
        modifier = if (spell != null) Modifier.spellCheckUnderlines(spell, scrollState, singleLine, color) else Modifier,
    ) {
        innerTextField()
    }
}

private fun Modifier.spellCheckUnderlines(
    spell: SpellCheckFieldState,
    scrollState: ScrollState,
    singleLine: Boolean,
    color: Color,
): Modifier =
    pointerInput(spell, singleLine) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    val position = event.changes.firstOrNull()?.position ?: continue
                    spell.onSecondaryPress(position + scrollOffset(scrollState, singleLine))
                }
            }
        }
    }.drawWithCache {
        // Rebuilt only when the text, its layout or the check changes; not on cursor blinks,
        // cursor moves, selection drags or scroll frames, which only redraw.
        val layout = spell.layout?.invoke()
        val amplitude = 1.2.dp.toPx()
        val halfWave = 2.dp.toPx()
        val gap = 1.5.dp.toPx()
        val stroke = Stroke(width = 1.dp.toPx())
        val underlines =
            if (layout == null) {
                emptyList()
            } else {
                spell.drawnMisspellings(layout).map {
                    val path = Path()
                    layout.forEachLineSegment(it.drawn) { left, right, baseline ->
                        path.addSquiggle(left, right, baseline + gap + amplitude, amplitude, halfWave)
                    }
                    it.misspelling to path
                }
            }
        onDrawWithContent {
            drawContent()
            if (underlines.isEmpty()) return@onDrawWithContent
            val typing = spell.wordBeingTyped()
            val shift = scrollOffset(scrollState, singleLine)
            clipRect {
                translate(-shift.x, -shift.y) {
                    underlines.forEach { (misspelling, path) ->
                        if (misspelling != typing) drawPath(path, color, style = stroke)
                    }
                }
            }
        }
    }

private fun scrollOffset(
    scrollState: ScrollState,
    singleLine: Boolean,
): Offset = if (singleLine) Offset(scrollState.value.toFloat(), 0f) else Offset(0f, scrollState.value.toFloat())

/** Calls [block] with the horizontal extent and baseline of [range] on each line it spans. */
private inline fun TextLayoutResult.forEachLineSegment(
    range: TextRange,
    block: (left: Float, right: Float, baseline: Float) -> Unit,
) {
    if (range.collapsed) return
    val firstLine = getLineForOffset(range.start)
    val lastLine = getLineForOffset(range.end - 1)
    for (line in firstLine..lastLine) {
        val start = maxOf(range.start, getLineStart(line))
        val end = minOf(range.end, getLineEnd(line, visibleEnd = true))
        if (end <= start) continue
        val first = getBoundingBox(start)
        val last = getBoundingBox(end - 1)
        block(minOf(first.left, last.left), maxOf(first.right, last.right), getLineBaseline(line))
    }
}

private fun Path.addSquiggle(
    left: Float,
    right: Float,
    centerY: Float,
    amplitude: Float,
    halfWave: Float,
) {
    moveTo(left, centerY)
    var x = left
    var up = true
    while (x < right) {
        val next = minOf(x + halfWave, right)
        quadraticTo((x + next) / 2, if (up) centerY - 2 * amplitude else centerY + 2 * amplitude, next, centerY)
        x = next
        up = !up
    }
}

/** Context-menu entries for the right-clicked misspelling, ahead of Cut/Copy/Paste. */
@Immutable
data class SpellCheckMenuEntry(
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/**
 * The menu entries for [spell]'s right-clicked word: its suggestions (or [noSuggestionsLabel]),
 * then [addToDictionaryLabel]. Empty when the click was not on a misspelling. Reads snapshot
 * state, so a menu that calls it while open fills in as suggestions arrive.
 */
fun spellCheckMenuEntries(
    spell: SpellCheckFieldState,
    noSuggestionsLabel: String,
    addToDictionaryLabel: String,
): List<SpellCheckMenuEntry> {
    val target = spell.menuTarget ?: return emptyList()
    val suggestions = spell.menuSuggestions
    return buildList {
        when {
            suggestions == null -> {}
            suggestions.isEmpty() -> add(SpellCheckMenuEntry(noSuggestionsLabel, enabled = false) {})
            else -> suggestions.forEach { add(SpellCheckMenuEntry(it, enabled = true) { spell.replace(target, it) }) }
        }
        add(SpellCheckMenuEntry(addToDictionaryLabel, enabled = true) { spell.addToDictionary(target) })
    }
}

/**
 * Puts [spell]'s suggestions in the text field's context menu, where the platform has a menu that
 * takes extra entries (desktop). Elsewhere it only draws [content].
 *
 * [spellCheckable] must stay the same for a field's lifetime (it is the field's opt-in), while
 * [spell] may come and go as the engine loads or the user turns it off: changing what wraps
 * [content] would dispose and rebuild the text field, losing its focus and cursor.
 */
@Composable
expect fun SpellCheckContextMenu(
    spellCheckable: Boolean,
    spell: SpellCheckFieldState?,
    content: @Composable () -> Unit,
)
