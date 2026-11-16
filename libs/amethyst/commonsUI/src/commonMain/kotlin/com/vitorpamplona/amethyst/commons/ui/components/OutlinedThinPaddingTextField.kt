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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.privacylock.LocalUserActivity
import com.vitorpamplona.amethyst.commons.ui.spellcheck.SpellCheckContextMenu
import com.vitorpamplona.amethyst.commons.ui.spellcheck.SpellCheckedInnerTextField
import com.vitorpamplona.amethyst.commons.ui.spellcheck.VisualOffsetMapping
import com.vitorpamplona.amethyst.commons.ui.spellcheck.rememberSpellCheckFieldState
import com.vitorpamplona.amethyst.commons.ui.text.TransformedTextInputGuard
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import kotlinx.coroutines.flow.drop

/**
 * Outlined sibling of [ThinPaddingTextField]. Wraps the new [TextFieldState] API
 * with [OutlinedTextFieldDefaults.DecorationBox] so callers that want the
 * Material3 outlined look (visible rounded border, transparent inside, notched
 * label) can use the same `outputTransformation` / `inputTransformation`
 * mention pipeline as the filled `ThinPaddingTextField`.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OutlinedThinPaddingTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    onTextChanged: (() -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    spellCheck: Boolean = false,
    onKeyboardAction: KeyboardActionHandler? = null,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    contentPadding: PaddingValues =
        OutlinedTextFieldDefaults.contentPaddingWithoutLabel(
            start = 10.dp,
            top = 12.dp,
            end = 10.dp,
            bottom = 12.dp,
        ),
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }

    // Typing counts as activity for the privacy lock's idle timer.
    val onActivity = LocalUserActivity.current
    LaunchedEffect(state, onActivity) {
        snapshotFlow { state.text }
            .drop(1)
            .collect { onActivity() }
    }

    if (onTextChanged != null) {
        val callback by rememberUpdatedState(onTextChanged)
        LaunchedEffect(state) {
            snapshotFlow { state.text }
                .collect { callback() }
        }
    }

    val textColor =
        textStyle.color.takeOrElse {
            val focused by interactionSource.collectIsFocusedAsState()
            when {
                !enabled -> MaterialTheme.colorScheme.placeholderText
                isError -> MaterialTheme.colorScheme.onSurface
                focused -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurface
            }
        }
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

    val lineLimits =
        if (singleLine) {
            TextFieldLineLimits.SingleLine
        } else {
            TextFieldLineLimits.MultiLine(minLines, maxLines)
        }

    // Underlines misspellings and offers suggestions on right-click where the platform checks spelling.
    // spellCheckable fixes the field's structure; spell comes and goes with the engine and the setting.
    val spellCheckable = spellCheck && LocalAppPlatform.current.spellCheck != null
    val spell = rememberSpellCheckFieldState(state, spellCheckable && enabled && !readOnly, outputTransformation as? VisualOffsetMapping)
    val scrollState = rememberScrollState()

    CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
        SpellCheckContextMenu(spellCheckable, spell) {
            TransformedTextInputGuard(active = outputTransformation != null) {
                BasicTextField(
                    state = state,
                    modifier =
                        modifier
                            .defaultMinSize(
                                minWidth = TextFieldDefaults.MinWidth,
                                minHeight = 36.dp,
                            ),
                    enabled = enabled,
                    readOnly = readOnly,
                    textStyle = mergedTextStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = keyboardOptions,
                    lineLimits = lineLimits,
                    interactionSource = interactionSource,
                    inputTransformation = inputTransformation,
                    outputTransformation = outputTransformation,
                    onKeyboardAction = onKeyboardAction,
                    onTextLayout = spell?.onTextLayout,
                    scrollState = scrollState,
                    decorator = { innerTextField ->
                        OutlinedTextFieldDefaults.DecorationBox(
                            value = state.text.toString(),
                            visualTransformation = VisualTransformation.None,
                            innerTextField =
                                if (spellCheckable) {
                                    { SpellCheckedInnerTextField(spell, scrollState, singleLine, innerTextField) }
                                } else {
                                    innerTextField
                                },
                            placeholder = placeholder,
                            label = label,
                            leadingIcon = leadingIcon,
                            trailingIcon = trailingIcon,
                            prefix = prefix,
                            suffix = suffix,
                            supportingText = supportingText,
                            singleLine = singleLine,
                            enabled = enabled,
                            isError = isError,
                            interactionSource = interactionSource,
                            colors = colors,
                            contentPadding = contentPadding,
                            container = {
                                OutlinedTextFieldDefaults.Container(
                                    enabled = enabled,
                                    isError = isError,
                                    interactionSource = interactionSource,
                                    colors = colors,
                                    shape = shape,
                                )
                            },
                        )
                    },
                )
            }
        }
    }
}
