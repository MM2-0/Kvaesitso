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
package com.vitorpamplona.amethyst.ui.note.types

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.podcast_value_error_title
import com.vitorpamplona.amethyst.commons.resources.podcast_value_stream
import com.vitorpamplona.amethyst.commons.resources.podcast_value_stream_hint
import com.vitorpamplona.amethyst.commons.resources.podcast_value_stream_rate
import com.vitorpamplona.amethyst.commons.resources.podcast_value_stream_requires_wallet
import com.vitorpamplona.amethyst.commons.resources.podcast_value_streamed_total
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.quartz.podcasts.PodcastStreamingAccrual
import com.vitorpamplona.quartz.podcasts.PodcastValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// How often the accrual loop checks whether audio is genuinely playing. Cheap (one wake/second),
// and re-reading the live isPlaying each tick is what keeps streaming honest: if the player paused,
// went to the background, lost audio focus, errored, or was released, we simply stop accruing.
private const val STREAM_TICK_MS = 1_000L

// Per-minute rates the user can pick from. Deliberately small and explicit — streaming sends sats
// automatically, so the choices are bounded and the selected rate is always shown on the toggle.
private val STREAM_RATE_CHOICES = listOf(1L, 5L, 10L, 21L, 50L, 100L)
private const val DEFAULT_STREAM_RATE = 10L

/**
 * Per-minute "streaming" payments for a podcast episode (Podcasting-2.0 value-for-value, streaming
 * model). A switch — **off by default** — that, while on, sends the show/episode's [PodcastValue]
 * split once per full minute of playback, at the chosen sats/minute rate.
 *
 * The whole point is that it must never pay while the user isn't listening, so accrual is bound
 * tightly to real playback:
 * - It lives inside the player composable, so navigating away disposes it and stops streaming.
 * - Every second it re-reads the live [MediaControllerState.controller] and only accrues when audio
 *   is genuinely **audible**: playing, no playback error, in-app volume > 0, and system media volume
 *   > 0. `isPlaying` alone is not enough — a muted player (the player's mute button sets volume to 0)
 *   or a system volume of 0 keeps `isPlaying` true while the user hears nothing, and we must not
 *   spend then. The player also pauses itself on background / off-screen / error, stopping accrual.
 * - Only whole, actually-played minutes are billed ([PodcastStreamingAccrual]); a partial minute is
 *   dropped when the session ends.
 * - The toggle is gated to an in-app wallet (NWC/CLINK debit). Streaming to an external wallet app
 *   would mean firing a payment intent every minute, which we never do.
 */
@Composable
fun PodcastStreamingControl(
    value: PodcastValue,
    note: Note,
    episodeName: String?,
    podcastName: String?,
    controllerState: MediaControllerState,
    accountViewModel: AccountViewModel,
) {
    val payableRecipients = remember(value) { value.recipients.count { it.split > 0 && !it.address.isNullOrBlank() } }
    if (payableRecipients == 0) return

    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val hasInAppWallet = remember { accountViewModel.account.settings.defaultPaymentSource() != null }

    // Deliberately plain remember (not rememberSaveable): streaming must never silently resume after
    // a rotation or process death. Any fresh creation of this control starts OFF; the user re-opts in.
    var enabled by remember(note.idHex) { mutableStateOf(false) }
    var rate by remember(note.idHex) { mutableLongStateOf(DEFAULT_STREAM_RATE) }
    var rateMenuOpen by remember { mutableStateOf(false) }
    var streamedSats by remember(note.idHex) { mutableLongStateOf(0L) }

    if (enabled) {
        // Restart the loop whenever the toggle, the player, or the rate changes; cancels (and so
        // stops streaming) when this composable leaves the tree.
        LaunchedEffect(controllerState, rate) {
            val accrual = PodcastStreamingAccrual()
            while (isActive) {
                delay(STREAM_TICK_MS)
                // Accrue only when audio is genuinely AUDIBLE, not merely "playing". isPlaying stays
                // true when the player is muted (the player's mute button sets volume to 0) or when
                // the system media volume is at 0 — in both cases the user hears nothing, so we must
                // not spend. Require: playing, no error, in-app volume > 0, and system media volume > 0.
                val audible =
                    runCatching {
                        controllerState.controller.isPlaying &&
                            controllerState.playbackError.value == null &&
                            controllerState.controller.volume > 0.001f &&
                            (audioManager?.let { it.getStreamVolume(AudioManager.STREAM_MUSIC) > 0 } ?: true)
                    }.getOrDefault(false)
                if (audible) {
                    val minutes = accrual.accrue(STREAM_TICK_MS)
                    if (minutes > 0) {
                        val amount = minutes * rate
                        streamedSats += amount
                        accountViewModel.payV4V(
                            value = value,
                            totalSats = amount,
                            podcastName = podcastName,
                            episodeName = episodeName,
                            zappedNote = note,
                            streaming = true,
                        )
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Bolt,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringRes(Res.string.podcast_value_stream),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                // Rate chip — tap to change sats/minute.
                Text(
                    text = stringRes(Res.string.podcast_value_stream_rate, rate.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { rateMenuOpen = true },
                )
                DropdownMenu(
                    expanded = rateMenuOpen,
                    onDismissRequest = { rateMenuOpen = false },
                ) {
                    STREAM_RATE_CHOICES.forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(stringRes(Res.string.podcast_value_stream_rate, choice.toInt())) },
                            onClick = {
                                rate = choice
                                rateMenuOpen = false
                            },
                        )
                    }
                }
            }
            val status =
                if (streamedSats > 0L) {
                    stringRes(Res.string.podcast_value_streamed_total, streamedSats.toInt())
                } else {
                    stringRes(Res.string.podcast_value_stream_hint)
                }
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.grayText,
            )
        }

        Switch(
            checked = enabled,
            onCheckedChange = { wantOn ->
                if (wantOn && !hasInAppWallet) {
                    // No NWC/CLINK wallet -> we won't auto-stream; tell the user why and stay off.
                    accountViewModel.toastManager.toast(
                        Res.string.podcast_value_error_title,
                        Res.string.podcast_value_stream_requires_wallet,
                    )
                } else {
                    enabled = wantOn
                    if (!wantOn) streamedSats = 0L
                }
            },
        )
    }
}
