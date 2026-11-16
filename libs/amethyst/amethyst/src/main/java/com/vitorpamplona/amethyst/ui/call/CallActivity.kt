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
package com.vitorpamplona.amethyst.ui.call

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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallState
import com.vitorpamplona.amethyst.commons.relayClient.authCommand.compose.RelayAuthSubscription
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.service.call.CallSessionBridge
import com.vitorpamplona.amethyst.service.call.notification.CallNotifier
import com.vitorpamplona.amethyst.ui.call.session.CallSession
import com.vitorpamplona.amethyst.ui.screen.ManageRelayServices
import com.vitorpamplona.amethyst.ui.screen.ManageWebOkHttp
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType
import kotlinx.coroutines.launch

class CallActivity : AppCompatActivity() {
    val isInPipMode = mutableStateOf(false)

    /** Activity-owned call session. Created in [onCreate], released in [onDestroy]. */
    private var session: CallSession? = null

    // Launcher for requesting call permissions when accepting from notification
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            if (hasCallPermissions(this, isVideo = pendingAcceptIsVideo)) {
                acceptCall()
            }
        }

    private var pendingAcceptIsVideo = false

    private val pipActionReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    ACTION_PIP_HANGUP -> {
                        lifecycleScope.launch { CallSessionBridge.callManager?.hangup() }
                    }

                    ACTION_PIP_TOGGLE_MUTE -> {
                        session?.toggleMute()
                        updatePipParams()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Show over lock screen and turn screen on for incoming calls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }

        val callManager = CallSessionBridge.callManager
        // The session deliberately binds to the account, not to the AccountViewModel: MainActivity
        // (and with it the ViewModel) can be destroyed while this call is running, so a session
        // built on the ViewModel would die with it. The ViewModel below is only for the UI.
        val account = CallSessionBridge.account
        val accountViewModel = CallSessionBridge.accountViewModel

        if (callManager == null || account == null || accountViewModel == null) {
            finish()
            return
        }

        // Create the Activity-owned call session.
        val callSession =
            CallSession(
                context = this,
                callManager = callManager,
                scope = lifecycleScope,
                publishWrap = { wrap -> account.publishCallSignaling(wrap) },
                signerProvider = { account.signer },
                localPubKey = account.signer.pubKey,
                settingsProvider = { account.settings },
            )
        session = callSession

        // No callback wiring needed — CallSession collects from
        // callManager.sessionEvents and renegotiationEvents SharedFlows
        // in its init block.

        registerPipReceiver()
        observeVideoStateForPip(callSession)
        handleIntent(intent)

        setContent {
            // The call's account, not whichever account is logged in if the user switches mid-call.
            AmethystTheme(accountPubKey = account.pubKey) {
                // Pauses relay services when the app pauses
                ManageRelayServices()
                ManageWebOkHttp()

                // Adds this account to the authentication procedures for relays.
                RelayAuthSubscription(accountViewModel)

                // Loads account information + DMs and Notifications from Relays.
                AccountFilterAssemblerSubscription(accountViewModel)

                CallScreen(
                    callManager = callManager,
                    callSession = callSession,
                    accountViewModel = accountViewModel,
                    onCallEnded = { finish() },
                    isInPipMode = isInPipMode.value,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return

        // Accept an incoming call (from notification action)
        if (intent.getBooleanExtra(EXTRA_ACCEPT_CALL, false)) {
            intent.removeExtra(EXTRA_ACCEPT_CALL)
            CallNotifier.cancelIncomingCall(this)

            val callManager = CallSessionBridge.callManager ?: return
            val state = callManager.state.value
            if (state !is CallState.IncomingCall) return

            val isVideo = state.callType == CallType.VIDEO
            pendingAcceptIsVideo = isVideo

            if (hasCallPermissions(this, isVideo)) {
                acceptCall()
            } else {
                permissionLauncher.launch(buildCallPermissions(isVideo))
            }
            return
        }

        // Initiate an outgoing call (from ChatroomScreen)
        if (intent.getBooleanExtra(EXTRA_INITIATE_CALL, false)) {
            intent.removeExtra(EXTRA_INITIATE_CALL)
            val peerPubKeys = intent.getStringArrayExtra(EXTRA_PEER_PUB_KEYS)?.toSet() ?: return
            val callTypeName = intent.getStringExtra(EXTRA_CALL_TYPE) ?: return
            val callType = CallType.valueOf(callTypeName)

            pendingAcceptIsVideo = callType == CallType.VIDEO

            if (hasCallPermissions(this, callType == CallType.VIDEO)) {
                session?.initiate(peerPubKeys, callType)
            } else {
                // Store for after permission grant — will need separate handling
                permissionLauncher.launch(buildCallPermissions(callType == CallType.VIDEO))
            }
            return
        }
    }

    private fun acceptCall() {
        val session = session ?: return
        val callManager = CallSessionBridge.callManager ?: return
        val state = callManager.state.value
        if (state is CallState.IncomingCall) {
            session.accept(state.sdpOffer)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipIfActive()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode.value = isInPictureInPictureMode
    }

    override fun onDestroy() {
        unregisterPipReceiver()

        // Release all WebRTC/audio/notification resources. close() is
        // synchronous and runs before super.onDestroy() cancels
        // lifecycleScope.
        session?.close()
        session = null

        // Publish hangup/reject so the remote side stops ringing.
        // This is the PRIMARY signaling path (covers PiP dismiss,
        // back press, finish()). CallForegroundService.onTaskRemoved
        // is the BACKUP for task-swipe-from-Recents where Activity
        // onDestroy may not complete in time.
        //
        // hangup()/rejectCall() are idempotent — if the state is
        // already Ended/Idle from a prior transition, they return
        // immediately. So double-publishing from both Activity and
        // Service is safe.
        val manager = CallSessionBridge.callManager
        if (manager != null) {
            when (manager.state.value) {
                is CallState.IncomingCall -> {
                    // Best-effort on a detached scope. If the process
                    // dies before completion, the remote 60s timeout
                    // or our 65s watchdog handles it.
                    kotlinx.coroutines
                        .CoroutineScope(
                            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate,
                        ).launch {
                            manager.rejectCall()
                        }
                }

                is CallState.Offering,
                is CallState.Connecting,
                is CallState.Connected,
                -> {
                    kotlinx.coroutines
                        .CoroutineScope(
                            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate,
                        ).launch {
                            manager.hangup()
                        }
                }

                else -> {}
            }
        }

        super.onDestroy()
    }

    private fun observeVideoStateForPip(callSession: CallSession?) {
        callSession ?: return
        lifecycleScope.launch {
            callSession.isRemoteVideoActive.collect {
                updatePipParams()
            }
        }
    }

    private fun enterPipIfActive() {
        val callManager = CallSessionBridge.callManager ?: return
        val state = callManager.state.value
        val isActive =
            state is CallState.Connected ||
                state is CallState.Connecting ||
                state is CallState.Offering

        if (isActive) {
            try {
                enterPictureInPictureMode(buildPipParams())
            } catch (_: Exception) {
                // PiP not supported or activity not in correct state
            }
        }
    }

    private fun updatePipParams() {
        if (!isInPipMode.value) return
        try {
            setPictureInPictureParams(buildPipParams())
        } catch (_: Exception) {
        }
    }

    private fun buildPipParams(): PictureInPictureParams {
        val aspectRatio = computePipAspectRatio()
        val builder =
            PictureInPictureParams
                .Builder()
                .setAspectRatio(aspectRatio)

        builder.setActions(buildPipActions())

        return builder.build()
    }

    private fun computePipAspectRatio(): Rational {
        val s = session ?: return Rational(9, 16)
        val isVideoActive = s.isRemoteVideoActive.value
        val videoRatio = s.remoteVideoAspectRatio.value

        if (isVideoActive && videoRatio != null && videoRatio > 0f) {
            // Clamp to Android's allowed PiP range (roughly 1:2.39 to 2.39:1)
            val clamped = videoRatio.coerceIn(0.42f, 2.39f)
            val num = (clamped * 1000).toInt()
            return Rational(num, 1000)
        }

        // No active video — use portrait ratio
        return Rational(9, 16)
    }

    private fun buildPipActions(): List<RemoteAction> {
        val actions = mutableListOf<RemoteAction>()

        // Mute / Unmute toggle
        val isMuted = session?.isAudioMuted?.value == true
        val muteIntent =
            PendingIntent.getBroadcast(
                this,
                PIP_MUTE_REQUEST_CODE,
                Intent(ACTION_PIP_TOGGLE_MUTE).setPackage(packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val muteAction =
            RemoteAction(
                Icon.createWithResource(
                    this,
                    if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic_on,
                ),
                stringRes(this, if (isMuted) R.string.call_unmute else R.string.call_mute),
                stringRes(this, if (isMuted) R.string.call_unmute else R.string.call_mute),
                muteIntent,
            )
        actions.add(muteAction)

        // Hangup
        val hangupIntent =
            PendingIntent.getBroadcast(
                this,
                PIP_HANGUP_REQUEST_CODE,
                Intent(ACTION_PIP_HANGUP).setPackage(packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val hangupAction =
            RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_call_end),
                stringRes(this, R.string.call_hangup),
                stringRes(this, R.string.call_hangup),
                hangupIntent,
            )
        actions.add(hangupAction)

        return actions
    }

    private fun registerPipReceiver() {
        val filter =
            IntentFilter().apply {
                addAction(ACTION_PIP_HANGUP)
                addAction(ACTION_PIP_TOGGLE_MUTE)
            }
        ContextCompat.registerReceiver(this, pipActionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun unregisterPipReceiver() {
        try {
            unregisterReceiver(pipActionReceiver)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val EXTRA_ACCEPT_CALL = "com.vitorpamplona.amethyst.EXTRA_ACCEPT_CALL"
        const val EXTRA_INITIATE_CALL = "com.vitorpamplona.amethyst.EXTRA_INITIATE_CALL"
        const val EXTRA_PEER_PUB_KEYS = "com.vitorpamplona.amethyst.EXTRA_PEER_PUB_KEYS"
        const val EXTRA_CALL_TYPE = "com.vitorpamplona.amethyst.EXTRA_CALL_TYPE"
        private const val ACTION_PIP_HANGUP = "com.vitorpamplona.amethyst.PIP_HANGUP"
        private const val ACTION_PIP_TOGGLE_MUTE = "com.vitorpamplona.amethyst.PIP_TOGGLE_MUTE"
        private const val PIP_HANGUP_REQUEST_CODE = 0x60001
        private const val PIP_MUTE_REQUEST_CODE = 0x60002

        fun launch(context: Context) {
            context.startActivity(
                Intent(context, CallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }

        fun launchForOutgoingCall(
            context: Context,
            peerPubKeys: Set<String>,
            callType: CallType,
        ) {
            context.startActivity(
                Intent(context, CallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(EXTRA_INITIATE_CALL, true)
                    putExtra(EXTRA_PEER_PUB_KEYS, peerPubKeys.toTypedArray())
                    putExtra(EXTRA_CALL_TYPE, callType.name)
                },
            )
        }
    }
}
