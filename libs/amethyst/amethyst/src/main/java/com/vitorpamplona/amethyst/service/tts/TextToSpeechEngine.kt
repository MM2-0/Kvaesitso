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
package com.vitorpamplona.amethyst.service.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

const val DEF_SPEECH_AND_PITCH = 0.8f

fun getErrorText(errorCode: Int): String =
    when (errorCode) {
        TextToSpeech.ERROR -> "ERROR"
        TextToSpeech.ERROR_INVALID_REQUEST -> "ERROR_INVALID_REQUEST"
        TextToSpeech.ERROR_NETWORK -> "ERROR_NETWORK"
        TextToSpeech.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
        TextToSpeech.ERROR_SERVICE -> "ERROR_SERVICE"
        TextToSpeech.ERROR_SYNTHESIS -> "ERROR_SYNTHESIS"
        TextToSpeech.ERROR_NOT_INSTALLED_YET -> "ERROR_NOT_INSTALLED_YET"
        else -> "UNKNOWN"
    }

class TextToSpeechEngine private constructor() {
    private var tts: TextToSpeech? = null

    private var defaultPitch = DEF_SPEECH_AND_PITCH
    private var defaultSpeed = DEF_SPEECH_AND_PITCH
    private var defLanguage = Locale.getDefault()
    private var onStartListener: (() -> Unit)? = null
    private var onDoneListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    private var onHighlightListener: ((Int, Int) -> Unit)? = null
    private var message: String? = null

    companion object {
        private var instance: TextToSpeechEngine? = null

        fun getInstance(): TextToSpeechEngine {
            if (instance == null) {
                instance = TextToSpeechEngine()
            }
            return instance!!
        }
    }

    fun initTTS(
        context: Context,
        message: String,
    ) {
        // Each call binds a new engine; release the last one or every tap leaves an engine bound
        // (and still speaking) until the process dies.
        tts?.stop()
        tts?.shutdown()
        tts =
            TextToSpeech(context) { result ->
                if (result == TextToSpeech.SUCCESS) {
                    tts?.let { engine ->
                        val result = engine.setLanguage(defLanguage)
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            engine.language = Locale.US
                        }
                        engine.setPitch(defaultPitch)
                        engine.setSpeechRate(defaultSpeed)

                        val audioAttributes =
                            AudioAttributes
                                .Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        engine.setAudioAttributes(audioAttributes)

                        engine.setListener(
                            onStart = { onStartListener?.invoke() },
                            onError = { e -> e?.let { error -> onErrorListener?.invoke(error) } },
                            onRange = { start, end ->
                                if (this@TextToSpeechEngine.message != null) {
                                    onHighlightListener?.invoke(start, end)
                                }
                            },
                            onDone = { onDoneListener?.invoke() },
                        )
                        speak(message)
                    }
                } else {
                    onErrorListener?.invoke(getErrorText(result))
                }
            }
    }

    private fun speak(message: String): TextToSpeechEngine {
        tts?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            TextToSpeech.ACTION_TTS_QUEUE_PROCESSING_COMPLETED,
        )
        return this
    }

    fun setPitchAndSpeed(
        pitch: Float,
        speed: Float,
    ) {
        defaultPitch = pitch
        defaultSpeed = speed
    }

    fun resetPitchAndSpeed() {
        defaultPitch = DEF_SPEECH_AND_PITCH
        defaultSpeed = DEF_SPEECH_AND_PITCH
    }

    fun setLanguage(local: Locale): TextToSpeechEngine {
        this.defLanguage = local
        return this
    }

    fun setHighlightedMessage(message: String) {
        this.message = message
    }

    fun setOnStartListener(onStartListener: (() -> Unit)): TextToSpeechEngine {
        this.onStartListener = onStartListener
        return this
    }

    fun setOnCompletionListener(onDoneListener: () -> Unit): TextToSpeechEngine {
        this.onDoneListener = onDoneListener
        return this
    }

    fun setOnErrorListener(onErrorListener: (String) -> Unit): TextToSpeechEngine {
        this.onErrorListener = onErrorListener
        return this
    }

    fun setOnHighlightListener(onHighlightListener: (Int, Int) -> Unit): TextToSpeechEngine {
        this.onHighlightListener = onHighlightListener
        return this
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        onStartListener = null
        onDoneListener = null
        onErrorListener = null
        onHighlightListener = null
        message = null
        instance = null
    }
}

inline fun TextToSpeech.setListener(
    crossinline onStart: (String?) -> Unit = {},
    crossinline onError: (String?) -> Unit = {},
    crossinline onRange: (Int, Int) -> Unit = { _, _ -> },
    crossinline onDone: (String?) -> Unit,
) = this.apply {
    setOnUtteranceProgressListener(
        object : UtteranceProgressListener() {
            override fun onStart(p0: String?) {
                onStart.invoke(p0)
            }

            override fun onDone(p0: String?) {
                onDone.invoke(p0)
            }

            @Deprecated("Deprecated in Java", ReplaceWith("onError.invoke(p0)"))
            override fun onError(p0: String?) {
                onError.invoke(p0)
            }

            override fun onRangeStart(
                utteranceId: String?,
                start: Int,
                end: Int,
                frame: Int,
            ) {
                super.onRangeStart(utteranceId, start, end, frame)
                onRange.invoke(start, end)
            }
        },
    )
}
