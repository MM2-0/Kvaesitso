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
package com.vitorpamplona.amethyst.service.uploads.blossom

import android.content.Context
import android.content.pm.ServiceInfo
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.service.upload.blossom.BlossomSyncState
import com.vitorpamplona.amethyst.service.foreground.FlowProgressForegroundService
import com.vitorpamplona.amethyst.ui.stringRes

/**
 * Foreground service that keeps the BUD-04 "sync all" sweep ([BlossomMirrorQueue]) running
 * while the app is backgrounded. Uses the Android 14+ `dataSync` type — the correct type for
 * an upload/download/sync operation (a multi-file mirror routinely exceeds the `shortService`
 * ~3-minute budget that PoW mining uses).
 *
 * `dataSync` must be started while the app is foreground; "Sync all" is user-initiated from the
 * manager, so that holds. All of the notification + lifecycle lives in the shared
 * [FlowProgressForegroundService]; this maps the sweep's [BlossomSyncState] onto the card.
 */
class BlossomSyncForegroundService : FlowProgressForegroundService<BlossomSyncState?>() {
    // Only passed to startForeground on API 34+, where the type exists.
    @Suppress("InlinedApi")
    override val fgsType: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    override val channelId = CHANNEL_ID
    override val channelNameRes = R.string.blossom_sync_channel_name
    override val channelDescRes = R.string.blossom_sync_channel_description
    override val notificationId = NOTIFICATION_ID
    override val cancelAction = ACTION_CANCEL
    override val cancelLabelRes = R.string.blossom_sync_cancel

    override fun state() = Amethyst.instance.blossomMirrorQueue.state

    override fun isActive(value: BlossomSyncState?) = value?.running == true

    override fun cancelAll() = Amethyst.instance.blossomMirrorQueue.cancel()

    // State emits on every mirror step (including currentHost changes), so no clock refresh.
    override val refreshMs: Long? = null

    override fun render(value: BlossomSyncState?): Content {
        if (value == null) return Content(stringRes(this, R.string.blossom_syncing), null, Bar.Indeterminate)
        val text =
            buildString {
                append("${value.done} / ${value.total}")
                if (value.failed > 0) append("  ·  ${value.failed} failed")
            }
        return Content(stringRes(this, R.string.blossom_syncing), text, Bar.Determinate(value.fraction.toDouble()))
    }

    companion object {
        private const val TAG = "BlossomSyncFgs"
        private const val CHANNEL_ID = "blossom_sync"
        private const val NOTIFICATION_ID = 0x424C4F // "BLO"
        private const val ACTION_CANCEL = "com.vitorpamplona.amethyst.blossom.SYNC_CANCEL"

        /**
         * Started when a sweep begins (from the foreground); stops itself when it finishes.
         * No `running` de-dup: a sweep starts this exactly once (via [BlossomMirrorQueue.onActive]),
         * and a redundant start would just route to a cheap onStartCommand — whereas a stale
         * "already running" flag could leave a fresh sweep with no foreground protection.
         */
        fun start(context: Context) {
            FlowProgressForegroundService.start(context, BlossomSyncForegroundService::class.java, TAG)
        }
    }
}
