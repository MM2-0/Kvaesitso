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
package com.vitorpamplona.amethyst.service.lang

import android.util.LruCache
import androidx.compose.runtime.Immutable
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.vitorpamplona.amethyst.commons.model.AccountLanguagePreferences
import com.vitorpamplona.amethyst.commons.state.ModelDownloads
import com.vitorpamplona.quartz.utils.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Immutable
data class ResultOrError(
    val result: String?,
    val sourceLang: String?,
    val targetLang: String?,
)

/**
 * The translation needs a model that can't be used now: it isn't on the device and the download
 * settings don't allow fetching it (e.g. "Wi-Fi only" on mobile data), or the user blocked its
 * language while the request was in flight. Not a "no translation needed" result, so callers must
 * not cache it: the same text translates once the model is allowed.
 */
class TranslationModelUnavailableException : Exception("Translation model unavailable under the current settings")

object LanguageTranslatorService {
    private const val TAG = "LanguageTranslatorService"

    private val executorService: ExecutorService =
        Executors.newFixedThreadPool(maxOf(2, Runtime.getRuntime().availableProcessors() / 2))

    private val identificationOptions =
        LanguageIdentificationOptions
            .Builder()
            .setExecutor(executorService)
            .setConfidenceThreshold(0.6f)
            .build()
    private val languageIdentification = LanguageIdentification.getClient(identificationOptions)

    private val modelManager by lazy { RemoteModelManager.getInstance() }

    private val anyNetwork = DownloadConditions.Builder().build()
    private val unmeteredOnly = DownloadConditions.Builder().requireWifi().build()

    // Translator codes, as TranslationLanguages.toTranslatorCode returns them. Keyed by the pair
    // rather than TranslatorOptions so the translators that use a model can be found and closed
    // before the model is deleted.
    private data class LanguagePair(
        val source: String,
        val target: String,
    )

    private val translators =
        object : LruCache<LanguagePair, Translator>(3) {
            override fun create(pair: LanguagePair): Translator =
                Translation.getClient(
                    TranslatorOptions
                        .Builder()
                        .setExecutor(executorService)
                        .setSourceLanguage(pair.source)
                        .setTargetLanguage(pair.target)
                        .build(),
                )

            override fun entryRemoved(
                evicted: Boolean,
                key: LanguagePair,
                oldValue: Translator,
                newValue: Translator?,
            ) {
                oldValue.close()
            }
        }

    private data class InFlightKey(
        val text: String,
        val translateTo: String,
        val dontTranslateFrom: Set<String>,
        val downloads: ModelDownloads,
    )

    // Coalesces concurrent translation requests for the same (content, settings) — the same note
    // shown in N composables (reposts, notifications) only fires one ML Kit pipeline.
    private val inFlight = ConcurrentHashMap<InFlightKey, Task<ResultOrError>>()

    // Language identification by text. A note that can't translate yet (its model isn't allowed to
    // download) isn't cached by the caller, so without this every scroll back re-identifies it.
    private val detectedLanguages = LruCache<String, String>(500)

    // Whether each translator code's model is on the device, as this process last saw it. Every
    // download and delete goes through this service, so it only needs one disk check per language.
    private val modelOnDevice = ConcurrentHashMap<String, Boolean>()

    // Models being deleted, so the requests that finish together delete each one once.
    private val deleting = ConcurrentHashMap.newKeySet<String>()

    fun clear() {
        translators.evictAll()
        inFlight.clear()
        detectedLanguages.evictAll()
        TranslationsCache.clear()
    }

    fun identifyLanguage(text: String): Task<String> = languageIdentification.identifyLanguage(text)

    /**
     * Translates [text] from [source] to [target]. [downloads] says whether a missing model may be
     * fetched now; when it may not, the task fails with [TranslationModelUnavailableException].
     * [languages] is the account's live language settings: the request may outlive the settings it
     * was made with, so it rechecks them before and after a download, and drops a model whose
     * language was blocked meanwhile.
     */
    fun translate(
        text: String,
        source: String,
        target: String,
        downloads: ModelDownloads,
        languages: AccountLanguagePreferences,
    ): Task<ResultOrError> {
        val sourceLangCode = TranslationLanguages.toTranslatorCode(source)
        val targetLangCode = TranslationLanguages.toTranslatorCode(target)

        if (sourceLangCode == null || targetLangCode == null || sourceLangCode == targetLangCode) {
            return Tasks.forCanceled()
        }

        val pair = LanguagePair(sourceLangCode, targetLangCode)

        return areModelsOnDevice(pair).onSuccessTask(executorService) { onDevice ->
            if (onDevice != true) {
                if (downloads == ModelDownloads.NONE || isUnwanted(pair, languages)) {
                    throw TranslationModelUnavailableException()
                }
            }

            val translator = translators[pair]
            val conditions = if (downloads == ModelDownloads.UNMETERED_ONLY) unmeteredOnly else anyNetwork

            translator.downloadModelIfNeeded(conditions).onSuccessTask(executorService) {
                modelOnDevice[pair.source] = true
                modelOnDevice[pair.target] = true

                // The user may have blocked a language while its model was downloading, and an
                // ML Kit download can't be cancelled: drop what it just stored.
                if (isUnwanted(pair, languages)) {
                    deleteUnwanted(pair.source, languages)
                    deleteUnwanted(pair.target, languages)
                    throw TranslationModelUnavailableException()
                }

                val dict = TranslationDictionary.build(text)
                val encoded = TranslationDictionary.encode(text, dict)

                translator.translate(encoded).continueWith(executorService) { task ->
                    task.exception?.let { throw it }
                    ResultOrError(TranslationDictionary.decode(task.result, dict), source, target)
                }
            }
        }
    }

    /**
     * Translates [text] when [dontTranslateFrom] and [translateTo], the settings the caller caches
     * the result under, ask for it. [languages] is where those settings live, read again around
     * the model download.
     */
    fun autoTranslate(
        text: String,
        dontTranslateFrom: Set<String>,
        translateTo: String,
        downloads: ModelDownloads,
        languages: AccountLanguagePreferences,
    ): Task<ResultOrError> {
        if (!TranslationDictionary.isWorthTranslating(text)) return Tasks.forCanceled()
        return dedupe(InFlightKey(text, translateTo, dontTranslateFrom, downloads)) {
            detectLanguage(text).onSuccessTask(executorService) { detected ->
                if (TranslationLanguages.shouldTranslate(detected, dontTranslateFrom, translateTo)) {
                    translate(text, detected, translateTo, downloads, languages)
                } else {
                    Tasks.forCanceled()
                }
            }
        }
    }

    /**
     * The user just added [language] to "Don't translate from": deletes its model unless it is the
     * account's target or English (built in). In-flight requests that download it afterwards drop
     * it again through their own check.
     */
    fun onLanguageBlocked(
        language: String,
        translateTo: String,
    ) {
        val code = TranslationLanguages.toTranslatorCode(language) ?: return
        if (code == TranslateLanguage.ENGLISH || code == TranslationLanguages.toTranslatorCode(translateTo)) return
        executorService.execute { deleteModel(code) }
    }

    private fun detectLanguage(text: String): Task<String> {
        detectedLanguages.get(text)?.let { return Tasks.forResult(it) }
        return identifyLanguage(text).addOnSuccessListener(executorService) { detectedLanguages.put(text, it) }
    }

    /** Whether the live settings no longer want this pair: either language blocked and not the target. */
    private fun isUnwanted(
        pair: LanguagePair,
        languages: AccountLanguagePreferences,
    ): Boolean = isUnwanted(pair.source, languages) || isUnwanted(pair.target, languages)

    private fun isUnwanted(
        code: String,
        languages: AccountLanguagePreferences,
    ): Boolean {
        if (code == TranslateLanguage.ENGLISH) return false
        if (code == TranslationLanguages.toTranslatorCode(languages.translateTo.value)) return false
        return TranslationLanguages.blocks(languages.dontTranslateFrom.value, code)
    }

    private fun deleteUnwanted(
        code: String,
        languages: AccountLanguagePreferences,
    ) {
        if (isUnwanted(code, languages)) deleteModel(code)
    }

    private fun areModelsOnDevice(pair: LanguagePair): Task<Boolean> {
        val source = isModelOnDevice(pair.source)
        val target = isModelOnDevice(pair.target)
        return Tasks.whenAll(source, target).continueWith(executorService) {
            source.isSuccessful && source.result == true && target.isSuccessful && target.result == true
        }
    }

    private fun isModelOnDevice(code: String): Task<Boolean> {
        if (code == TranslateLanguage.ENGLISH) return Tasks.forResult(true)
        modelOnDevice[code]?.let { return Tasks.forResult(it) }
        return modelManager
            .isModelDownloaded(TranslateRemoteModel.Builder(code).build())
            .addOnSuccessListener(executorService) { modelOnDevice.putIfAbsent(code, it) }
    }

    private fun deleteModel(code: String) {
        if (!deleting.add(code)) return

        // Close the translators that hold the model before deleting its files.
        translators.snapshot().keys.forEach { pair ->
            if (pair.source == code || pair.target == code) translators.remove(pair)
        }

        modelManager
            .deleteDownloadedModel(TranslateRemoteModel.Builder(code).build())
            .addOnCompleteListener(executorService) { task ->
                deleting.remove(code)
                if (task.isSuccessful) {
                    modelOnDevice[code] = false
                    Log.d(TAG) { "Deleted the translation model of blocked language $code" }
                } else {
                    // Unknown state: check the disk again next time.
                    modelOnDevice.remove(code)
                    Log.w(TAG, "Could not delete the translation model of $code", task.exception)
                }
            }
    }

    private inline fun dedupe(
        key: InFlightKey,
        factory: () -> Task<ResultOrError>,
    ): Task<ResultOrError> {
        inFlight[key]?.let { return it }
        val candidate = factory()
        // putIfAbsent guards against a racing caller: keep the winner, drop the loser.
        val winner = inFlight.putIfAbsent(key, candidate) ?: candidate
        winner.addOnCompleteListener(executorService) { inFlight.remove(key, winner) }
        return winner
    }
}
