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
package com.vitorpamplona.amethyst.ui.actions.uploads

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.vitorpamplona.amethyst.commons.audio.AnonymizedResult
import com.vitorpamplona.amethyst.commons.audio.PitchShifter
import com.vitorpamplona.amethyst.commons.audio.VoicePreset
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Processes audio files to alter voice characteristics for privacy.
 *
 * Uses the in-house [PitchShifter] (WSOLA time-stretch + resampling) to shift pitch
 * while preserving duration. Preset pitch factors work inversely: factor < 1 raises
 * pitch, factor > 1 lowers pitch — so the shifter receives the reciprocal as its
 * output-to-input frequency ratio.
 */
class VoiceAnonymizer {
    companion object {
        private const val TAG = "VoiceAnonymizer"
        private const val CHANNELS = 1
        private const val BIT_RATE = 128000
    }

    /**
     * Applies voice anonymization to an audio file.
     *
     * The process involves three stages:
     * 1. Decode input audio to PCM (0-30% progress)
     * 2. Apply pitch shifting with [PitchShifter] (30-70% progress)
     * 3. Encode processed audio to AAC (70-100% progress)
     *
     * @param inputFile Source audio file (supports formats decodable by MediaCodec)
     * @param preset Voice transformation preset (NONE is not allowed)
     * @param onProgress Callback invoked with progress value from 0.0 to 1.0
     * @return [Result.success] with [AnonymizedResult] containing the output file,
     *         waveform data, and duration; or [Result.failure] with the exception
     */
    suspend fun anonymize(
        inputFile: File,
        preset: VoicePreset,
        onProgress: (Float) -> Unit = {},
    ): Result<AnonymizedResult> =
        withContext(Dispatchers.IO) {
            if (preset == VoicePreset.NONE) {
                return@withContext Result.failure(
                    IllegalArgumentException("Cannot anonymize with NONE preset"),
                )
            }

            try {
                val outputFile = createOutputFile(inputFile, preset)
                val (pcmData, sampleRate, duration) =
                    decodeAudioToPcm(inputFile) { progress ->
                        onProgress(progress * 0.3f)
                    }

                val processedPcm = applyPitchShift(pcmData, preset)
                onProgress(0.7f)

                val waveform = extractWaveform(processedPcm, sampleRate)

                encodePcmToAac(processedPcm, sampleRate, outputFile) { progress ->
                    onProgress(0.7f + progress * 0.3f)
                }

                onProgress(1f)
                Result.success(AnonymizedResult(outputFile.toOkioPath(), waveform, duration))
            } catch (e: CancellationException) {
                // Let cancellation propagate so structured concurrency works (e.g. the
                // upload screen being dismissed cancels this job).
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to anonymize audio", e)
                Result.failure(e)
            }
        }

    private fun createOutputFile(
        inputFile: File,
        preset: VoicePreset,
    ): File {
        val baseName = inputFile.nameWithoutExtension
        val presetSuffix = preset.name.lowercase()
        val parentDir = inputFile.parentFile ?: inputFile.absoluteFile.parentFile
        return File(parentDir, "${baseName}_$presetSuffix.mp4")
    }

    private data class DecodedAudio(
        val pcmData: FloatArray,
        val sampleRate: Int,
        val duration: Int,
    )

    private data class AudioTrackInfo(
        val trackIndex: Int,
        val format: MediaFormat,
        val mime: String,
        val sampleRate: Int,
        val durationUs: Long,
    )

    private fun findAudioTrack(extractor: MediaExtractor): AudioTrackInfo {
        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                return AudioTrackInfo(
                    trackIndex = i,
                    format = trackFormat,
                    mime = mime,
                    sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    durationUs = trackFormat.getLong(MediaFormat.KEY_DURATION),
                )
            }
        }
        throw IllegalStateException("No audio track found in file")
    }

    private class DecoderInputFeeder(
        private val decoder: MediaCodec,
        private val extractor: MediaExtractor,
        private val durationUs: Long,
        private val onProgress: (Float) -> Unit,
    ) {
        var isDone = false
            private set

        fun feedInput() {
            if (isDone) return

            val inputBufferIndex = decoder.dequeueInputBuffer(10000)
            if (inputBufferIndex < 0) return

            val inputBuffer =
                requireNotNull(decoder.getInputBuffer(inputBufferIndex)) {
                    "Decoder input buffer $inputBufferIndex was null (codec in async mode?)"
                }
            val sampleSize = extractor.readSampleData(inputBuffer, 0)

            if (sampleSize < 0) {
                queueEndOfStream(inputBufferIndex)
            } else {
                queueSampleData(inputBufferIndex, sampleSize)
            }
        }

        private fun queueEndOfStream(inputBufferIndex: Int) {
            decoder.queueInputBuffer(
                inputBufferIndex,
                0,
                0,
                0,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
            )
            isDone = true
        }

        private fun queueSampleData(
            inputBufferIndex: Int,
            sampleSize: Int,
        ) {
            val presentationTimeUs = extractor.sampleTime
            decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
            extractor.advance()
            reportProgress(presentationTimeUs)
        }

        private fun reportProgress(presentationTimeUs: Long) {
            if (durationUs > 0) {
                onProgress((presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
            }
        }
    }

    private class DecoderOutputDrainer(
        private val decoder: MediaCodec,
        private val pcmSamples: MutableList<Float>,
    ) {
        private val bufferInfo = MediaCodec.BufferInfo()
        var isDone = false
            private set

        fun drainOutput() {
            val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputBufferIndex < 0) return

            extractPcmSamples(outputBufferIndex)
            decoder.releaseOutputBuffer(outputBufferIndex, false)
            checkForEndOfStream()
        }

        private fun extractPcmSamples(outputBufferIndex: Int) {
            val outputBuffer =
                requireNotNull(decoder.getOutputBuffer(outputBufferIndex)) {
                    "Decoder output buffer $outputBufferIndex was null (codec in async mode?)"
                }
            val shortBuffer = outputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
            while (shortBuffer.hasRemaining()) {
                pcmSamples.add(shortBuffer.get() / 32768f)
            }
        }

        private fun checkForEndOfStream() {
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                isDone = true
            }
        }
    }

    private suspend fun decodeAudioToPcm(
        inputFile: File,
        onProgress: (Float) -> Unit,
    ): DecodedAudio {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null

        try {
            extractor.setDataSource(inputFile.absolutePath)
            val trackInfo = findAudioTrack(extractor)
            extractor.selectTrack(trackInfo.trackIndex)

            decoder =
                MediaCodec.createDecoderByType(trackInfo.mime).apply {
                    configure(trackInfo.format, null, null, 0)
                    start()
                }

            val estimatedSamples = (trackInfo.sampleRate.toLong() * trackInfo.durationUs / 1_000_000).toInt()
            val pcmSamples = ArrayList<Float>(estimatedSamples)

            val inputFeeder = DecoderInputFeeder(decoder, extractor, trackInfo.durationUs, onProgress)
            val outputDrainer = DecoderOutputDrainer(decoder, pcmSamples)

            while (!outputDrainer.isDone && currentCoroutineContext().isActive) {
                inputFeeder.feedInput()
                outputDrainer.drainOutput()
            }

            val duration = (trackInfo.durationUs / 1_000_000).toInt()
            return DecodedAudio(pcmSamples.toFloatArray(), trackInfo.sampleRate, duration)
        } finally {
            decoder?.safeStopAndRelease()
            extractor.release()
        }
    }

    private fun MediaCodec.safeStopAndRelease() {
        try {
            stop()
        } catch (_: IllegalStateException) {
            // Decoder was never started
        }
        release()
    }

    private fun applyPitchShift(
        pcmData: FloatArray,
        preset: VoicePreset,
    ): FloatArray {
        val baseFactor = preset.pitchFactor
        val factor =
            when (preset) {
                VoicePreset.DEEP, VoicePreset.HIGH -> {
                    // Add ±10% random variation
                    val randomShift = 0.9 + (Math.random() * 0.2)
                    baseFactor * randomShift
                }

                else -> {
                    baseFactor
                }
            }

        // Presets express factor inversely (factor > 1 lowers pitch), so the
        // output-to-input frequency ratio is the reciprocal.
        val frequencyRatio = 1.0 / factor
        return PitchShifter().shift(pcmData, frequencyRatio)
    }

    private fun extractWaveform(
        pcmData: FloatArray,
        sampleRate: Int,
    ): List<Float> {
        val waveform = mutableListOf<Float>()
        var offset = 0

        while (offset < pcmData.size) {
            val end = minOf(offset + sampleRate, pcmData.size)
            var maxAmplitude = 0f
            for (i in offset until end) {
                val amplitude = abs(pcmData[i])
                if (amplitude > maxAmplitude) {
                    maxAmplitude = amplitude
                }
            }
            waveform.add(maxAmplitude * 32768f)
            offset += sampleRate
        }

        return waveform
    }

    private class EncoderInputFeeder(
        private val encoder: MediaCodec,
        private val pcmData: FloatArray,
        private val sampleRate: Int,
        private val onProgress: (Float) -> Unit,
    ) {
        private var inputOffset = 0
        var isDone = false
            private set

        fun feedInput() {
            if (isDone) return

            val inputBufferIndex = encoder.dequeueInputBuffer(10000)
            if (inputBufferIndex < 0) return

            val inputBuffer =
                requireNotNull(encoder.getInputBuffer(inputBufferIndex)) {
                    "Encoder input buffer $inputBufferIndex was null (codec in async mode?)"
                }
            inputBuffer.clear()

            val samplesToWrite = minOf((inputBuffer.capacity() / 2), pcmData.size - inputOffset)
            if (samplesToWrite <= 0) {
                queueEndOfStream(inputBufferIndex)
            } else {
                queueSampleData(inputBufferIndex, inputBuffer, samplesToWrite)
            }
        }

        private fun queueEndOfStream(inputBufferIndex: Int) {
            encoder.queueInputBuffer(
                inputBufferIndex,
                0,
                0,
                0,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
            )
            isDone = true
        }

        private fun queueSampleData(
            inputBufferIndex: Int,
            inputBuffer: ByteBuffer,
            samplesToWrite: Int,
        ) {
            writePcmSamplesToBuffer(inputBuffer, samplesToWrite)
            val presentationTimeUs = (inputOffset * 1_000_000L) / sampleRate
            encoder.queueInputBuffer(inputBufferIndex, 0, inputBuffer.position(), presentationTimeUs, 0)
            inputOffset += samplesToWrite
            onProgress(inputOffset.toFloat() / pcmData.size)
        }

        private fun writePcmSamplesToBuffer(
            inputBuffer: ByteBuffer,
            samplesToWrite: Int,
        ) {
            for (i in 0 until samplesToWrite) {
                val sample =
                    (pcmData[inputOffset + i] * 32767)
                        .toInt()
                        .coerceIn(-32768, 32767)
                        .toShort()
                inputBuffer.putShort(sample)
            }
        }
    }

    private class EncoderOutputDrainer(
        private val encoder: MediaCodec,
        private val muxer: MediaMuxer,
    ) {
        private val bufferInfo = MediaCodec.BufferInfo()
        private var audioTrackIndex = -1
        var isMuxerStarted = false
            private set
        var isDone = false
            private set

        fun drainOutput() {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)

            when {
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> startMuxer()
                outputBufferIndex >= 0 -> processOutputBuffer(outputBufferIndex)
            }
        }

        private fun startMuxer() {
            audioTrackIndex = muxer.addTrack(encoder.outputFormat)
            muxer.start()
            isMuxerStarted = true
        }

        private fun processOutputBuffer(outputBufferIndex: Int) {
            val outputBuffer =
                requireNotNull(encoder.getOutputBuffer(outputBufferIndex)) {
                    "Encoder output buffer $outputBufferIndex was null (codec in async mode?)"
                }
            writeToMuxerIfReady(outputBuffer)
            encoder.releaseOutputBuffer(outputBufferIndex, false)
            checkForEndOfStream()
        }

        private fun writeToMuxerIfReady(outputBuffer: ByteBuffer) {
            if (isMuxerStarted && bufferInfo.size > 0) {
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                muxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)
            }
        }

        private fun checkForEndOfStream() {
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                isDone = true
            }
        }
    }

    private fun createAacFormat(sampleRate: Int): MediaFormat =
        MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        }

    private suspend fun encodePcmToAac(
        pcmData: FloatArray,
        sampleRate: Int,
        outputFile: File,
        onProgress: (Float) -> Unit,
    ) {
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false

        try {
            encoder.configure(createAacFormat(sampleRate), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val inputFeeder = EncoderInputFeeder(encoder, pcmData, sampleRate, onProgress)
            val outputDrainer = EncoderOutputDrainer(encoder, muxer)

            while (!outputDrainer.isDone && currentCoroutineContext().isActive) {
                inputFeeder.feedInput()
                outputDrainer.drainOutput()
            }
            muxerStarted = outputDrainer.isMuxerStarted
        } finally {
            encoder.safeStopAndRelease()
            muxer.safeStopAndRelease(muxerStarted)
        }
    }

    private fun MediaMuxer.safeStopAndRelease(wasStarted: Boolean) {
        if (wasStarted) {
            stop()
        }
        release()
    }
}
