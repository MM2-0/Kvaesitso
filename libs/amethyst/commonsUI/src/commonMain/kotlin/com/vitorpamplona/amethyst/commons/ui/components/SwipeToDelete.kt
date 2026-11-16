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

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue.Settled
import androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.request_deletion
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteContainer(
    modifier: Modifier = Modifier,
    onStartToEnd: () -> Unit,
    content: @Composable (RowScope.() -> Unit),
) {
    val dismissState =
        rememberSwipeToDismissBoxState(
            positionalThreshold = { it * .40f },
        )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = { DismissBackground(dismissState) },
        enableDismissFromEndToStart = false,
        onDismiss = { if (it == StartToEnd) onStartToEnd() },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteWithConfirmation(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    confirmLabelRes: StringResource = Res.string.request_deletion,
    content: @Composable (RowScope.() -> Unit),
) {
    val scope = rememberCoroutineScope()

    val dismissState =
        rememberSwipeToDismissBoxState(
            positionalThreshold = { it * .40f },
        )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            ConfirmDeleteBackground(
                dismissState = dismissState,
                onConfirmDelete = {
                    onDelete()
                    scope.launch { dismissState.reset() }
                },
                onCancel = {
                    scope.launch { dismissState.reset() }
                },
                confirmLabelRes = confirmLabelRes,
            )
        },
        enableDismissFromEndToStart = true,
        content = content,
    )
}

@Composable
fun DismissBackground(dismissState: SwipeToDismissBoxState) {
    val color by animateColorAsState(
        if (dismissState.currentValue > dismissState.targetValue) {
            Color(0xFFFF1744)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "DismissBackground",
    )

    val haptic = LocalHapticFeedback.current
    LaunchedEffect(key1 = dismissState.currentValue > dismissState.targetValue) {
        // doesn't run for the first time or when the list is updated.
        if (dismissState.progress > 0 && dismissState.progress < 1) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .background(color)
                .padding(20.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            MaterialSymbols.Delete,
            contentDescription = stringRes(id = Res.string.request_deletion),
        )
        Spacer(modifier = Modifier)
        Icon(
            MaterialSymbols.Delete,
            contentDescription = stringRes(id = Res.string.request_deletion),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmDeleteBackground(
    dismissState: SwipeToDismissBoxState,
    onConfirmDelete: () -> Unit,
    onCancel: () -> Unit,
    confirmLabelRes: StringResource = Res.string.request_deletion,
) {
    val settled = dismissState.currentValue == Settled && dismissState.targetValue == Settled

    val color by animateColorAsState(
        if (!settled) {
            Color(0xFFFF1744)
        } else {
            Color.Transparent
        },
        label = "ConfirmDeleteBackground",
    )

    val haptic = LocalHapticFeedback.current
    LaunchedEffect(key1 = dismissState.currentValue > dismissState.targetValue) {
        if (dismissState.progress > 0 && dismissState.progress < 1) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .background(color)
                .padding(20.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(enabled = !settled) { onConfirmDelete() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                MaterialSymbols.Delete,
                contentDescription = stringRes(id = confirmLabelRes),
                tint = Color.White,
            )
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Text(
                text = stringRes(id = confirmLabelRes),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(enabled = !settled) { onCancel() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                MaterialSymbols.Close,
                contentDescription = stringRes(id = Res.string.cancel),
                tint = Color.White,
            )
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Text(
                text = stringRes(id = Res.string.cancel),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
