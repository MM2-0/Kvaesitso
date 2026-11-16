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
package com.vitorpamplona.amethyst.commons.ui.actions.mediaServers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.blossom_sync_done
import com.vitorpamplona.amethyst.commons.resources.blossom_syncing
import com.vitorpamplona.amethyst.commons.service.upload.blossom.BlossomSyncState
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import kotlinx.coroutines.delay

/** How long the finished "Sync complete" banner lingers before it auto-dismisses. */
private const val AUTO_DISMISS_DELAY_MS = 4000L

/**
 * App-wide floating banner for the BUD-04 "sync all" sweep, mounted at the navigation
 * root (a sibling of the mining/broadcast banners) so it floats over every screen and
 * survives navigation. Observes the app-level [com.vitorpamplona.amethyst.commons.service.AppServices.blossomMirrorQueue].
 */
@Composable
fun DisplayBlossomSyncProgress() {
    val queue = LocalAppServices.current.blossomMirrorQueue
    val state by queue.state.collectAsStateWithLifecycle()

    // Retain the last non-null value so the slide-out exit still has content to draw when
    // state clears to null on cancel/dismiss.
    var lastShown by remember { mutableStateOf<BlossomSyncState?>(null) }
    LaunchedEffect(state) { state?.let { lastShown = it } }

    // Once the sweep finishes, auto-dismiss the "Sync complete" banner after a short pause so
    // the user doesn't have to tap X. Keyed on `running`: a new sweep flips it back to true and
    // cancels the pending dismiss; tapping X clears state to null and re-keys this to a no-op.
    val running = state?.running
    LaunchedEffect(running) {
        if (running == false) {
            delay(AUTO_DISMISS_DELAY_MS)
            queue.dismiss()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = state != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier =
                Modifier
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 116.dp)
                    .widthIn(max = 560.dp),
        ) {
            val s = lastShown ?: return@AnimatedVisibility
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            symbol = if (s.running) MaterialSymbols.CloudUpload else MaterialSymbols.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (s.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.allGoodColor,
                        )
                        Text(
                            text = if (s.running) stringRes(Res.string.blossom_syncing) else stringRes(Res.string.blossom_sync_done),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        )
                        IconButton(
                            onClick = { if (s.running) queue.cancel() else queue.dismiss() },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(symbol = MaterialSymbols.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }

                    LinearProgressIndicator(
                        progress = { s.fraction },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )

                    Text(
                        text =
                            buildString {
                                append("${s.done} / ${s.total}")
                                if (s.failed > 0) append("  ·  ${s.failed} failed")
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.grayText,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
