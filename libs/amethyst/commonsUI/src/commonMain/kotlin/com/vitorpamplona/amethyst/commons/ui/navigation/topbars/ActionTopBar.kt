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
package com.vitorpamplona.amethyst.commons.ui.navigation.topbars

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.create
import com.vitorpamplona.amethyst.commons.resources.post
import com.vitorpamplona.amethyst.commons.resources.publish
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.resources.send
import com.vitorpamplona.amethyst.commons.ui.note.buttons.CloseButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.HalfHorzPadding
import org.jetbrains.compose.resources.StringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionTopBar(
    postRes: StringResource,
    titleRes: StringResource? = null,
    isActive: () -> Boolean = { true },
    onCancel: () -> Unit,
    onPost: () -> Unit,
    isBusy: () -> Boolean = { false },
    additionalActions: @Composable (() -> Unit)? = null,
) = ActionTopBar(
    postLabel = stringRes(postRes),
    titleRes = titleRes,
    isActive = isActive,
    onCancel = onCancel,
    onPost = onPost,
    isBusy = isBusy,
    additionalActions = additionalActions,
)

/**
 * A composer's top bar: Close on the left, the title in the middle, and the main action on the
 * right, labelled [postLabel] for a label worked out at the call site (a plural, a count). While
 * [isBusy] the action shows a spinner instead of its label and cannot be pressed again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionTopBar(
    postLabel: String,
    titleRes: StringResource? = null,
    isActive: () -> Boolean = { true },
    onCancel: () -> Unit,
    onPost: () -> Unit,
    isBusy: () -> Boolean = { false },
    additionalActions: @Composable (() -> Unit)? = null,
) {
    ShorterTopAppBar(
        title = {
            if (titleRes != null) {
                Text(
                    text = stringRes(titleRes),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
        },
        navigationIcon = {
            CloseButton(
                modifier = HalfHorzPadding,
                onPress = onCancel,
            )
        },
        actions = {
            if (additionalActions != null) {
                additionalActions()
            }
            val busy = isBusy()
            Button(
                modifier = HalfHorzPadding,
                enabled = isActive() && !busy,
                onClick = onPost,
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(text = postLabel)
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostingTopBar(
    titleRes: StringResource? = null,
    isActive: () -> Boolean = { true },
    onCancel: () -> Unit,
    onPost: () -> Unit,
    // A private note is not posted, it is sealed and delivered to a named set of
    // people. The button says which of the two is about to happen.
    postRes: StringResource = Res.string.post,
) = ActionTopBar(
    titleRes = titleRes,
    postRes = postRes,
    isActive = isActive,
    onCancel = onCancel,
    onPost = onPost,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingTopBar(
    titleRes: StringResource? = null,
    isActive: () -> Boolean = { true },
    onCancel: () -> Unit,
    onPost: () -> Unit,
    additionalActions: @Composable (() -> Unit)? = null,
) = ActionTopBar(
    titleRes = titleRes,
    postRes = Res.string.save,
    isActive = isActive,
    onCancel = onCancel,
    onPost = onPost,
    additionalActions = additionalActions,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendingTopBar(
    titleRes: StringResource? = null,
    isActive: () -> Boolean = { true },
    onCancel: () -> Unit,
    onPost: () -> Unit,
    additionalActions: @Composable (() -> Unit)? = null,
) = ActionTopBar(
    titleRes = titleRes,
    postRes = Res.string.send,
    isActive = isActive,
    onCancel = onCancel,
    onPost = onPost,
    additionalActions = additionalActions,
)

/**
 * For a standalone piece people find and come back to (an article, a product, a track, an event).
 * The labels follow one rule: Post for a public message in feeds, Send for one only its recipients
 * read, Publish for a standalone piece, Create for a place others join, Save for changing something
 * that already exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishingTopBar(
    titleRes: StringResource? = null,
    isActive: () -> Boolean = { true },
    onCancel: () -> Unit,
    onPost: () -> Unit,
) = ActionTopBar(
    titleRes = titleRes,
    postRes = Res.string.publish,
    isActive = isActive,
    onCancel = onCancel,
    onPost = onPost,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatingTopBar(
    titleRes: StringResource? = null,
    isActive: () -> Boolean = { true },
    onCancel: () -> Unit,
    onPost: () -> Unit,
) = ActionTopBar(
    titleRes = titleRes,
    postRes = Res.string.create,
    isActive = isActive,
    onCancel = onCancel,
    onPost = onPost,
)
