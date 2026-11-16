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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import kotlinx.coroutines.flow.drop

// Drop-ins for Material's OutlinedTextField and TextField on fields that hold prose (descriptions,
// bios, captions, notes, reasons), with spell checking where the platform offers it.
//
// Without a spell checker (Android and iOS, where the keyboard checks spelling) each one is
// Material's own field, unchanged. With one (desktop) it is an OutlinedThinPaddingTextField or
// ThinPaddingTextField with Material's padding and size, mirrored to the caller's value.
//
// Don't use them for identifiers (search, URLs, keys, handles, amounts): a dictionary only flags
// those. Instead of `keyboardActions` they take `onImeAction`: the keyboard's action key on phones,
// Enter in a single-line field on desktop, run after the caller has the field's latest text.

/**
 * [OutlinedTextField] for a `String` holding prose, spell-checked on desktop. [maxLength] caps the
 * text: an `onValueChange` that drops longer text would leave the desktop field out of step.
 */
@Composable
fun SpellCheckedOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxLength: Int? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onImeAction: (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    if (LocalAppPlatform.current.spellCheck == null) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (maxLength == null || it.length <= maxLength) onValueChange(it) },
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            supportingText = supportingText,
            isError = isError,
            keyboardOptions = keyboardOptions,
            keyboardActions = onImeAction?.let { action -> KeyboardActions(onAny = { action() }) } ?: KeyboardActions.Default,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            shape = shape,
            colors = colors,
        )
        return
    }

    val mirror = rememberMirroredState(value, selection = null) { text, _ -> onValueChange(text) }
    SpellCheckedOutlinedTextField(
        mirror.state,
        modifier,
        enabled,
        readOnly,
        textStyle,
        label,
        placeholder,
        leadingIcon,
        trailingIcon,
        supportingText,
        isError,
        keyboardOptions,
        mirror.beforeAction(onImeAction),
        singleLine,
        maxLines,
        minLines,
        shape,
        colors,
        maxLength = maxLength,
    )
}

/** [OutlinedTextField] for a [TextFieldValue] holding prose, spell-checked on desktop. */
@Composable
fun SpellCheckedOutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onImeAction: (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    if (LocalAppPlatform.current.spellCheck == null) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            supportingText = supportingText,
            isError = isError,
            keyboardOptions = keyboardOptions,
            keyboardActions = onImeAction?.let { action -> KeyboardActions(onAny = { action() }) } ?: KeyboardActions.Default,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            shape = shape,
            colors = colors,
        )
        return
    }

    val mirror = rememberMirroredState(value.text, value.selection) { text, selection -> onValueChange(TextFieldValue(text, selection)) }
    SpellCheckedOutlinedTextField(
        mirror.state,
        modifier,
        enabled,
        readOnly,
        textStyle,
        label,
        placeholder,
        leadingIcon,
        trailingIcon,
        supportingText,
        isError,
        keyboardOptions,
        mirror.beforeAction(onImeAction),
        singleLine,
        maxLines,
        minLines,
        shape,
        colors,
    )
}

/** [OutlinedTextField] look for a [TextFieldState] holding prose, spell-checked on desktop. */
@Composable
fun SpellCheckedOutlinedTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onImeAction: (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    maxLength: Int? = null,
) {
    val inputTransformation = maxLength?.let { InputTransformation.maxLength(it) }
    if (LocalAppPlatform.current.spellCheck == null) {
        OutlinedTextField(
            state = state,
            inputTransformation = inputTransformation,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            label = label?.let { { it() } },
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            supportingText = supportingText,
            isError = isError,
            keyboardOptions = keyboardOptions,
            onKeyboardAction = onImeAction?.let { action -> KeyboardActionHandler { action() } },
            lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(minLines, maxLines),
            shape = shape,
            colors = colors,
        )
        return
    }

    OutlinedThinPaddingTextField(
        state = state,
        inputTransformation = inputTransformation,
        modifier = modifier.defaultMinSize(OutlinedTextFieldDefaults.MinWidth, OutlinedTextFieldDefaults.MinHeight),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        spellCheck = true,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onImeAction?.let { action -> KeyboardActionHandler { action() } },
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = shape,
        colors = colors,
        contentPadding = if (label == null) OutlinedTextFieldDefaults.contentPaddingWithoutLabel() else OutlinedTextFieldDefaults.contentPaddingWithLabel(),
    )
}

/** Material's filled [TextField] for a `String` holding prose, spell-checked on desktop. */
@Composable
fun SpellCheckedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onImeAction: (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
) {
    if (LocalAppPlatform.current.spellCheck == null) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            supportingText = supportingText,
            isError = isError,
            keyboardOptions = keyboardOptions,
            keyboardActions = onImeAction?.let { action -> KeyboardActions(onAny = { action() }) } ?: KeyboardActions.Default,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            shape = shape,
            colors = colors,
        )
        return
    }

    val mirror = rememberMirroredState(value, selection = null) { text, _ -> onValueChange(text) }
    SpellCheckedTextField(
        mirror.state,
        modifier,
        enabled,
        readOnly,
        textStyle,
        label,
        placeholder,
        leadingIcon,
        trailingIcon,
        supportingText,
        isError,
        keyboardOptions,
        mirror.beforeAction(onImeAction),
        singleLine,
        maxLines,
        minLines,
        shape,
        colors,
    )
}

/** Material's filled [TextField] for a [TextFieldValue] holding prose, spell-checked on desktop. */
@Composable
fun SpellCheckedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onImeAction: (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
) {
    if (LocalAppPlatform.current.spellCheck == null) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            supportingText = supportingText,
            isError = isError,
            keyboardOptions = keyboardOptions,
            keyboardActions = onImeAction?.let { action -> KeyboardActions(onAny = { action() }) } ?: KeyboardActions.Default,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            shape = shape,
            colors = colors,
        )
        return
    }

    val mirror = rememberMirroredState(value.text, value.selection) { text, selection -> onValueChange(TextFieldValue(text, selection)) }
    SpellCheckedTextField(
        mirror.state,
        modifier,
        enabled,
        readOnly,
        textStyle,
        label,
        placeholder,
        leadingIcon,
        trailingIcon,
        supportingText,
        isError,
        keyboardOptions,
        mirror.beforeAction(onImeAction),
        singleLine,
        maxLines,
        minLines,
        shape,
        colors,
    )
}

@Composable
private fun SpellCheckedTextField(
    state: TextFieldState,
    modifier: Modifier,
    enabled: Boolean,
    readOnly: Boolean,
    textStyle: TextStyle,
    label: @Composable (() -> Unit)?,
    placeholder: @Composable (() -> Unit)?,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    supportingText: @Composable (() -> Unit)?,
    isError: Boolean,
    keyboardOptions: KeyboardOptions,
    onImeAction: (() -> Unit)?,
    singleLine: Boolean,
    maxLines: Int,
    minLines: Int,
    shape: Shape,
    colors: TextFieldColors,
) {
    ThinPaddingTextField(
        state = state,
        modifier = modifier.defaultMinSize(TextFieldDefaults.MinWidth, TextFieldDefaults.MinHeight),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        spellCheck = true,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onImeAction?.let { action -> KeyboardActionHandler { action() } },
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = shape,
        colors = colors,
        contentPadding = if (label == null) TextFieldDefaults.contentPaddingWithoutLabel() else TextFieldDefaults.contentPaddingWithLabel(),
    )
}

/** A mirrored [state], and the way to hand the caller its latest text before acting on it. */
private class MirroredState(
    val state: TextFieldState,
    private val flush: () -> Unit,
) {
    /**
     * Wraps [action] so the caller has the field's current text when it runs: the caller's copy
     * trails the field by a frame, and Enter right after a keystroke would act on the old text.
     */
    fun beforeAction(action: (() -> Unit)?): (() -> Unit)? =
        action?.let {
            {
                flush()
                it()
            }
        }
}

/**
 * A [TextFieldState] kept in step with a caller that holds the text itself.
 *
 * Edits are reported through [report]. The caller's text trails the field by a frame, so a value
 * arriving that the field reported lately is an echo and is ignored; anything else is the caller
 * changing the text itself (clearing it after sending, loading a draft) and replaces the field's.
 * With a [selection] (a `TextFieldValue` caller) cursor moves are reported too.
 */
@Composable
private fun rememberMirroredState(
    text: String,
    selection: TextRange?,
    report: (String, TextRange) -> Unit,
): MirroredState {
    val state = rememberTextFieldState(text, selection ?: TextRange(text.length))
    val reported = remember { ArrayDeque<String>() }
    val latestReport by rememberUpdatedState(report)
    val tracksSelection = selection != null

    fun send(
        current: String,
        currentSelection: TextRange,
    ) {
        reported.addLast(current)
        if (reported.size > MAX_REPORTED) reported.removeFirst()
        latestReport(current, if (tracksSelection) currentSelection else TextRange(current.length))
    }

    LaunchedEffect(state, tracksSelection) {
        snapshotFlow { state.text.toString() to (if (tracksSelection) state.selection else TextRange.Zero) }
            .drop(1)
            .collect { (current, currentSelection) -> send(current, currentSelection) }
    }

    LaunchedEffect(text) {
        if (text != state.text.toString() && text !in reported) {
            reported.clear()
            state.edit {
                replace(0, length, text)
                this.selection =
                    if (selection != null) {
                        TextRange(selection.start.coerceIn(0, text.length), selection.end.coerceIn(0, text.length))
                    } else {
                        TextRange(text.length)
                    }
            }
        }
    }

    return MirroredState(state) {
        val current = state.text.toString()
        if (reported.lastOrNull() != current) send(current, state.selection)
    }
}

private const val MAX_REPORTED = 32
