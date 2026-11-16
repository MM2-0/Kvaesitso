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
package com.vitorpamplona.amethyst.commons.ui.note.platform

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** What the shared episode controls ask the platform's player to do. */
@Immutable
sealed interface EpisodeCommand {
    /** Moves to [millis], and starts playing when [play] is set. */
    data class Seek(
        val millis: Long,
        val play: Boolean,
    ) : EpisodeCommand

    data object Play : EpisodeCommand

    data object Pause : EpisodeCommand
}

/**
 * The line from the shared episode controls (skip, chapters, clips) to whichever player the
 * platform draws. The player collects [commands] while it is shown; a command sent before it does
 * waits for it.
 */
@Stable
class EpisodeControl {
    private val channel = Channel<EpisodeCommand>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commands: Flow<EpisodeCommand> = channel.receiveAsFlow()

    fun seek(
        millis: Long,
        play: Boolean = false,
    ) {
        channel.trySend(EpisodeCommand.Seek(millis.coerceAtLeast(0), play))
    }

    fun play() {
        channel.trySend(EpisodeCommand.Play)
    }

    fun pause() {
        channel.trySend(EpisodeCommand.Pause)
    }
}
