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
package com.vitorpamplona.amethyst.service.playback.composable.wavefront

import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

suspend fun Player.completionRatio() = withContext(Dispatchers.Main) { currentPosition / duration.toFloat() }

suspend fun Player.positionDuration() = withContext(Dispatchers.Main) { PositionDuration(currentPosition, duration) }

class PositionDuration(
    val position: Long,
    val duration: Long,
) {
    fun finished() = position > duration

    fun ratio() = position / (duration.toFloat())
}

fun Player.pollCurrentRelativePositionFlow() =
    flow {
        do {
            delay(100)
            val ratio = positionDuration()
            emit(ratio.ratio())
        } while (!ratio.finished())
    }.onStart {
        emit(completionRatio())
    }.flowOn(Dispatchers.IO)
        .conflate()

fun Player.pollCurrentPositionFlow() =
    flow {
        do {
            delay(100)
            val ratio = positionDuration()
            emit(ratio.position)
        } while (!ratio.finished())
    }.onStart {
        emit(withContext(Dispatchers.Main) { currentPosition })
    }.flowOn(Dispatchers.IO)
        .conflate()
