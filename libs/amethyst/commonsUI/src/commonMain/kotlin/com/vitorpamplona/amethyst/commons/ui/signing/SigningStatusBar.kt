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
package com.vitorpamplona.amethyst.commons.ui.signing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.signing_default_label
import com.vitorpamplona.amethyst.commons.resources.signing_progress
import com.vitorpamplona.amethyst.commons.resources.signing_waiting_for_signer
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.delay

@Composable
fun SigningStatusBar(
    opState: SigningOpState,
    modifier: Modifier = Modifier,
) {
    var showPending by remember { mutableStateOf(false) }

    LaunchedEffect(opState) {
        if (opState is SigningOpState.Pending) {
            delay(500)
            showPending = true
        } else {
            showPending = false
        }
    }

    val visible = showPending || opState is SigningOpState.Error

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        when (opState) {
            is SigningOpState.Pending -> {
                var elapsedSeconds by remember { mutableLongStateOf(0L) }

                LaunchedEffect(Unit) {
                    while (true) {
                        delay(1000)
                        elapsedSeconds++
                    }
                }

                Snackbar(
                    shape = RoundedCornerShape(8.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = stringRes(Res.string.signing_waiting_for_signer, elapsedSeconds),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            is SigningOpState.Progress -> {
                Snackbar(
                    shape = RoundedCornerShape(8.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    val label = opState.label ?: stringRes(Res.string.signing_default_label)
                    Text(
                        text = stringRes(Res.string.signing_progress, label, opState.current, opState.total),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            is SigningOpState.Error -> {
                Snackbar(
                    shape = RoundedCornerShape(8.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = opState.message,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            is SigningOpState.Idle -> {}
        }
    }
}
