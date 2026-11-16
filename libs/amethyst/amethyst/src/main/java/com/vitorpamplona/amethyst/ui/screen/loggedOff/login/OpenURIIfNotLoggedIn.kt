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
package com.vitorpamplona.amethyst.ui.screen.loggedOff.login

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.invalid_nip19_uri_description
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.ui.components.getActivityOrNull
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OpenURIIfNotLoggedIn(onNewNIP19: suspend (String) -> Unit) {
    val context = LocalContext.current
    val activity = context.getActivityOrNull() ?: return
    val scope = rememberCoroutineScope()

    var currentIntentNextPage by remember {
        val uri =
            activity.intent
                ?.data
                ?.toString()
                ?.ifBlank { null }

        activity.intent.data = null

        mutableStateOf(uri)
    }

    currentIntentNextPage?.let { intentNextPage ->
        var nip19 by remember {
            mutableStateOf(
                Nip19Parser.tryParseAndClean(currentIntentNextPage),
            )
        }

        LaunchedEffect(intentNextPage) {
            if (nip19 != null) {
                nip19?.let {
                    scope.launch {
                        onNewNIP19(it)
                    }
                    nip19 = null
                }
            } else {
                scope.launch {
                    Toast
                        .makeText(
                            context,
                            loadStringRes(Res.string.invalid_nip19_uri_description, intentNextPage),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }

            currentIntentNextPage = null
        }
    }

    DisposableEffect(activity) {
        val consumer =
            Consumer<Intent> { intent ->
                val uri = intent.data?.toString()
                if (!uri.isNullOrBlank()) {
                    val newNip19 = Nip19Parser.tryParseAndClean(uri)
                    if (newNip19 != null) {
                        scope.launch {
                            onNewNIP19(newNip19)
                        }
                    } else {
                        scope.launch {
                            delay(1000)
                            Toast
                                .makeText(
                                    context,
                                    loadStringRes(Res.string.invalid_nip19_uri_description, uri),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    }
                }
            }
        activity.addOnNewIntentListener(consumer)
        onDispose { activity.removeOnNewIntentListener(consumer) }
    }
}
