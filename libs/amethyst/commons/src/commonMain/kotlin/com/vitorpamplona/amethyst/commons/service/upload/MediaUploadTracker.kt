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
package com.vitorpamplona.amethyst.commons.service.upload

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * Whether a composer is uploading. The picked files go up as one batch ([startUpload] and
 * [finishUpload], which also resets it); side uploads such as a hand drawing run beside that batch
 * and are counted on their own ([startSideUpload], [finishSideUpload]), so a drawing that finishes
 * does not mark a still-running batch as done, nor the other way round. A side upload shows as an
 * image upload.
 */
@OptIn(ExperimentalAtomicApi::class)
class MediaUploadTracker {
    private var batchImage by mutableStateOf(false)
    private var batchFile by mutableStateOf(false)

    private val sideUploadCount = AtomicInt(0)
    private var sideUploading by mutableStateOf(false)

    val isUploadingImage: Boolean get() = batchImage || sideUploading
    val isUploadingFile: Boolean get() = batchFile

    val isUploading: Boolean get() = isUploadingImage || isUploadingFile

    fun startUpload(hasNonMedia: Boolean) {
        if (hasNonMedia) {
            batchFile = true
        } else {
            batchImage = true
        }
    }

    fun finishUpload() {
        batchImage = false
        batchFile = false
    }

    fun startSideUpload() {
        sideUploading = sideUploadCount.incrementAndFetch() > 0
    }

    fun finishSideUpload() {
        sideUploading = sideUploadCount.decrementAndFetch() > 0
    }
}
