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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.expand_relay_list
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ShowMoreRelaysButtonBoxModifer
import com.vitorpamplona.amethyst.commons.ui.theme.Size17Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample

@OptIn(ExperimentalLayoutApi::class, FlowPreview::class)
@Composable
fun RenderAllRelayList(
    baseNote: Note,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Cold wrapper: `flow()` must be re-resolved on every collection start.
    // A memory trim destroys the NoteFlowSet while the lifecycle is stopped;
    // a stateFlow captured in remember would then be orphaned and never see
    // another relay update.
    val flow =
        remember(baseNote) {
            flow { emitAll(baseNote.flow().relays.stateFlow) }
                .sample(500)
                .map { it.note.relays }
                .distinctUntilChanged()
        }

    val relays by flow.collectAsStateWithLifecycle(baseNote.relays)

    FlowRow(modifier, verticalArrangement = verticalArrangement) {
        relays.forEach { RenderRelay(it, accountViewModel, nav) }
    }
}

// Single sampled subscription instead of one per slot: emits the first 3 relays from the note.
// Throttled to 500ms because relay arrivals can churn a list of an actively-fanned-out note.
@OptIn(FlowPreview::class)
@Composable
fun RenderClosedRelayList(
    baseNote: Note,
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Cold wrapper for the same trim-survival reason as RenderAllRelayList.
    val flow =
        remember(baseNote) {
            flow { emitAll(baseNote.flow().relays.stateFlow) }
                .sample(500)
                .map { it.note.relays.take(3) }
                .distinctUntilChanged()
        }

    val initial = remember(baseNote) { baseNote.relays.take(3) }
    val relays by flow.collectAsStateWithLifecycle(initial)

    Row(modifier, verticalAlignment = verticalAlignment) {
        RenderRelaySlot(relays.getOrNull(0), accountViewModel, nav)
        RenderRelaySlot(relays.getOrNull(1), accountViewModel, nav)
        RenderRelaySlot(relays.getOrNull(2), accountViewModel, nav)
    }
}

@Composable
private fun RenderRelaySlot(
    relay: NormalizedRelayUrl?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    CrossfadeIfEnabled(targetState = relay, label = "RenderRelay", modifier = Size17Modifier) {
        if (it != null) {
            RenderRelay(it, accountViewModel, nav)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview
@Composable
fun RelayIconLayoutPreview() {
    Column(modifier = Modifier.width(55.dp)) {
        Spacer(StdVertSpacer)

        // FlowRow Seems to be a lot faster than LazyVerticalGrid
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, end = 1.dp),
        ) {
            FlowRow {
                Box(
                    modifier = Modifier.size(17.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(shape = CircleShape)
                            .background(Color.Black),
                    )
                }
                Box(
                    modifier = Modifier.size(17.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(shape = CircleShape)
                            .background(Color.Black),
                    )
                }
                Box(
                    modifier = Modifier.size(17.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(shape = CircleShape)
                            .background(Color.Black),
                    )
                }

                Box(
                    modifier = Modifier.size(17.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(shape = CircleShape)
                            .background(Color.Black),
                    )
                }
                Box(
                    modifier = Modifier.size(17.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(shape = CircleShape)
                            .background(Color.Black),
                    )
                }
                Box(
                    modifier = Modifier.size(17.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(shape = CircleShape)
                            .background(Color.Black),
                    )
                }
            }
        }

        ShowMoreRelaysButton { }
    }
}

@Composable
private fun ShowMoreRelaysButton(onClick: () -> Unit) {
    Row(
        modifier = ShowMoreRelaysButtonBoxModifer,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(
            onClick = onClick,
        ) {
            Icon(
                symbol = MaterialSymbols.ExpandMore,
                contentDescription = stringRes(id = Res.string.expand_relay_list),
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }
    }
}
