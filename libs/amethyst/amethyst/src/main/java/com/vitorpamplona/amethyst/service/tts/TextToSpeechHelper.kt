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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.vitorpamplona.quartz.utils.Log
import java.lang.ref.WeakReference
import java.util.Locale

class TextToSpeechHelper private constructor(
    private val context: WeakReference<Context>,
) : LifecycleEventObserver {
    private val appContext
        get() = context.get()!!.applicationContext

    private var message: String? = null

    private var ttsEngine: TextToSpeechEngine? = null

    private var onStart: (() -> Unit)? = null

    private var onDoneListener: (() -> Unit)? = null

    private var onErrorListener: ((String) -> Unit)? = null

    private var onHighlightListener: ((Pair<Int, Int>) -> Unit)? = null

    private var customActionForDestroy: (() -> Unit)? = null

    init {
        Log.d("Init", "Init TTS")
        initTTS()
    }

    fun registerLifecycle(owner: LifecycleOwner): TextToSpeechHelper {
        owner.lifecycle.addObserver(this)
        return this
    }

    private fun initTTS() =
        context.get()?.run {
            ttsEngine =
                TextToSpeechEngine
                    .getInstance()
                    .setOnCompletionListener { onDoneListener?.invoke() }
                    .setOnErrorListener { onErrorListener?.invoke(it) }
                    .setOnStartListener { onStart?.invoke() }
        }

    fun speak(message: String): TextToSpeechHelper {
        if (ttsEngine == null) {
            initTTS()
        }
        this.message = message

        ttsEngine?.initTTS(
            appContext,
            message,
        )
        return this
    }

    /**
     * This method will highlight the text in the textView
     *
     * @exception Exception("Message can't be null for highlighting !! Call speak() first")
     */
    fun highlight(): TextToSpeechHelper {
        if (message == null) {
            throw Exception("Message can't be null for highlighting !! Call speak() first")
        }
        ttsEngine?.setHighlightedMessage(message!!)
        ttsEngine?.setOnHighlightListener { i, i2 -> onHighlightListener?.invoke(Pair(i, i2)) }
        return this
    }

    fun removeHighlight(): TextToSpeechHelper {
        message = null
        onHighlightListener = null
        return this
    }

    fun destroy(action: (() -> Unit) = {}) {
        ttsEngine?.destroy()
        ttsEngine = null
        action.invoke()
        // A stale helper's lifecycle event must not drop the newer one getInstance handed out.
        synchronized(TextToSpeechHelper::class.java) {
            if (instance === this) instance = null
        }
    }

    fun onStart(onStartListener: () -> Unit): TextToSpeechHelper {
        this.onStart = onStartListener
        return this
    }

    fun onDone(onCompleteListener: () -> Unit): TextToSpeechHelper {
        this.onDoneListener = onCompleteListener
        return this
    }

    fun onError(onErrorListener: (String) -> Unit): TextToSpeechHelper {
        this.onErrorListener = onErrorListener
        return this
    }

    fun onHighlight(onHighlightListener: (Pair<Int, Int>) -> Unit): TextToSpeechHelper {
        this.onHighlightListener = onHighlightListener
        return this
    }

    fun setCustomActionForDestroy(action: () -> Unit): TextToSpeechHelper {
        customActionForDestroy = action
        return this
    }

    fun setLanguage(locale: Locale): TextToSpeechHelper {
        ttsEngine?.setLanguage(locale)
        return this
    }

    fun setPitchAndSpeed(
        pitch: Float = DEF_SPEECH_AND_PITCH,
        speed: Float = DEF_SPEECH_AND_PITCH,
    ): TextToSpeechHelper {
        ttsEngine?.setPitchAndSpeed(pitch, speed)
        return this
    }

    fun resetPitchAndSpeed(): TextToSpeechHelper {
        ttsEngine?.resetPitchAndSpeed()
        return this
    }

    companion object {
        private var instance: TextToSpeechHelper? = null

        fun getInstance(context: Context): TextToSpeechHelper {
            synchronized(TextToSpeechHelper::class.java) {
                if (instance == null) {
                    instance = TextToSpeechHelper(WeakReference(context))
                }
                return instance!!
            }
        }
    }

    override fun onStateChanged(
        source: LifecycleOwner,
        event: Lifecycle.Event,
    ) {
        if (
            event == Lifecycle.Event.ON_DESTROY ||
            event == Lifecycle.Event.ON_STOP ||
            event == Lifecycle.Event.ON_PAUSE
        ) {
            // Every speak() registers on its screen's lifecycle; once destroyed, stop listening.
            source.lifecycle.removeObserver(this)
            destroy { customActionForDestroy?.invoke() }
        }
    }
}
