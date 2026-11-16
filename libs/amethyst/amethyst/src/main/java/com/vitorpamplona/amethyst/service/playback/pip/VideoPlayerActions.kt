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
package com.vitorpamplona.amethyst.service.playback.pip

import android.app.PendingIntent
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes

const val ACTION_MUTE = "com.vitorpamplona.amethyst.MUTE"
const val ACTION_PLAY_PAUSE = "com.vitorpamplona.amethyst.PLAY_PAUSE"

class ActionReceiver(
    val onMute: () -> Unit,
    val onPlayPause: () -> Unit,
) : BroadcastReceiver() {
    val filterMute = IntentFilter(ACTION_MUTE)
    val filterPlayPause = IntentFilter(ACTION_PLAY_PAUSE)

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            ACTION_MUTE -> onMute()
            ACTION_PLAY_PAUSE -> onPlayPause()
        }
    }
}

fun createMuteAction(
    context: Context,
    isMuted: Boolean,
): RemoteAction {
    val icon =
        if (isMuted) {
            Icon.createWithResource(context, androidx.media3.session.R.drawable.media3_icon_volume_up)
        } else {
            Icon.createWithResource(context, androidx.media3.session.R.drawable.media3_icon_volume_off)
        }
    val title = stringRes(context, if (isMuted) R.string.muted_button else R.string.mute_button)

    val intent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_MUTE).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    return RemoteAction(icon, title, title, intent)
}

fun createPlayPauseAction(
    context: Context,
    isPlaying: Boolean,
): RemoteAction {
    val icon =
        if (!isPlaying) {
            Icon.createWithResource(context, androidx.media3.ui.compose.material3.R.drawable.media3_icon_play)
        } else {
            Icon.createWithResource(context, androidx.media3.session.R.drawable.media3_icon_pause)
        }
    val title = stringRes(context, if (!isPlaying) R.string.play else R.string.pause)
    val intent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_PLAY_PAUSE).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    return RemoteAction(icon, title, title, intent)
}
