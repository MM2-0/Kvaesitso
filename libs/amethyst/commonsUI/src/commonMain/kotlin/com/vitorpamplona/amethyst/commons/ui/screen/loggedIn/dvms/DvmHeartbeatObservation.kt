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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.dvms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip90DVMs.DvmHeartbeatRegistry
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteAndMap
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.dvm_offline_banner
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val HEARTBEAT_RECHECK_MILLIS = 30_000L

/**
 * True while the DVM announced at [appDefinitionAddress] has a heartbeat (kind 11998) at most
 * 420s old. Resolves the beat's cache note by its mirror address, opens a composable-scoped
 * relay subscription (the event-finder assembler fetches the beat by kind/author/d while it is
 * missing), and re-checks staleness on a timer — an expired beat produces no cache event.
 */
@Composable
fun rememberDvmHeartbeatFresh(
    appDefinitionAddress: Address,
    accountViewModel: AccountViewModel,
): State<Boolean> {
    val heartbeatAddressTag =
        remember(appDefinitionAddress) {
            Address.assemble(DvmHeartbeatEvent.KIND, appDefinitionAddress.pubKeyHex, appDefinitionAddress.dTag)
        }

    var heartbeatNote by
        remember(heartbeatAddressTag) {
            mutableStateOf(LocalCache.getNoteIfExists(heartbeatAddressTag))
        }
    LaunchedEffect(heartbeatAddressTag) {
        if (heartbeatNote == null) {
            heartbeatNote = LocalCache.checkGetOrCreateNote(heartbeatAddressTag)
        }
    }

    val observed =
        heartbeatNote?.let { observeNoteAndMap(it, accountViewModel) { it.event as? DvmHeartbeatEvent } }

    // The registry is the eviction-proof freshness source (beat notes are WeakReference-held);
    // the observed note is the secondary source and also drives the outbox fetch.
    val registryBeat by
        DvmHeartbeatRegistry.flowForPublic(appDefinitionAddress).collectAsStateWithLifecycle()

    var now by remember(heartbeatNote) { mutableLongStateOf(TimeUtils.now()) }
    LaunchedEffect(heartbeatNote) {
        while (isActive) {
            delay(HEARTBEAT_RECHECK_MILLIS)
            now = TimeUtils.now()
        }
    }

    val beat = observed?.value
    val bestBeat = maxOf(beat?.createdAt ?: 0L, registryBeat)
    return rememberUpdatedState(bestBeat > 0L && bestBeat >= now - DvmHeartbeatEvent.MAX_AGE_SECONDS)
}

/** Floating "DVM is offline" banner, mirroring the Home status banner's card style. */
@Composable
fun DvmOfflineBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Text(
                text = stringRes(Res.string.dvm_offline_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
