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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.action_refresh
import com.vitorpamplona.amethyst.commons.resources.action_try_again
import com.vitorpamplona.amethyst.commons.resources.error_loading_feed
import com.vitorpamplona.amethyst.commons.resources.feed_empty
import org.jetbrains.compose.resources.stringResource

/**
 * A centered loading state with a progress indicator and message.
 * Can be used by both Desktop and Android apps.
 */
@Composable
fun LoadingState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A centered empty state with title, optional description, and optional refresh action.
 *
 * The optional `description` is wrapped in a [SelectionContainer] so users can
 * select and copy it. `EmptyState` is reused as the in-feed error renderer
 * (e.g. "Error loading feed" with the underlying error in `description`), so
 * making the description selectable lets users copy error text for reporting.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onRefresh: (() -> Unit)? = null,
    refreshLabel: String? = null,
) {
    val actualRefreshLabel = refreshLabel ?: stringResource(Res.string.action_refresh)

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (onRefresh != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRefresh) {
                Text(actualRefreshLabel)
            }
        }
    }
}

/**
 * A centered error state with message and optional retry action.
 *
 * The error `message` is wrapped in a [SelectionContainer] so users can select
 * and copy it — useful for reporting bugs or pasting error text into a search.
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String? = null,
) {
    val actualRetryLabel = retryLabel ?: stringResource(Res.string.action_try_again)

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SelectionContainer {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(actualRetryLabel)
            }
        }
    }
}

/**
 * Empty feed state - a common pattern for feed views.
 */
@Composable
fun FeedEmptyState(
    modifier: Modifier = Modifier,
    title: String? = null,
    onRefresh: (() -> Unit)? = null,
) {
    val actualTitle = title ?: stringResource(Res.string.feed_empty)

    EmptyState(
        title = actualTitle,
        modifier = modifier,
        onRefresh = onRefresh,
    )
}

/**
 * Feed error state - a common pattern for feed views.
 */
@Composable
fun FeedErrorState(
    errorMessage: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val formattedMessage = stringResource(Res.string.error_loading_feed, errorMessage)

    ErrorState(
        message = formattedMessage,
        modifier = modifier,
        onRetry = onRetry,
    )
}
