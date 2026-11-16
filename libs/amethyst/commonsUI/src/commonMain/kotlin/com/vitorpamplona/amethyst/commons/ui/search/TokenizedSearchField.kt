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
package com.vitorpamplona.amethyst.commons.ui.search

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.screen_search_title
import com.vitorpamplona.amethyst.commons.search.ActivePicker
import com.vitorpamplona.amethyst.commons.search.KindCandidate
import com.vitorpamplona.amethyst.commons.search.KindRegistry
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.TransformedTextInputGuard
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The search box: a plain text field that draws the tokens it holds as chips, with the picker
 * that the caret's position calls for underneath it.
 *
 * The field's value stays the text the reader typed — chips are a *rendering* of it, never a
 * replacement — so the box can be selected, copied, pasted back and shared as a url, and a query
 * always survives that round trip. See [SearchTokenTransformation] for how the chips are drawn
 * and [SearchFieldState] for how the caret decides which picker is open.
 *
 * While a picker is up it owns the arrow keys and Enter: those keys are walking the offered rows,
 * not the text. When no picker is up they fall through to the field and to [onSubmit].
 *
 * The rows are supplied by the caller — as [people]/[groups], or as a whole [peoplePicker] slot —
 * because resolving a name to a key is an account-scoped, relay-backed question that `commons`
 * has no business answering. This component only says *when* to ask, via [onPeopleQuery] and
 * [onGroupQuery], and what a pick splices into the text. The `kind:` picker is the exception: the
 * kind vocabulary is a constant in `KindRegistry`, so this component fills those rows in itself
 * and every caller gets the picker for free.
 */
@Composable
fun TokenizedSearchField(
    state: SearchFieldState,
    modifier: Modifier = Modifier,
    placeholder: String = stringRes(Res.string.screen_search_title),
    people: ImmutableList<PersonCandidate> = persistentListOf(),
    groups: ImmutableList<GroupCandidate> = persistentListOf(),
    /**
     * The people picker's rows, when the caller has a richer list of its own — Android hands in
     * the composer's `ShowUserSuggestionList`, which already resolves NIP-05, asks the search
     * relays and ranks follows first, and which `commons` cannot reach because it is built on
     * app-module types.
     *
     * A slot owns its own selection affordance, so the arrow keys stay with the caret while one
     * is up; the built-in [people] list is the keyboard-walkable path. Either way this component
     * still owns *when* the picker opens and what a pick splices into the text.
     */
    peoplePicker: (@Composable (ActivePicker.People, onPick: (String) -> Unit) -> Unit)? = null,
    displayName: (String) -> String? = { null },
    groupName: (String) -> String? = { null },
    scopeName: (String, String) -> String? = { _, _ -> null },
    /**
     * Shared with [decorationBox] so a caller's chrome — an outlined border, say — sees the same
     * focus and hover the field does. Without one, chrome built on `OutlinedTextFieldDefaults`
     * watches an interaction source nothing ever emits into and never lights up.
     */
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onPeopleQuery: (String) -> Unit = {},
    onGroupQuery: (String) -> Unit = {},
    onSubmit: () -> Unit = {},
    textStyle: TextStyle = LocalTextStyle.current,
    fieldModifier: Modifier = Modifier,
    /**
     * The field's own chrome — a border, a leading icon, a clear button. When given, it also owns
     * the placeholder: the caller's chrome usually has a slot for one already, and two would
     * stack. When absent the field draws [placeholder] itself.
     */
    decorationBox: (@Composable (@Composable () -> Unit) -> Unit)? = null,
) {
    val styles = rememberSearchTokenStyles()
    // Derived, so typing that leaves the picker as it was (plain words, a caret moving through
    // them) does not recompose the field and everything under it on every keystroke.
    val pickerState = remember(state) { derivedStateOf { state.activePicker } }
    val picker = pickerState.value
    var highlighted by rememberSaveable(picker?.token?.start) { mutableIntStateOf(0) }

    // The one picker whose rows this component fills in itself: the kind vocabulary is a
    // constant in KindRegistry, so there is nothing account-scoped to ask a caller for.
    val kinds =
        remember(picker) {
            (picker as? ActivePicker.Kind)?.let { KindRegistry.candidates(it.token.partial).toImmutableList() } ?: persistentListOf()
        }

    // Asking is driven by the token under the caret, so a blur-and-return re-asks the same
    // question rather than leaving the last answer on screen for a token that has moved on.
    LaunchedQuery(picker, onPeopleQuery, onGroupQuery)

    val rows =
        when (picker) {
            // A slot's rows are not this component's to walk.
            is ActivePicker.People -> if (peoplePicker != null) 0 else people.size
            is ActivePicker.Group -> groups.size
            is ActivePicker.Kind -> kinds.size
            else -> 0
        }

    Column(modifier) {
        TransformedTextInputGuard {
            BasicTextField(
                state = state.textState,
                modifier =
                    fieldModifier
                        .fillMaxWidth()
                        .onFocusChanged { state.onFocusChanged(it.isFocused) }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            handleKey(event.key, state, picker, highlighted, rows, people, groups, kinds, onSubmit) { highlighted = it }
                        },
                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                lineLimits = TextFieldLineLimits.SingleLine,
                interactionSource = interactionSource,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                onKeyboardAction = KeyboardActionHandler { if (!takeEnter(state, picker, highlighted, people, groups, kinds)) onSubmit() },
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                // The caret is read inside the transformation, not keyed on here: the field re-runs it
                // whenever a state it read changes, so a new instance per caret move is not needed.
                outputTransformation = remember(state, styles, displayName, groupName, scopeName) { SearchTokenTransformation(state::settleCaret, styles, displayName, groupName, scopeName) },
                decorator =
                    TextFieldDecorator { inner ->
                        if (decorationBox != null) {
                            decorationBox(inner)
                        } else {
                            Box {
                                if (state.text.isEmpty()) {
                                    Text(
                                        placeholder,
                                        style = textStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                inner()
                            }
                        }
                    },
            )
        }

        when (picker) {
            is ActivePicker.Calendar ->
                SearchPickerSurface(Modifier.padding(top = 4.dp).fillMaxWidth()) {
                    SearchCalendarPicker(
                        picker = picker,
                        month = state.calendarMonth(picker.token),
                        cursor = state.calendarCursor,
                        onPick = { state.pickDay(picker, it) },
                        onStepMonth = { state.stepMonth(picker.token, it) },
                    )
                }

            is ActivePicker.People ->
                if (peoplePicker != null) {
                    SearchPickerSurface(Modifier.padding(top = 4.dp).fillMaxWidth()) {
                        peoplePicker(picker) { state.pickPerson(picker, it) }
                    }
                } else if (people.isNotEmpty()) {
                    SearchPickerSurface(Modifier.padding(top = 4.dp).fillMaxWidth()) {
                        SearchPeoplePicker(people, highlighted, onPick = { state.pickPerson(picker, it.pubkeyHex) })
                    }
                }

            is ActivePicker.Group ->
                if (groups.isNotEmpty()) {
                    SearchPickerSurface(Modifier.padding(top = 4.dp).fillMaxWidth()) {
                        SearchGroupPicker(groups, highlighted, onPick = { state.pickGroup(picker, it.id) })
                    }
                }

            is ActivePicker.Kind ->
                if (kinds.isNotEmpty()) {
                    SearchPickerSurface(Modifier.padding(top = 4.dp).fillMaxWidth()) {
                        SearchKindPicker(kinds, highlighted, onPick = { state.pickKind(picker, it.alias) })
                    }
                }

            null -> Unit
        }
    }
}

@Composable
private fun LaunchedQuery(
    picker: ActivePicker?,
    onPeopleQuery: (String) -> Unit,
    onGroupQuery: (String) -> Unit,
) {
    LaunchedEffect(picker) {
        when (picker) {
            is ActivePicker.People -> onPeopleQuery(picker.token.partial)
            is ActivePicker.Group -> onGroupQuery(picker.token.partial)
            else -> Unit
        }
    }
}

/**
 * Enter, wherever it arrived from: true when a picker consumed it. Nothing highlighted is not
 * consumed — a picker is a suggestion over the text, not a gate in front of it.
 */
private fun takeEnter(
    state: SearchFieldState,
    picker: ActivePicker?,
    highlighted: Int,
    people: ImmutableList<PersonCandidate>,
    groups: ImmutableList<GroupCandidate>,
    kinds: ImmutableList<KindCandidate>,
): Boolean =
    when (picker) {
        is ActivePicker.Calendar ->
            state.calendarCursor?.let {
                state.pickDay(picker, it.ymd())
                true
            } ?: false
        is ActivePicker.People ->
            people.getOrNull(highlighted)?.let {
                state.pickPerson(picker, it.pubkeyHex)
                true
            } ?: false
        is ActivePicker.Group ->
            groups.getOrNull(highlighted)?.let {
                state.pickGroup(picker, it.id)
                true
            } ?: false
        is ActivePicker.Kind ->
            kinds.getOrNull(highlighted)?.let {
                state.pickKind(picker, it.alias)
                true
            } ?: false
        null -> false
    }

/** True when the picker consumed the key; false hands it back to the text field. */
private fun handleKey(
    key: Key,
    state: SearchFieldState,
    picker: ActivePicker?,
    highlighted: Int,
    rows: Int,
    people: ImmutableList<PersonCandidate>,
    groups: ImmutableList<GroupCandidate>,
    kinds: ImmutableList<KindCandidate>,
    onSubmit: () -> Unit,
    setHighlighted: (Int) -> Unit,
): Boolean {
    if (picker == null) {
        if (key != Key.Enter && key != Key.NumPadEnter) return false
        onSubmit()
        return true
    }

    if (key == Key.Enter || key == Key.NumPadEnter) {
        if (takeEnter(state, picker, highlighted, people, groups, kinds)) return true
        onSubmit()
        return true
    }

    if (picker is ActivePicker.Calendar) {
        // The grid is two-dimensional, so all four arrows belong to it while it is open.
        val step =
            when (key) {
                Key.DirectionLeft -> -1
                Key.DirectionRight -> 1
                Key.DirectionUp -> -7
                Key.DirectionDown -> 7
                else -> return false
            }
        state.moveCalendarCursor(picker.token, step)
        return true
    }

    // A list picker owns only up and down; left and right still move the caret through the token.
    val delta =
        when (key) {
            Key.DirectionUp -> -1
            Key.DirectionDown -> 1
            else -> return false
        }
    if (rows == 0) return false
    setHighlighted(((highlighted + delta) % rows + rows) % rows)
    return true
}
