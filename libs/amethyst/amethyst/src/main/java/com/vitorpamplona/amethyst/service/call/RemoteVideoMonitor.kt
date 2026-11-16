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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "RemoteVideoMonitor"
private const val FRAME_TIMEOUT_MS = 2000L
private const val POLL_INTERVAL_MS = 1500L

/**
 * Tracks remote video activity for all peers in a call.
 *
 * Attaches [VideoSink]s to remote [VideoTrack]s and periodically polls
 * whether frames are still arriving. Exposes per-peer activity state
 * and a combined "any video active" flag for the UI.
 */
class RemoteVideoMonitor(
    private val scope: CoroutineScope,
) {
    // Primary remote track (first connected peer) for P2P backward compat
    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    // All remote tracks keyed by peer pubkey (for group call UI)
    private val _remoteVideoTracks = MutableStateFlow<Map<HexKey, VideoTrack>>(emptyMap())
    val remoteVideoTracks: StateFlow<Map<HexKey, VideoTrack>> = _remoteVideoTracks.asStateFlow()

    private val _isRemoteVideoActive = MutableStateFlow(false)
    val isRemoteVideoActive: StateFlow<Boolean> = _isRemoteVideoActive.asStateFlow()

    private val _remoteVideoAspectRatio = MutableStateFlow<Float?>(null)
    val remoteVideoAspectRatio: StateFlow<Float?> = _remoteVideoAspectRatio.asStateFlow()

    // Per-peer video activity
    private val _activePeerVideos = MutableStateFlow<Set<HexKey>>(emptySet())
    val activePeerVideos: StateFlow<Set<HexKey>> = _activePeerVideos.asStateFlow()

    // Primary track monitoring
    private val lastRemoteFrameTimeMs = AtomicLong(0L)
    private var remoteVideoMonitorJob: Job? = null
    private val remoteFrameSink =
        VideoSink { frame: VideoFrame ->
            lastRemoteFrameTimeMs.set(System.currentTimeMillis())
            val w = frame.rotatedWidth
            val h = frame.rotatedHeight
            if (w > 0 && h > 0) {
                _remoteVideoAspectRatio.value = w.toFloat() / h.toFloat()
            }
        }

    // Per-peer monitoring
    private val perPeerFrameSinks = ConcurrentHashMap<HexKey, VideoSink>()
    private val perPeerLastFrameTimeMs = ConcurrentHashMap<HexKey, AtomicLong>()
    private var groupVideoMonitorJob: Job? = null

    /** Protects compound read-modify-write on [_remoteVideoTracks] and
     *  [_remoteVideoTrack] which can be called from WebRTC callback threads. */
    private val trackLock = Any()

    fun onRemoteVideoTrack(
        peerPubKey: HexKey,
        track: VideoTrack,
    ) {
        Log.d(TAG) { "Remote video track from ${peerPubKey.take(8)}" }
        synchronized(trackLock) {
            _remoteVideoTracks.value = _remoteVideoTracks.value + (peerPubKey to track)
            if (_remoteVideoTrack.value == null) {
                _remoteVideoTrack.value = track
                startPrimaryMonitor(track)
            }
        }
        startPeerMonitor(peerPubKey, track)
    }

    fun onPeerRemoved(peerPubKey: HexKey) {
        stopPeerMonitor(peerPubKey)
        synchronized(trackLock) {
            val currentTracks = _remoteVideoTracks.value
            if (peerPubKey in currentTracks) {
                _remoteVideoTracks.value = currentTracks - peerPubKey
                if (_remoteVideoTrack.value == currentTracks[peerPubKey]) {
                    stopPrimaryMonitor()
                    val nextTrack = _remoteVideoTracks.value.values.firstOrNull()
                    _remoteVideoTrack.value = nextTrack
                    if (nextTrack != null) {
                        startPrimaryMonitor(nextTrack)
                    }
                }
            }
        }
    }

    fun dispose() {
        synchronized(trackLock) {
            stopPrimaryMonitor()
            stopGroupMonitor()
            for (peerPubKey in perPeerFrameSinks.keys.toList()) {
                stopPeerMonitor(peerPubKey)
            }
            _remoteVideoTrack.value = null
            _remoteVideoTracks.value = emptyMap()
            _isRemoteVideoActive.value = false
            _remoteVideoAspectRatio.value = null
            _activePeerVideos.value = emptySet()
        }
    }

    private fun startPrimaryMonitor(track: VideoTrack) {
        stopPrimaryMonitor()
        lastRemoteFrameTimeMs.set(System.currentTimeMillis())
        track.addSink(remoteFrameSink)
        remoteVideoMonitorJob =
            scope.launch {
                while (true) {
                    delay(POLL_INTERVAL_MS)
                    val elapsed = System.currentTimeMillis() - lastRemoteFrameTimeMs.get()
                    _isRemoteVideoActive.value = elapsed < FRAME_TIMEOUT_MS
                }
            }
    }

    private fun stopPrimaryMonitor() {
        remoteVideoMonitorJob?.cancel()
        remoteVideoMonitorJob = null
        try {
            _remoteVideoTrack.value?.removeSink(remoteFrameSink)
        } catch (_: Exception) {
        }
    }

    private fun stopGroupMonitor() {
        groupVideoMonitorJob?.cancel()
        groupVideoMonitorJob = null
    }

    private fun startPeerMonitor(
        peerPubKey: HexKey,
        track: VideoTrack,
    ) {
        stopPeerMonitor(peerPubKey)

        val lastFrameTime = AtomicLong(System.currentTimeMillis())
        perPeerLastFrameTimeMs[peerPubKey] = lastFrameTime
        val sink = VideoSink { _: VideoFrame -> lastFrameTime.set(System.currentTimeMillis()) }
        perPeerFrameSinks[peerPubKey] = sink
        track.addSink(sink)
        ensureGroupMonitorRunning()
    }

    private fun stopPeerMonitor(peerPubKey: HexKey) {
        val sink = perPeerFrameSinks.remove(peerPubKey) ?: return
        perPeerLastFrameTimeMs.remove(peerPubKey)
        val track = _remoteVideoTracks.value[peerPubKey]
        try {
            track?.removeSink(sink)
        } catch (_: Exception) {
        }
    }

    private fun ensureGroupMonitorRunning() {
        if (groupVideoMonitorJob != null) return
        groupVideoMonitorJob =
            scope.launch {
                while (true) {
                    delay(POLL_INTERVAL_MS)
                    val now = System.currentTimeMillis()
                    val activePeers = mutableSetOf<HexKey>()
                    for ((peerKey, lastFrame) in perPeerLastFrameTimeMs) {
                        if (now - lastFrame.get() < FRAME_TIMEOUT_MS) {
                            activePeers.add(peerKey)
                        }
                    }
                    _activePeerVideos.value = activePeers
                    val anyActive = activePeers.isNotEmpty()
                    _isRemoteVideoActive.value = anyActive || (now - lastRemoteFrameTimeMs.get() < FRAME_TIMEOUT_MS)
                }
            }
    }
}
