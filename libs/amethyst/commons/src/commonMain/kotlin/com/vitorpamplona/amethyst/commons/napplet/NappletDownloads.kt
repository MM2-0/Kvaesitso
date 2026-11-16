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
package com.vitorpamplona.amethyst.commons.napplet

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * How far the download a launch waits on has come: [bytes] of [total], when the server said how many.
 * A webxdc archive is one file of up to hundreds of megabytes, fetched before anything can show.
 */
data class NappletDownloadProgress(
    val bytes: Long,
    val total: Long?,
) {
    /** Done so far, from 0 to 1; null when the size isn't known. */
    val fraction: Float? get() = total?.takeIf { it > 0 }?.let { (bytes.toFloat() / it).coerceIn(0f, 1f) }

    /** "2.4 MB / 5.8 MB", or "2.4 MB" without a total: numbers only, so no language is needed. */
    fun label(): String = total?.takeIf { it > 0 }?.let { "${size(bytes)} / ${size(it)}" } ?: size(bytes)

    private fun size(count: Long): String {
        val (unit, name) =
            when {
                count >= 1_000_000_000 -> 1_000_000_000L to "GB"
                count >= 1_000_000 -> 1_000_000L to "MB"
                count >= 1_000 -> 1_000L to "KB"
                else -> return "$count B"
            }
        val tenths = count * 10 / unit
        return if (tenths >= 100) "${tenths / 10} $name" else "${tenths / 10}.${tenths % 10} $name"
    }
}

/**
 * The downloads of the apps running inside notes, by the instance id the launcher gave each run:
 * the platform's surface reports them, and the note's stage draws the bar.
 */
class NappletInlineDownloads {
    private val downloads = MutableStateFlow<Map<String, NappletDownloadProgress>>(emptyMap())

    /** Where the run [instanceId]'s download is; null when none is running. */
    fun report(
        instanceId: String,
        progress: NappletDownloadProgress?,
    ) = downloads.update { if (progress == null) it - instanceId else it + (instanceId to progress) }

    fun progress(instanceId: String): Flow<NappletDownloadProgress?> = downloads.map { it[instanceId] }.distinctUntilChanged()

    /** The inline run [instanceId] ended. */
    fun forget(instanceId: String) = report(instanceId, null)

    companion object {
        /** The app's one record: the platforms' surfaces and the notes share it. */
        val shared = NappletInlineDownloads()
    }
}
