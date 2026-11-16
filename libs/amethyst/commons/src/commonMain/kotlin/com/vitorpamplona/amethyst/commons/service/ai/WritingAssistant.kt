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
package com.vitorpamplona.amethyst.commons.service.ai

import androidx.compose.runtime.Immutable

interface WritingAssistant {
    suspend fun checkAvailability(): WritingAssistantStatus

    /**
     * Asks the platform to fetch the on-device model when [checkAvailability] reported
     * [WritingAssistantStatus.Downloadable]. Returns the status after the attempt.
     *
     * Implementations must be safe to call repeatedly: only the first call per instance
     * starts a download, later ones just report the current status.
     */
    suspend fun requestDownload(): WritingAssistantStatus

    suspend fun transform(
        text: String,
        tone: WritingTone,
    ): WritingResult

    fun close()
}

enum class WritingTone {
    CORRECT,
    REPHRASE,
    SHORTER,
    ELABORATE,
    FRIENDLY,
    PROFESSIONAL,
    EMOJIFY,
}

sealed class WritingAssistantStatus {
    data object Available : WritingAssistantStatus()

    data object Unavailable : WritingAssistantStatus()

    /** The device supports the model but it has not been fetched yet. */
    data object Downloadable : WritingAssistantStatus()

    data object Downloading : WritingAssistantStatus()
}

@Immutable
data class WritingResult(
    val originalText: String,
    val transformedText: String,
    val tone: WritingTone,
)
