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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import org.jetbrains.compose.resources.StringResource

// Form fields in the look of the settings search field: a label above, a soft tonal fill with
// rounded corners and no outline until focus (then a primary ring), helper text below.

val FormFieldShape = RoundedCornerShape(14.dp)

/** Colors for an [OutlinedTextField] in the form look; pass with [FormFieldShape]. */
@Composable
fun formFieldColors(): TextFieldColors {
    val colors = MaterialTheme.colorScheme
    val fill = colors.surfaceContainerHigh
    val hint = colors.onSurfaceVariant.copy(alpha = 0.6f)
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = fill,
        unfocusedContainerColor = fill,
        errorContainerColor = fill,
        disabledContainerColor = fill,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = Color.Transparent,
        errorBorderColor = colors.error,
        focusedPlaceholderColor = hint,
        unfocusedPlaceholderColor = hint,
        focusedLeadingIconColor = colors.primary,
        unfocusedLeadingIconColor = colors.onSurfaceVariant,
    )
}

/** A [label] above [field], and [helper] text (red when [isError]) below it. */
@Composable
fun LabeledField(
    label: StringResource,
    modifier: Modifier = Modifier,
    helper: String? = null,
    isError: Boolean = false,
    field: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringRes(label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp),
        )
        field()
        if (helper != null) {
            Text(
                text = helper,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.grayText,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** A full-width text field in the form look. Pair it with [LabeledField] for its label. */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: MaterialSymbol? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: String? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = placeholder?.let { { Text(it, style = textStyle) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) } },
        trailingIcon = trailingIcon,
        prefix = prefix?.let { { Text(it, style = textStyle, color = MaterialTheme.colorScheme.grayText) } },
        textStyle = textStyle,
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        shape = FormFieldShape,
        colors = formFieldColors(),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}

/**
 * Chips for [items] (tap one to remove it) above a field where the next one is typed: a
 * comma, space or the keyboard's Done key finishes it ([onCommit]). [chipLabel] formats a chip
 * and [removeDescription] is the accessibility label of its remove icon.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipsInputField(
    items: List<String>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onCommit: () -> Unit,
    onRemove: (String) -> Unit,
    removeDescription: @Composable (String) -> String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: MaterialSymbol? = null,
    chipLabel: (String) -> String = { it },
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (items.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    val remove = removeDescription(item)
                    InputChip(
                        selected = true,
                        onClick = { onRemove(item) },
                        label = { Text(chipLabel(item)) },
                        trailingIcon = { Icon(MaterialSymbols.Close, contentDescription = remove, modifier = Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(50),
                    )
                }
            }
        }
        FormTextField(
            value = draft,
            onValueChange = { value ->
                onDraftChange(value)
                if (value.endsWith(',') || value.endsWith(' ') || value.endsWith('\n')) onCommit()
            },
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit() }),
        )
    }
}
