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
package com.vitorpamplona.amethyst.service.call

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "CallAudioManager"

enum class AudioRoute {
    EARPIECE,
    SPEAKER,
    BLUETOOTH,
}

class CallAudioManager(
    private val context: Context,
) {
    /**
     * Instance id — every log line from this audio manager is prefixed
     * with this so we can trace whether multiple CallAudioManager
     * instances are fighting over the ringtone.
     */
    private val instanceId = nextInstanceId.getAndIncrement()

    private var ringtonePlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var ringbackTone: ToneGenerator? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var scoReceiver: BroadcastReceiver? = null

    init {
        Log.d(TAG) { "[#$instanceId] created on ${Thread.currentThread().name}" }
    }

    companion object {
        private val nextInstanceId =
            java.util.concurrent.atomic
                .AtomicInteger(0)
    }

    private val _audioRoute = MutableStateFlow(AudioRoute.EARPIECE)
    val audioRoute: StateFlow<AudioRoute> = _audioRoute.asStateFlow()

    private fun hasBluetoothPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private val _isBluetoothAvailable = MutableStateFlow(false)
    val isBluetoothAvailable: StateFlow<Boolean> = _isBluetoothAvailable.asStateFlow()

    // Proximity sensor — detects when the phone is held near the ear
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val _isNearEar = MutableStateFlow(false)
    val isNearEar: StateFlow<Boolean> = _isNearEar.asStateFlow()

    private val proximityListener =
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val maxRange = event.sensor.maximumRange
                _isNearEar.value = event.values[0] < maxRange
            }

            override fun onAccuracyChanged(
                sensor: Sensor?,
                accuracy: Int,
            ) {
                // no-op: proximity is a binary near/far signal; accuracy changes don't affect routing.
            }
        }

    fun startRinging() {
        Log.d(TAG) { "[#$instanceId] startRinging on ${Thread.currentThread().name} — existing player=${ringtonePlayer?.hashCode()}" }
        // IDEMPOTENT: always stop any existing player/vibrator before
        // starting a new one. Without this, rapid re-entry (e.g. state
        // flow re-emission when a group member rejects while we are
        // ringing) would leak MediaPlayer/Ringtone instances that keep
        // playing because the old reference is overwritten before stop.
        stopRinging()
        val ringerMode = audioManager.ringerMode
        if (ringerMode != AudioManager.RINGER_MODE_SILENT) {
            if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                startRingtone()
            }
            startVibration()
        }
    }

    fun stopRinging() {
        Log.d(TAG) { "[#$instanceId] stopRinging on ${Thread.currentThread().name} — player=${ringtonePlayer?.hashCode()}" }
        stopRingtone()
        stopVibration()
    }

    fun startRingbackTone() {
        stopRingbackTone()
        try {
            ringbackTone =
                ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80).also {
                    it.startTone(ToneGenerator.TONE_SUP_RINGTONE)
                }
        } catch (_: Exception) {
        }
    }

    fun stopRingbackTone() {
        try {
            ringbackTone?.stopTone()
            ringbackTone?.release()
        } catch (_: Exception) {
        }
        ringbackTone = null
    }

    fun switchToCallAudioMode() {
        if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) return
        previousAudioMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (hasBluetoothPermission()) {
            _isBluetoothAvailable.value = hasBluetoothDevice()
            if (_isBluetoothAvailable.value) {
                startBluetoothSco()
            } else {
                routeToEarpiece()
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                registerBluetoothScoReceiver()
            }
        } else {
            _isBluetoothAvailable.value = false
            routeToEarpiece()
        }
    }

    fun restoreAudioMode() {
        stopBluetoothSco()
        unregisterBluetoothScoReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        audioManager.mode = previousAudioMode
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false
    }

    fun cycleAudioRoute() {
        val hasBt = _isBluetoothAvailable.value
        val next =
            when (_audioRoute.value) {
                AudioRoute.EARPIECE -> if (hasBt) AudioRoute.BLUETOOTH else AudioRoute.SPEAKER
                AudioRoute.BLUETOOTH -> AudioRoute.SPEAKER
                AudioRoute.SPEAKER -> AudioRoute.EARPIECE
            }
        setAudioRoute(next)
    }

    fun setAudioRoute(route: AudioRoute) {
        _audioRoute.value = route
        when (route) {
            AudioRoute.EARPIECE -> routeToEarpiece()
            AudioRoute.SPEAKER -> routeToSpeaker()
            AudioRoute.BLUETOOTH -> routeToBluetooth()
        }
    }

    fun acquireProximityWakeLock() {
        if (proximityWakeLock != null) return
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        proximityWakeLock =
            powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "amethyst:call_proximity",
            )
        // No timeout — the wake lock is held for the entire call duration
        // and released in releaseProximityWakeLock() during cleanup.
        @Suppress("WakelockTimeout")
        proximityWakeLock?.acquire()
        registerProximitySensor()
    }

    fun releaseProximityWakeLock() {
        proximityWakeLock?.let { if (it.isHeld) it.release() }
        proximityWakeLock = null
        unregisterProximitySensor()
    }

    private fun registerProximitySensor() {
        proximitySensor?.let {
            sensorManager?.registerListener(proximityListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun unregisterProximitySensor() {
        sensorManager?.unregisterListener(proximityListener)
        _isNearEar.value = false
    }

    fun release() {
        Log.d(TAG) { "[#$instanceId] release on ${Thread.currentThread().name}" }
        stopRinging()
        stopRingbackTone()
        restoreAudioMode()
        releaseProximityWakeLock()
    }

    private fun routeToEarpiece() {
        stopBluetoothSco()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val earpiece =
                audioManager
                    .availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            earpiece?.let { audioManager.setCommunicationDevice(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
    }

    private fun routeToSpeaker() {
        stopBluetoothSco()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speaker =
                audioManager
                    .availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            speaker?.let { audioManager.setCommunicationDevice(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        }
    }

    private fun routeToBluetooth() {
        if (!hasBluetoothPermission()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val btDevice =
                audioManager
                    .availableCommunicationDevices
                    .firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    }
            btDevice?.let { audioManager.setCommunicationDevice(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            startBluetoothSco()
        }
    }

    private fun hasBluetoothDevice(): Boolean =
        try {
            audioManager
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .any {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
                }
        } catch (_: Exception) {
            false
        }

    @Suppress("DEPRECATION")
    private fun startBluetoothSco() {
        try {
            if (!audioManager.isBluetoothScoOn) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
            }
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun stopBluetoothSco() {
        try {
            if (audioManager.isBluetoothScoOn) {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun registerBluetoothScoReceiver() {
        if (scoReceiver != null) return
        scoReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context?,
                    intent: Intent?,
                ) {
                    val state = intent?.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                    when (state) {
                        AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                            _isBluetoothAvailable.value = true
                            if (_audioRoute.value == AudioRoute.BLUETOOTH) {
                                audioManager.isBluetoothScoOn = true
                            }
                        }

                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                            _isBluetoothAvailable.value = hasBluetoothDevice()
                            if (_audioRoute.value == AudioRoute.BLUETOOTH) {
                                _audioRoute.value = AudioRoute.EARPIECE
                                routeToEarpiece()
                            }
                        }
                    }
                }
            }
        ContextCompat.registerReceiver(
            context,
            scoReceiver,
            IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun unregisterBluetoothScoReceiver() {
        scoReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        scoReceiver = null
    }

    /**
     * Starts the ringtone via [MediaPlayer] (not [android.media.Ringtone]).
     *
     * Why MediaPlayer: `Ringtone.stop()` has documented reliability
     * problems on older Android versions where stop() returns but
     * playback continues, and `isLooping` is only settable on API 28+.
     * `MediaPlayer` has reliable stop/release on all API levels and
     * supports looping universally.
     */
    private fun startRingtone() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val player =
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    setDataSource(context, ringtoneUri)
                    isLooping = true
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG) { "[#$instanceId] ringtone MediaPlayer error what=$what extra=$extra" }
                        true
                    }
                    prepare()
                    start()
                }
            ringtonePlayer = player
            Log.d(TAG) { "[#$instanceId] startRingtone: player=${player.hashCode()} started" }
        } catch (e: Exception) {
            Log.e(TAG, "[#$instanceId] startRingtone failed", e)
        }
    }

    private fun stopRingtone() {
        val player = ringtonePlayer ?: return
        Log.d(TAG) { "[#$instanceId] stopRingtone: player=${player.hashCode()} isPlaying=${runCatching { player.isPlaying }.getOrDefault(false)}" }
        try {
            if (player.isPlaying) player.stop()
        } catch (e: Exception) {
            Log.e(TAG, "[#$instanceId] stopRingtone: stop() failed", e)
        }
        try {
            player.release()
        } catch (e: Exception) {
            Log.e(TAG, "[#$instanceId] stopRingtone: release() failed", e)
        }
        ringtonePlayer = null
    }

    private fun startVibration() {
        try {
            vibrator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
            val pattern = longArrayOf(0, 1000, 1000)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (_: Exception) {
        }
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        vibrator = null
    }
}
