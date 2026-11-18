package de.mm20.launcher2.comms.recording

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import de.mm20.launcher2.comms.telephony.TelosCallSession
import de.mm20.launcher2.ktx.TelosShellEngine
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import rikka.shizuku.Shizuku
import java.io.File

/**
 * Picks a capture path:
 *  - Shizuku/root grant `CAPTURE_AUDIO_OUTPUT` then [MediaRecorder.AudioSource.VOICE_CALL]
 *    (two-way mix on many OEMs, no announcement).
 *  - Unprivileged [CallAudioRecorder] (VOICE_COMMUNICATION → MIC) always as last resort.
 *
 * User preference [CommsGroup.recordingBackend] is honored; if the chosen privileged
 * backend is missing or the grant/start fails, we fall through to the next option.
 */
object RecordingCoordinator : KoinComponent {
    private const val TAG = "RecordingCoordinator"

    private val commsSettings: CommsSettings by inject()

    private val _backend = MutableStateFlow("none")
    val activeBackend = _backend.asStateFlow()

    private val startMutex = kotlinx.coroutines.sync.Mutex()
    private val generation = java.util.concurrent.atomic.AtomicLong()

    /** Deletes recordings older than the configured retention (0 = keep forever). */
    suspend fun purgeOld(context: Context) {
        val days = commsSettings.recordingAutoDeleteDays.first()
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        // listing encrypts older recordings: file work, not for the main thread
        withContext(Dispatchers.IO) {
            for (rec in CallAudioRecorder.list(context)) {
                if (rec.file.lastModified() in 1 until cutoff) CallAudioRecorder.delete(rec.file)
            }
        }
    }

    suspend fun start(
        context: Context,
        phoneNumber: String,
        quality: RecordingQuality,
    ): Boolean {
        val appContext = context.applicationContext
        val requestedGeneration = generation.get()
        startMutex.lock()
        try {
            return withContext(Dispatchers.IO) {
                try {
                    if (CallAudioRecorder.isRecording.value) return@withContext true
                    CallAudioRecorder.applyNomedia(appContext, commsSettings.hideCallRecordingsFromMedia.first())
                    purgeOld(appContext)
                    val pref = commsSettings.recordingBackend.first()
                    val order = when (pref) {
                        "unprivileged" -> listOf("unprivileged")
                        "shizuku" -> listOf("shizuku", "unprivileged")
                        "root" -> listOf("root", "unprivileged")
                        else -> listOf("shizuku", "root", "unprivileged")
                    }
                    for (backend in order) {
                        if (generation.get() != requestedGeneration) return@withContext false
                        val ok = when (backend) {
                            "shizuku" -> tryShizuku(appContext, phoneNumber, quality)
                            "root" -> tryRoot(appContext, phoneNumber, quality)
                            else -> CallAudioRecorder.start(
                                appContext,
                                phoneNumber,
                                quality,
                                privilegedCapture = false,
                                bluetooth = TelosCallSession.ui.value.bluetooth,
                            )
                        }
                        if (ok) {
                            if (generation.get() != requestedGeneration) {
                                CallAudioRecorder.stop()
                                return@withContext false
                            }
                            _backend.value = backend
                            Log.i(TAG, "Recording started via $backend")
                            return@withContext true
                        }
                        CallAudioRecorder.stop()
                    }
                    _backend.value = "none"
                    return@withContext false
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Optional call recording could not start", e)
                    _backend.value = "none"
                    false
                }
            }
        } finally {
            startMutex.unlock()
        }
    }

    fun stop(): File? {
        generation.incrementAndGet()
        _backend.value = "none"
        return CallAudioRecorder.stop()
    }

    private suspend fun tryShizuku(
        context: Context,
        phoneNumber: String,
        quality: RecordingQuality,
    ): Boolean {
        if (!shizukuReady()) return false
        grantCaptureViaShizuku(context.packageName)
        return CallAudioRecorder.start(
            context, phoneNumber, quality,
            privilegedCapture = true,
            bluetooth = TelosCallSession.ui.value.bluetooth,
        )
    }

    private suspend fun tryRoot(
        context: Context,
        phoneNumber: String,
        quality: RecordingQuality,
    ): Boolean {
        if (!rootReady()) return false
        grantCaptureViaRoot(context.packageName)
        return CallAudioRecorder.start(
            context, phoneNumber, quality,
            privilegedCapture = true,
            bluetooth = TelosCallSession.ui.value.bluetooth,
        )
    }

    private fun shizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun rootReady(): Boolean = TelosShellEngine.isRootAvailable()

    private suspend fun grantCaptureViaShizuku(packageName: String) {
        val uid = Process.myUid()
        val cmd =
            "appops set --uid $uid $packageName CAPTURE_AUDIO_OUTPUT allow; " +
                "appops set $packageName CAPTURE_AUDIO_OUTPUT allow; " +
                "appops set $packageName RECORD_AUDIO allow; " +
                "pm grant $packageName android.permission.CAPTURE_AUDIO_OUTPUT; " +
                "cmd appops set $packageName CAPTURE_AUDIO_OUTPUT allow"
        val res = TelosShellEngine.execResult(cmd, timeoutMs = 10_000L)
        if (!res.isSuccess) {
            Log.w(TAG, "Shizuku appops failed: ${res.stderr}")
        }
    }

    private suspend fun grantCaptureViaRoot(packageName: String) {
        val uid = Process.myUid()
        val cmd =
            "appops set --uid $uid $packageName CAPTURE_AUDIO_OUTPUT allow; " +
                "appops set $packageName CAPTURE_AUDIO_OUTPUT allow; " +
                "pm grant $packageName android.permission.CAPTURE_AUDIO_OUTPUT; " +
                "cmd appops set $packageName CAPTURE_AUDIO_OUTPUT allow"
        val res = TelosShellEngine.execRootResult(cmd, timeoutMs = 10_000L)
        if (!res.isSuccess) {
            Log.w(TAG, "Root appops failed: ${res.stderr}")
        }
    }
}
