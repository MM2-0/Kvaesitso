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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.activity

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.nests.room.activity.NestBridge
import com.vitorpamplona.amethyst.commons.relayClient.authCommand.compose.RelayAuthSubscription
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.components.toasts.DisplayErrorMessages
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.screen.ManageRelayServices
import com.vitorpamplona.amethyst.ui.screen.ManageWebOkHttp
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Standalone activity that owns the lifetime of an audio-room session. The
 * lobby ([com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.lobby.NestJoinCard])
 * launches this activity when the user taps "Join audio room"; finishing it
 * tears down the MoQ session, the broadcaster (if any), and the foreground
 * notification.
 *
 * Picture-in-Picture: pressing Home or the Recents gesture while connected
 * collapses the activity into a small floating window so the user can
 * keep the speakers visible while doing other things. Mute / leave are
 * exposed via the system PIP overlay as [RemoteAction]s.
 *
 * AccountViewModel arrives via [NestBridge] — same pattern as
 * [com.vitorpamplona.amethyst.ui.call.CallActivity] uses for
 * `CallSessionBridge`.
 */
class NestActivity : AppCompatActivity() {
    private val isInPipMode = mutableStateOf(false)
    private val isMuted = mutableStateOf(false)
    private val isConnected = mutableStateOf(false)

    private val pipReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    ACTION_PIP_TOGGLE_MUTE -> {
                        // Per-Activity SharedFlow — observed by the composable
                        // and forwarded to the VM. Replaces an earlier
                        // process-wide singleton that could leak edges across
                        // activity instances.
                        _toggleMuteSignal.tryEmit(Unit)
                    }

                    ACTION_PIP_LEAVE -> {
                        // Tear down the speaker session + listener BEFORE
                        // finish() so AudioRecord (and the system mic
                        // indicator) releases promptly. onCleared() alone
                        // runs late in the destroy lifecycle.
                        pipCleanupAction?.invoke()
                        finish()
                    }
                }
            }
        }

    /**
     * Wired by [NestActivityBody] via a DisposableEffect. Invokes
     * `viewModel.leave()` so the broadcast handle + listener close
     * route through `cleanupScope` (the application scope) and survive Activity
     * destruction. Called from PIP close paths (the overlay's Leave
     * action and a swipe-dismiss while in PIP).
     */
    @Volatile
    private var pipCleanupAction: (() -> Unit)? = null

    fun setPipCleanupAction(action: (() -> Unit)?) {
        pipCleanupAction = action
    }

    private val _toggleMuteSignal =
        MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Emitted when the PIP-overlay mute action is tapped. Read-only so
     * external code can't tryEmit into it from outside the Activity
     * (audit round-2 Android #2).
     */
    val toggleMuteSignal: SharedFlow<Unit> get() = _toggleMuteSignal.asSharedFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val accountViewModel = NestBridge.accountViewModel
        val addressValue = intent.getStringExtra(EXTRA_ADDRESS)
        if (accountViewModel == null || addressValue == null) {
            // After process death the bridge is empty (the previous
            // process's AccountViewModel is gone). Bounce the user back to
            // MainActivity so they land on the lobby instead of a black
            // screen flashing closed (audit Android #7).
            if (accountViewModel == null) {
                runCatching {
                    startActivity(
                        Intent(this, com.vitorpamplona.amethyst.ui.MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                }
            }
            finish()
            return
        }

        val filter =
            IntentFilter().apply {
                addAction(ACTION_PIP_TOGGLE_MUTE)
                addAction(ACTION_PIP_LEAVE)
            }
        // RECEIVER_NOT_EXPORTED requires API 33+. The PendingIntents that
        // fire these actions all use setPackage(packageName) so the
        // visibility risk on older devices is bounded; pre-33 we register
        // without the flag.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pipReceiver, filter)
        }

        val pipSupported =
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

        setContent {
            AmethystTheme(accountPubKey = accountViewModel.account.pubKey) {
                // Pauses relay services when the app pauses
                ManageRelayServices()
                ManageWebOkHttp()

                // Adds this account to the authentication procedures for relays.
                RelayAuthSubscription(accountViewModel)

                // Loads account information + DMs and Notifications from Relays.
                AccountFilterAssemblerSubscription(accountViewModel)

                NestActivityContent(
                    addressValue = addressValue,
                    accountViewModel = accountViewModel,
                    isInPipMode = isInPipMode.value,
                    isPipSupported = pipSupported,
                    onMuteState = { muted ->
                        if (isMuted.value != muted) {
                            isMuted.value = muted
                            updatePipParams()
                        }
                    },
                    onConnectedChange = { isConnected.value = it },
                    pipMuteSignal = toggleMuteSignal,
                    onLeave = { finish() },
                    onMinimize = { enterPip() },
                )

                // Mirror MainActivity's `AppNavigation` → render toasts
                // inside *this* Activity's Compose tree so promote /
                // demote / kick / room-edit results actually surface in
                // front of the room UI. Without this, every
                // `toastManager.toast(...)` from nest code queues into a
                // StateFlow whose only collector lives in MainActivity —
                // the dialog only becomes visible after the user PIPs out.
                // EmptyNav is the documented stub for hosts that have no
                // navigation graph; the only consumer that reads `nav`
                // is `MultiErrorToastMsg` (zap-error aggregator) — nest
                // call sites emit `ResourceToastMsg` / `StringToastMsg`
                // which don't touch nav.
                DisplayErrorMessages(
                    toastManager = accountViewModel.toastManager,
                    accountViewModel = accountViewModel,
                    nav = EmptyNav(),
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPip()
    }

    /**
     * Detects a PIP swipe-dismiss. When the user dismisses the floating
     * window the system finishes the activity, so [isFinishing] is true
     * by the time onStop runs. Tearing down here (rather than waiting
     * for onCleared) keeps AudioRecord + the system mic indicator from
     * lingering through the destruction queue. Non-PIP onStop (Home /
     * Recents while not in PIP) is a no-op so backgrounding doesn't
     * teardown.
     */
    override fun onStop() {
        if (isInPipMode.value && isFinishing) {
            pipCleanupAction?.invoke()
        }
        super.onStop()
    }

    /**
     * Enter Picture-in-Picture if the audio session is live and the
     * system supports PIP. Called both by [onUserLeaveHint] (Home /
     * Recents gesture) and by user-driven entry points (back gesture,
     * Minimize button in [com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.screen.NestFullScreen]).
     *
     * Gated on `isConnected` so PIP from the lobby/Connecting state
     * doesn't freeze a half-rendered card in Recents.
     */
    fun enterPip() {
        if (!isConnected.value) return
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching { enterPictureInPictureMode(buildPipParams()) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask brings an existing instance forward when the user
        // taps Join from a different room. We finish + let the new
        // intent's startActivity create a fresh activity rather than
        // silently keeping the old room running (audit Android #16).
        val newAddress = intent.getStringExtra(EXTRA_ADDRESS)
        val currentAddress = getIntent()?.getStringExtra(EXTRA_ADDRESS)
        if (newAddress != null && newAddress != currentAddress) {
            finish()
            startActivity(intent)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode.value = isInPictureInPictureMode
    }

    private fun updatePipParams() {
        // Pre-stage params even outside PIP so the next entry shows the
        // correct mute icon (audit Android #15). setPictureInPictureParams
        // is legal in any state on API 26+.
        runCatching { setPictureInPictureParams(buildPipParams()) }
    }

    private fun buildPipParams(): PictureInPictureParams =
        PictureInPictureParams
            .Builder()
            // Landscape ratio — the PIP layout is a horizontal row of
            // avatars under a title (see NestPipScreen), which 9:16
            // squeezed into a sliver. 16:9 fits the row and the room name.
            .setAspectRatio(Rational(16, 9))
            .setActions(buildPipActions())
            .build()

    private fun buildPipActions(): List<RemoteAction> {
        val muteIntent =
            PendingIntent.getBroadcast(
                this,
                PIP_MUTE_REQ,
                Intent(ACTION_PIP_TOGGLE_MUTE).setPackage(packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val leaveIntent =
            PendingIntent.getBroadcast(
                this,
                PIP_LEAVE_REQ,
                Intent(ACTION_PIP_LEAVE).setPackage(packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val muteIconRes = if (isMuted.value) R.drawable.ic_mic_off else R.drawable.ic_mic_on
        val muteLabel = stringRes(this, if (isMuted.value) R.string.nest_unmute else R.string.nest_mute)
        return listOf(
            RemoteAction(Icon.createWithResource(this, muteIconRes), muteLabel, muteLabel, muteIntent),
            RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_call_end),
                stringRes(this, R.string.nest_leave),
                stringRes(this, R.string.nest_leave),
                leaveIntent,
            ),
        )
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipReceiver) }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ADDRESS = "com.vitorpamplona.amethyst.NEST_ADDRESS"
        private const val ACTION_PIP_TOGGLE_MUTE = "com.vitorpamplona.amethyst.NEST_PIP_MUTE"
        private const val ACTION_PIP_LEAVE = "com.vitorpamplona.amethyst.NEST_PIP_LEAVE"
        private const val PIP_MUTE_REQ = 0x6A001
        private const val PIP_LEAVE_REQ = 0x6A002

        fun launch(
            context: Context,
            addressValue: String,
        ) {
            context.startActivity(
                Intent(context, NestActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(EXTRA_ADDRESS, addressValue)
                },
            )
        }
    }
}
