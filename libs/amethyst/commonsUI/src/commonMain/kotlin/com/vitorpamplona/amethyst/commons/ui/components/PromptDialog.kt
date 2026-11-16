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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.hide_password
import com.vitorpamplona.amethyst.commons.resources.show_password
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.CardSurfaces

/** The corners of a [PromptDialog]'s button and fields. */
val PromptShape = RoundedCornerShape(16.dp)

/**
 * A dialog that asks one thing: an icon badge, a title, a short [description], the fields in
 * [content], then the answer as a full-width button and the alternative as a text button below it.
 * Unless [dismissible], only those two buttons close it: a click outside or Back would leave the
 * caller waiting on an answer. An empty [dismissText] leaves the text button out.
 */
@Composable
fun PromptDialog(
    symbol: MaterialSymbol,
    title: String,
    description: String?,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    isError: Boolean = false,
    dismissible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    PromptDialog(
        badge = {
            val badge = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
            val onBadge = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
            Box(Modifier.size(56.dp).background(badge, CircleShape), contentAlignment = Alignment.Center) {
                Icon(symbol, contentDescription = null, tint = onBadge, modifier = Modifier.size(28.dp))
            }
        },
        title = title,
        description = description,
        confirmText = confirmText,
        onConfirm = onConfirm,
        dismissText = dismissText,
        onDismiss = onDismiss,
        confirmEnabled = confirmEnabled,
        dismissible = dismissible,
        content = content,
    )
}

/** A [PromptDialog] headed by any [badge], such as the avatar of the person it is about. */
@Composable
fun PromptDialog(
    badge: @Composable () -> Unit,
    title: String,
    description: String?,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    dismissible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible, usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = CardSurfaces.color,
            border = CardSurfaces.border,
            modifier = Modifier.padding(16.dp).widthIn(max = 400.dp),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                badge()
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                content()
                Column(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                        shape = PromptShape,
                        colors = CardSurfaces.mainButtonColors(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(confirmText, style = MaterialTheme.typography.titleSmall)
                    }
                    // A prompt with one way out (a progress the user can only hide) passes no text.
                    if (dismissText.isNotEmpty()) {
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text(dismissText)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A secret for a [PromptDialog]: a rounded field with a lock and a show/hide toggle. [error] sits
 * right under it; [isError] alone only marks it, for a field another one's error is also about.
 */
@Composable
fun PromptSecretField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    error: String? = null,
    isError: Boolean = error != null,
    onDone: (() -> Unit)? = null,
    autoFocus: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        // The prompt opens at launch, over the app: typing should not wait for a click.
        LaunchedEffect(Unit) {
            try {
                focusRequester.requestFocus()
            } catch (e: IllegalStateException) {
                // Not attached yet; the user can still click into the field.
            }
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = error?.let { { Text(it) } },
        shape = PromptShape,
        leadingIcon = { Icon(MaterialSymbols.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    symbol = if (visible) MaterialSymbols.VisibilityOff else MaterialSymbols.Visibility,
                    contentDescription = stringRes(if (visible) Res.string.hide_password else Res.string.show_password),
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
    )
}
