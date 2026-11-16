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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.lifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.viewmodels.BroadcastUiState
import com.vitorpamplona.amethyst.commons.viewmodels.NestUiState
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Publish-side of the kind-10312 presence loop. Three things wired
 * together as one composable:
 *
 *   - **Heartbeat** — emit on enter, on hand-raise toggle, on
 *     onstage transition, then every [PRESENCE_REFRESH_MS]. Mute
 *     and publishing flags are intentionally NOT keys: every mute
 *     toggle would otherwise round-trip a presence emit (audit
 *     Android #11). The heartbeat reads them via
 *     [rememberUpdatedState] so each refresh picks up the latest
 *     value WITHOUT cancelling and restarting the loop — without
 *     that wrapper the captured-at-launch values stay frozen and
 *     a 30 s refresh would overwrite a fresh mute toggle with the
 *     pre-toggle state, hiding the avatar's mute icon.
 *
 *   - **Debounce** — after a mute toggle, wait
 *     [PRESENCE_DEBOUNCE_MS] for further changes before sending a
 *     fresh presence. The LaunchedEffect's auto-cancel on key
 *     change is the debounce mechanism. Skipped while we're not
 *     broadcasting (`micMutedTag == null`); otherwise the FIRST
 *     composition would publish twice within 500 ms (heartbeat
 *     immediately + debounce-publisher 500 ms later, both with
 *     muted=null) — audit round-2 Android #10.
 *
 *   - **Final leave** — onDispose runs a non-cancellable
 *     publishPresence(publishing=false, onstage=false) so the
 *     "I'm gone" event survives the composable's scope being
 *     cancelled mid-network. Without this the leave event almost
 *     never reaches the relay (audit Android #12).
 */
@Composable
fun NestPresencePublisher(
    account: Account,
    event: MeetingSpaceEvent,
    ui: NestUiState,
    handRaised: Boolean,
    leaveScope: CoroutineScope,
) {
    val micMutedTag: Boolean? =
        when (val b = ui.broadcast) {
            is BroadcastUiState.Broadcasting -> b.isMuted
            else -> null
        }
    val publishingTag: Boolean = ui.publishingNow
    val onstageTag: Boolean = ui.onStageNow

    // Latest snapshots for the heartbeat. Without these, the
    // LaunchedEffect captures the values from FIRST composition; the
    // 30 s refresh would then overwrite a recent mute toggle with the
    // pre-toggle state, which presented as "the mute icon disappeared
    // a few seconds after I muted, but the mic is still hot."
    val currentHandRaised by rememberUpdatedState(handRaised)
    val currentMicMuted by rememberUpdatedState(micMutedTag)
    val currentPublishing by rememberUpdatedState(publishingTag)
    val currentOnstage by rememberUpdatedState(onstageTag)

    LaunchedEffect(event.address().toValue(), handRaised, onstageTag) {
        publishPresence(account, event, currentHandRaised, currentMicMuted, currentPublishing, currentOnstage)
        while (isActive) {
            delay(PRESENCE_REFRESH_MS)
            publishPresence(account, event, currentHandRaised, currentMicMuted, currentPublishing, currentOnstage)
        }
    }

    if (micMutedTag != null) {
        LaunchedEffect(micMutedTag) {
            delay(PRESENCE_DEBOUNCE_MS)
            publishPresence(account, event, handRaised, micMutedTag, publishingTag, onstageTag)
        }
    }

    DisposableEffect(event.address().toValue()) {
        onDispose {
            leaveScope.launch(Dispatchers.IO) {
                runCatching {
                    publishPresence(
                        account = account,
                        event = event,
                        handRaised = false,
                        micMuted = null,
                        publishing = false,
                        onstage = false,
                    )
                }
            }
        }
    }
}

private suspend fun publishPresence(
    account: Account,
    event: MeetingSpaceEvent,
    handRaised: Boolean,
    micMuted: Boolean?,
    publishing: Boolean? = null,
    onstage: Boolean? = null,
) {
    runCatching {
        account.signAndComputeBroadcast(
            MeetingRoomPresenceEvent.build(
                root = event,
                handRaised = handRaised,
                muted = micMuted,
                publishing = publishing,
                onstage = onstage,
            ),
        )
    }
}

private const val PRESENCE_REFRESH_MS = 30_000L
private const val PRESENCE_DEBOUNCE_MS = 500L
