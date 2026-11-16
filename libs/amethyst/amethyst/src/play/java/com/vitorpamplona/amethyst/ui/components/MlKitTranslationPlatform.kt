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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.AccountLanguagePreferences
import com.vitorpamplona.amethyst.commons.state.ModelDownloads
import com.vitorpamplona.amethyst.commons.ui.components.TranslationConfig
import com.vitorpamplona.amethyst.commons.ui.components.TranslationPlatform
import com.vitorpamplona.amethyst.commons.ui.theme.MaxWidthPaddingTop5dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.lang.LanguageTranslatorService
import com.vitorpamplona.amethyst.service.lang.ResultOrError
import com.vitorpamplona.amethyst.service.lang.TranslationsCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** ML Kit's on-device translator, which only the Play build ships. */
object MlKitTranslationPlatform : TranslationPlatform {
    @Composable
    override fun Translatable(
        content: String,
        id: String,
        translationMessageModifier: Modifier,
        accountViewModel: AccountViewModel,
        displayText: @Composable (String) -> Unit,
    ) = MlKitTranslatable(content, id, translationMessageModifier, accountViewModel, displayText)

    @Composable
    override fun rememberTranslation(
        content: String,
        accountViewModel: AccountViewModel,
    ): String = rememberMlKitTranslation(content, accountViewModel)

    override fun cachedTranslation(
        content: String,
        accountViewModel: AccountViewModel,
    ): String? = cachedMlKitTranslation(content, accountViewModel)

    override val downloadsModels: Boolean = true

    override fun onLanguageBlocked(
        language: String,
        accountViewModel: AccountViewModel,
    ) = LanguageTranslatorService.onLanguageBlocked(
        language,
        accountViewModel.account.settings.syncedSettings.languages.translateTo.value,
    )
}

val FlavorTranslationPlatform: TranslationPlatform = MlKitTranslationPlatform

@Composable
private fun MlKitTranslatable(
    content: String,
    id: String,
    translationMessageModifier: Modifier,
    accountViewModel: AccountViewModel,
    displayText: @Composable (String) -> Unit,
) {
    val languages = accountViewModel.account.settings.syncedSettings.languages
    val translateTo by languages.translateTo.collectAsStateWithLifecycle()
    val dontTranslateFrom by languages.dontTranslateFrom.collectAsStateWithLifecycle()
    val modelDownloads by accountViewModel.settings.translationModelDownloads.collectAsStateWithLifecycle()
    val languagePreferences by languages.languagePreferences.collectAsStateWithLifecycle()

    val translatedTextState =
        remember(id, content, translateTo, dontTranslateFrom) {
            mutableStateOf(
                TranslationsCache.get(content, translateTo, dontTranslateFrom)
                    ?: TranslationConfig(content, null, null),
            )
        }

    LaunchedEffect(content, translateTo, dontTranslateFrom, modelDownloads) {
        try {
            // ML Kit's first-touch class init reads a Properties file out of the play-services AAR
            // via ZipFile/RandomAccessFile, which trips StrictMode if it runs on the UI dispatcher
            // that LaunchedEffect uses by default.
            translatedTextState.value =
                withContext(Dispatchers.IO) {
                    translateAndCache(content, translateTo, dontTranslateFrom, modelDownloads, languages)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Transient ML Kit / network failure, or a model download the settings don't allow
            // right now — keep showing the original. Do not cache: a one-off failure shouldn't
            // block future attempts on the same text.
        }
    }

    RenderTextWithTranslateOptions(
        translatedTextState = translatedTextState.value,
        content = content,
        languagePreferences = languagePreferences,
        translationMessageModifier = translationMessageModifier,
        accountViewModel = accountViewModel,
        displayText = displayText,
    )
}

/**
 * The translation of [content] under the current language settings, or [content] unchanged when
 * no translation applies. Same machinery and cache as [MlKitTranslatable] but without
 * the status bar, for callers that already render one and need a second string translated in
 * step with it — e.g. a NIP-84 highlight, which must translate the quoted passage alongside the
 * context in order to keep locating the passage inside it.
 */
@Composable
private fun rememberMlKitTranslation(
    content: String,
    accountViewModel: AccountViewModel,
): String {
    val languages = accountViewModel.account.settings.syncedSettings.languages
    val translateTo by languages.translateTo.collectAsStateWithLifecycle()
    val dontTranslateFrom by languages.dontTranslateFrom.collectAsStateWithLifecycle()
    val modelDownloads by accountViewModel.settings.translationModelDownloads.collectAsStateWithLifecycle()

    val state =
        remember(content, translateTo, dontTranslateFrom) {
            mutableStateOf(
                TranslationsCache.get(content, translateTo, dontTranslateFrom)
                    ?: TranslationConfig(content, null, null),
            )
        }

    LaunchedEffect(content, translateTo, dontTranslateFrom, modelDownloads) {
        try {
            state.value =
                withContext(Dispatchers.IO) {
                    translateAndCache(content, translateTo, dontTranslateFrom, modelDownloads, languages)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Transient ML Kit / network failure — keep the original, same as the viewer does.
        }
    }

    val config = state.value
    val translated = config.sourceLang != null && config.targetLang != null && config.sourceLang != config.targetLang
    return if (translated) config.result else content
}

@Composable
private fun RenderTextWithTranslateOptions(
    translatedTextState: TranslationConfig,
    content: String,
    languagePreferences: Map<String, String>,
    translationMessageModifier: Modifier = MaxWidthPaddingTop5dp,
    accountViewModel: AccountViewModel,
    displayText: @Composable (String) -> Unit,
) {
    val source = translatedTextState.sourceLang
    val target = translatedTextState.targetLang
    val translationOccurred = source != null && target != null && source != target

    val storedPreference = if (translationOccurred) languagePreferences["$source,$target"] else null
    var showOriginal by
        remember(translatedTextState, storedPreference) {
            mutableStateOf(storedPreference == source)
        }

    val toBeViewed = if (showOriginal || !translationOccurred) content else translatedTextState.result

    Column {
        displayText(toBeViewed)

        if (translationOccurred) {
            TranslationStatusBar(
                source = source,
                target = target,
                modifier = translationMessageModifier,
                accountViewModel = accountViewModel,
            ) { showOriginal = it }
        }
    }
}

/**
 * Returns the translation for [content] under the current language settings, hitting the cache
 * first and falling back to ML Kit. ML Kit's "no translation needed" cancellation (same language,
 * undetected, blocklisted) is bridged into a no-op [TranslationConfig] that is itself cached, so
 * the same text scrolling back into view doesn't re-run language identification. A model the
 * settings don't allow downloading yet throws instead, so it isn't cached.
 */
private suspend fun translateAndCache(
    content: String,
    translateTo: String,
    dontTranslateFrom: Set<String>,
    modelDownloads: ModelDownloads,
    languages: AccountLanguagePreferences,
): TranslationConfig {
    TranslationsCache.get(content, translateTo, dontTranslateFrom)?.let { return it }

    val noOp = TranslationConfig(content, null, null)
    val raw =
        try {
            LanguageTranslatorService.autoTranslate(content, dontTranslateFrom, translateTo, modelDownloads, languages).await()
        } catch (e: CancellationException) {
            // If our coroutine is the cancelled one, propagate; otherwise it's ML Kit signalling
            // "no translation needed" — cache the no-op and return it.
            coroutineContext.ensureActive()
            return noOp.also { TranslationsCache.set(content, translateTo, dontTranslateFrom, it) }
        }
    coroutineContext.ensureActive()

    val config = raw.toTranslationConfig(content) ?: noOp
    TranslationsCache.set(content, translateTo, dontTranslateFrom, config)
    return config
}

private fun ResultOrError.toTranslationConfig(content: String): TranslationConfig? {
    val translated = result ?: return null
    val source = sourceLang ?: return null
    val target = targetLang ?: return null
    if (source == target || translated == content) return null
    return TranslationConfig(translated, source, target)
}
