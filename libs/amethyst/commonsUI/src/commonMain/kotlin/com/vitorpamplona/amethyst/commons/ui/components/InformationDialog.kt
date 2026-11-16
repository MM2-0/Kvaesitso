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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.copy_stack_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.error_dialog_details
import com.vitorpamplona.amethyst.commons.ui.components.toasts.ThrowableToastMsg
import com.vitorpamplona.amethyst.commons.ui.components.toasts.ThrowableToastMsg2
import com.vitorpamplona.amethyst.commons.ui.components.toasts.ToastSeverity
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.launch

@Composable
fun InformationDialog(
    obj: ThrowableToastMsg,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onDismiss: () -> Unit,
) {
    val str = obj.msg ?: obj.throwable.message ?: obj.throwable::class.simpleName ?: ""

    val stack =
        remember(obj) {
            obj.throwable.stackTraceToString()
        }

    InformationDialog(title = stringRes(obj.titleResId), textContent = str, moreInfo = stack, buttonColors, severity = obj.severity, onDismiss = onDismiss)
}

@Composable
fun InformationDialog(
    obj: ThrowableToastMsg2,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onDismiss: () -> Unit,
) {
    val str = stringRes(obj.description)

    val stack =
        remember(obj) {
            obj.throwable.stackTraceToString()
        }

    InformationDialog(title = stringRes(obj.titleResId), textContent = str, moreInfo = stack, buttonColors, severity = obj.severity, onDismiss = onDismiss)
}

/**
 * A message the user has to see: a severity icon and color, the title, the message (selectable),
 * and, when there is one, the technical detail (a stack trace) folded behind "Details" with its own
 * copy button, so it is there for a bug report without being the first thing anyone reads.
 *
 * [buttonColors] is kept for callers that tint the dismiss button; [confirmLabel] names it.
 */
@Composable
fun InformationDialog(
    title: String,
    textContent: String,
    moreInfo: String? = null,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    severity: ToastSeverity = ToastSeverity.WARNING,
    confirmLabel: String = stringRes(Res.string.close),
    onDismiss: () -> Unit,
) {
    val (symbol, tint) = severity.look()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(symbol = symbol, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp)) },
        title = { Text(title, textAlign = TextAlign.Center) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Text(textContent, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (moreInfo != null) ErrorDetails(moreInfo)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = buttonColors.containerColor)) {
                Text(confirmLabel)
            }
        },
    )
}

/**
 * Technical detail for a bug report, folded behind "Details": a monospace, scrollable, selectable
 * block with a copy button. Shared by the message dialog and the desktop crash window.
 *
 * The block scrolls on its own and is capped at a fixed height. With [fillHeight] it takes the
 * height [modifier] gives the unfolded part instead (a `weight` in the caller's column), for a window
 * whose content does not scroll.
 */
@Composable
fun ErrorDetails(
    moreInfo: String,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(top = 8.dp)) {
        Icon(
            symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(stringRes(Res.string.error_dialog_details), modifier = Modifier.padding(start = 4.dp))
    }

    // Unfolds downward from the button, like a disclosure.
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        modifier = modifier,
    ) {
        Column(if (fillHeight) Modifier.fillMaxHeight() else Modifier) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (fillHeight) Modifier.weight(1f) else Modifier.heightIn(max = 220.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                SelectionContainer {
                    Text(moreInfo, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
            val clipboard = LocalClipboard.current
            val scope = rememberCoroutineScope()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { scope.launch { clipboard.setText(moreInfo) } }) {
                    Icon(symbol = MaterialSymbols.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(stringRes(Res.string.copy_stack_to_clipboard), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

/** The icon and its color for a [ToastSeverity]. */
@Composable
private fun ToastSeverity.look(): Pair<MaterialSymbol, Color> =
    when (this) {
        ToastSeverity.ERROR -> MaterialSymbols.Error to MaterialTheme.colorScheme.error
        ToastSeverity.WARNING -> MaterialSymbols.Warning to MaterialTheme.colorScheme.tertiary
        ToastSeverity.INFO -> MaterialSymbols.Info to MaterialTheme.colorScheme.primary
    }
