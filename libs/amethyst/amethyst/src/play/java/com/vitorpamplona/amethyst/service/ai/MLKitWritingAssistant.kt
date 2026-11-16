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
package com.vitorpamplona.amethyst.service.ai

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.proofreading.Proofreader
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.ProofreadingRequest
import com.google.mlkit.genai.rewriting.Rewriter
import com.google.mlkit.genai.rewriting.RewriterOptions
import com.google.mlkit.genai.rewriting.Rewriting
import com.google.mlkit.genai.rewriting.RewritingRequest
import com.vitorpamplona.amethyst.commons.service.ai.WritingAssistant
import com.vitorpamplona.amethyst.commons.service.ai.WritingAssistantStatus
import com.vitorpamplona.amethyst.commons.service.ai.WritingResult
import com.vitorpamplona.amethyst.commons.service.ai.WritingTone
import com.vitorpamplona.amethyst.service.lang.LanguageTranslatorService
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * On-device writing assistance backed by ML Kit GenAI (Gemini Nano through AICore).
 *
 * Clients are cached per (output type, language) because building one is expensive, and
 * the composer asks for every tone at once — so the cache is hit concurrently and must be
 * a concurrent map.
 */
class MLKitWritingAssistant(
    context: Context,
) : WritingAssistant {
    private val context = context.applicationContext

    private val rewriters = ConcurrentHashMap<Long, Rewriter>()
    private val proofreaders = ConcurrentHashMap<Int, Proofreader>()

    @Volatile
    private var closed = false

    private val downloadMutex = Mutex()
    private var downloadRequested = false

    private val languageMutex = Mutex()
    private var lastDetectedText: String? = null
    private var lastDetectedLanguage: WritingLanguage = WritingLanguage.ENGLISH

    private fun rewriterCacheKey(
        @RewriterOptions.OutputType outputType: Int,
        language: WritingLanguage,
    ): Long = (outputType.toLong() shl 32) or language.rewriterCode.toLong()

    private fun getRewriter(
        @RewriterOptions.OutputType outputType: Int,
        language: WritingLanguage,
    ): Rewriter {
        check(!closed) { "MLKitWritingAssistant is closed" }
        val key = rewriterCacheKey(outputType, language)
        val rewriter =
            rewriters.computeIfAbsent(key) {
                Rewriting.getClient(
                    RewriterOptions
                        .builder(context)
                        .setOutputType(outputType)
                        .setLanguage(language.rewriterCode)
                        .build(),
                )
            }
        // close() may have run between the check above and the insert. Undo the insert
        // rather than leaking a client nothing will ever close.
        if (closed) {
            rewriters.remove(key)?.close()
            error("MLKitWritingAssistant is closed")
        }
        return rewriter
    }

    private fun getProofreader(language: WritingLanguage): Proofreader {
        check(!closed) { "MLKitWritingAssistant is closed" }
        val key = language.proofreaderCode
        val proofreader =
            proofreaders.computeIfAbsent(key) {
                Proofreading.getClient(
                    ProofreaderOptions
                        .builder(context)
                        .setInputType(ProofreaderOptions.InputType.KEYBOARD)
                        .setLanguage(key)
                        .build(),
                )
            }
        if (closed) {
            proofreaders.remove(key)?.close()
            error("MLKitWritingAssistant is closed")
        }
        return proofreader
    }

    override suspend fun checkAvailability(): WritingAssistantStatus =
        withContext(Dispatchers.IO) {
            try {
                statusOf(getRewriter(RewriterOptions.OutputType.REPHRASE, WritingLanguage.ENGLISH))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the writing assistant status", e)
                WritingAssistantStatus.Unavailable
            }
        }

    override suspend fun requestDownload(): WritingAssistantStatus =
        withContext(Dispatchers.IO) {
            try {
                downloadMutex.withLock {
                    val rewriter = getRewriter(RewriterOptions.OutputType.REPHRASE, WritingLanguage.ENGLISH)
                    if (!downloadRequested) {
                        downloadRequested = true
                        rewriter.downloadFeature(SilentDownloadCallback).awaitDetached()
                        // The proofreader ships as its own feature: fetch it too, or the
                        // CORRECT tone would stay missing forever. A failure here is not
                        // fatal — the rewriting tones still work.
                        try {
                            getProofreader(WritingLanguage.ENGLISH).downloadFeature(SilentDownloadCallback).awaitDetached()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not download the proofreading model", e)
                        }
                    }
                    statusOf(rewriter)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not download the writing assistant model", e)
                WritingAssistantStatus.Unavailable
            }
        }

    private suspend fun statusOf(rewriter: Rewriter): WritingAssistantStatus =
        when (rewriter.checkFeatureStatus().awaitDetached()) {
            FeatureStatus.AVAILABLE -> WritingAssistantStatus.Available
            FeatureStatus.DOWNLOADING -> WritingAssistantStatus.Downloading
            FeatureStatus.DOWNLOADABLE -> WritingAssistantStatus.Downloadable
            else -> WritingAssistantStatus.Unavailable
        }

    override suspend fun transform(
        text: String,
        tone: WritingTone,
    ): WritingResult {
        val language = detectLanguage(text)
        val transformedText =
            when (tone) {
                WritingTone.CORRECT -> proofread(text, language)
                WritingTone.REPHRASE -> rewrite(text, RewriterOptions.OutputType.REPHRASE, language)
                WritingTone.SHORTER -> rewrite(text, RewriterOptions.OutputType.SHORTEN, language)
                WritingTone.ELABORATE -> rewrite(text, RewriterOptions.OutputType.ELABORATE, language)
                WritingTone.FRIENDLY -> rewrite(text, RewriterOptions.OutputType.FRIENDLY, language)
                WritingTone.PROFESSIONAL -> rewrite(text, RewriterOptions.OutputType.PROFESSIONAL, language)
                WritingTone.EMOJIFY -> rewrite(text, RewriterOptions.OutputType.EMOJIFY, language)
            }

        return WritingResult(
            originalText = text,
            transformedText = transformedText,
            tone = tone,
        )
    }

    /**
     * Every tone of a batch runs over the same text, so the detection result is memoized:
     * the first caller pays for it and the rest read the cached answer.
     */
    private suspend fun detectLanguage(text: String): WritingLanguage =
        languageMutex.withLock {
            if (lastDetectedText == text) return@withLock lastDetectedLanguage

            val detected =
                try {
                    withContext(Dispatchers.IO) {
                        WritingLanguage.fromTag(Tasks.await(LanguageTranslatorService.identifyLanguage(text)))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not identify the language of the post", e)
                    WritingLanguage.ENGLISH
                }

            lastDetectedText = text
            lastDetectedLanguage = detected
            detected
        }

    private suspend fun rewrite(
        text: String,
        @RewriterOptions.OutputType outputType: Int,
        language: WritingLanguage,
    ): String {
        // Building a client touches disk and another process; awaiting the inference does not.
        val rewriter = withContext(Dispatchers.IO) { getRewriter(outputType, language) }
        val result = rewriter.runInference(RewritingRequest.builder(text).build()).awaitDetached()
        return result.results.firstOrNull()?.text ?: text
    }

    private suspend fun proofread(
        text: String,
        language: WritingLanguage,
    ): String {
        val proofreader = withContext(Dispatchers.IO) { getProofreader(language) }
        val result = proofreader.runInference(ProofreadingRequest.builder(text).build()).awaitDetached()
        return result.results.firstOrNull()?.text ?: text
    }

    override fun close() {
        closed = true
        rewriters.keys.toList().forEach { rewriters.remove(it)?.close() }
        proofreaders.keys.toList().forEach { proofreaders.remove(it)?.close() }
    }

    /**
     * The two ML Kit APIs declare their own language constants. They happen to share the
     * same numbering today; this enum keeps our call sites from depending on that.
     */
    enum class WritingLanguage(
        val rewriterCode: Int,
        val proofreaderCode: Int,
    ) {
        ENGLISH(RewriterOptions.Language.ENGLISH, ProofreaderOptions.Language.ENGLISH),
        JAPANESE(RewriterOptions.Language.JAPANESE, ProofreaderOptions.Language.JAPANESE),
        KOREAN(RewriterOptions.Language.KOREAN, ProofreaderOptions.Language.KOREAN),
        GERMAN(RewriterOptions.Language.GERMAN, ProofreaderOptions.Language.GERMAN),
        FRENCH(RewriterOptions.Language.FRENCH, ProofreaderOptions.Language.FRENCH),
        ITALIAN(RewriterOptions.Language.ITALIAN, ProofreaderOptions.Language.ITALIAN),
        SPANISH(RewriterOptions.Language.SPANISH, ProofreaderOptions.Language.SPANISH),
        ;

        companion object {
            fun fromTag(tag: String?): WritingLanguage =
                when (tag?.lowercase()?.take(2)) {
                    "en" -> ENGLISH
                    "ja" -> JAPANESE
                    "ko" -> KOREAN
                    "de" -> GERMAN
                    "fr" -> FRENCH
                    "it" -> ITALIAN
                    "es" -> SPANISH
                    else -> ENGLISH
                }
        }
    }

    private object SilentDownloadCallback : DownloadCallback {
        override fun onDownloadFailed(e: GenAiException) {
            Log.w(TAG, "Writing assistant model download failed", e)
        }
    }

    companion object {
        private const val TAG = "MLKitWritingAssistant"
    }
}
