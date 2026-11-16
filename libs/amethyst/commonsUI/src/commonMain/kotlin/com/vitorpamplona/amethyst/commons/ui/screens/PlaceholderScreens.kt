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
package com.vitorpamplona.amethyst.commons.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.screen_messages_description
import com.vitorpamplona.amethyst.commons.resources.screen_messages_title
import com.vitorpamplona.amethyst.commons.resources.screen_notifications_description
import com.vitorpamplona.amethyst.commons.resources.screen_notifications_title
import com.vitorpamplona.amethyst.commons.resources.screen_search_description
import com.vitorpamplona.amethyst.commons.resources.screen_search_title
import org.jetbrains.compose.resources.stringResource

/**
 * Generic placeholder screen with title and description.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Search screen placeholder.
 */
@Composable
fun SearchPlaceholder(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(Res.string.screen_search_title),
        description = stringResource(Res.string.screen_search_description),
        modifier = modifier,
    )
}

/**
 * Messages/DMs screen placeholder.
 */
@Composable
fun MessagesPlaceholder(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(Res.string.screen_messages_title),
        description = stringResource(Res.string.screen_messages_description),
        modifier = modifier,
    )
}

/**
 * Notifications screen placeholder.
 */
@Composable
fun NotificationsPlaceholder(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(Res.string.screen_notifications_title),
        description = stringResource(Res.string.screen_notifications_description),
        modifier = modifier,
    )
}
