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
package com.vitorpamplona.amethyst.commons.ui.actions.uploads

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.core.util.Consumer
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class ContentResolverSharedMedia(
    private val context: Context,
) : SharedMediaResolver {
    override suspend fun resolve(uriString: String?): SelectedMedia? =
        uriString?.ifBlank { null }?.toUri()?.let { uri ->
            withContext(Dispatchers.IO) {
                SelectedMedia(uri, context.contentResolver.getType(uri))
            }
        }

    // One trip to the IO dispatcher for the whole batch instead of one context switch per file.
    override suspend fun resolve(uriStrings: List<String>): ImmutableList<SelectedMedia> {
        val uris = uriStrings.mapNotNull { it.ifBlank { null }?.toUri() }
        if (uris.isEmpty()) return persistentListOf()

        return withContext(Dispatchers.IO) {
            uris.map { SelectedMedia(it, context.contentResolver.getType(it)) }.toImmutableList()
        }
    }
}

@Composable
actual fun rememberSharedMediaResolver(): SharedMediaResolver {
    val context = LocalContext.current.applicationContext
    return remember(context) { ContentResolverSharedMedia(context) }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }

@Composable
actual fun OnIncomingShare(
    onText: (String) -> Unit,
    onMedia: (SelectedMedia) -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findComponentActivity() ?: return
    val scope = rememberCoroutineScope()
    val currentOnText by rememberUpdatedState(onText)
    val currentOnMedia by rememberUpdatedState(onMedia)

    DisposableEffect(activity) {
        val consumer =
            Consumer<Intent> { intent ->
                if (intent.action == Intent.ACTION_SEND) {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.ifBlank { null }?.let {
                        currentOnText(it)
                    }

                    // Use the `intent` parameter (the new intent), not activity.intent (the launch intent).
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uri ->
                        scope.launch(Dispatchers.IO) {
                            val mediaType = context.contentResolver.getType(uri)
                            currentOnMedia(SelectedMedia(uri, mediaType))
                        }
                    }
                }
            }

        activity.addOnNewIntentListener(consumer)
        onDispose { activity.removeOnNewIntentListener(consumer) }
    }
}
