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
package com.vitorpamplona.amethyst.commons.nip64Chess.dal

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** The game start note [this] Jester note belongs to: itself for a start, the note it replies to for a move. */
fun Note.chessGameStartNote(): Note? {
    val jester = event as? JesterEvent ?: return null
    return when {
        jester.isStartEvent() -> this
        jester.isMoveEvent() -> jester.startEventId()?.let { LocalCache.getNoteIfExists(it) }
        else -> null
    }
}

/** True when [this] is a loaded Jester start event. */
fun Note.isChessGameStart(): Boolean = (event as? JesterEvent)?.isStartEvent() == true

/** The last time anyone touched this game: its newest move, or the challenge itself. */
fun Note.chessLastActivity(): Long = replies.maxOfOrNull { it.createdAt() ?: 0L } ?: createdAt() ?: 0L

/** Newest activity first, so the games being played float to the top. */
fun Collection<Note>.sortedByChessActivity(): List<Note> = sortedWith(compareByDescending<Note> { it.chessLastActivity() }.thenBy { it.idHex })

/** Whether [pubkey] started, was challenged to (by `p` or by jesterui's private reference), or moved in this game. */
fun Note.isChessGameOf(pubkey: String): Boolean {
    val start = event as? JesterEvent ?: return false
    return start.pubKey == pubkey ||
        pubkey in start.opponentKeys() ||
        JesterProtocol.privateStartRef(pubkey) in start.eTags() ||
        replies.any { it.author?.pubkeyHex == pubkey }
}

/**
 * The start ids of the user's games whose challenge is not in the cache: moves by the user, or
 * naming them, arrived, but the challenge they answer did not. That happens when the challenger is
 * on no list the screen asked for (an open seat taken from Global, then the top bar changed to
 * Mine). The lobby fetches these by id. Emits the current set on collection, then on every change.
 */
fun chessGamesMissingTheirStart(me: HexKey): Flow<Set<HexKey>> =
    flow {
        val missing = HashSet<HexKey>()
        LocalCache.notes.forEach { _, note -> note.unloadedChessStartOf(me)?.let(missing::add) }
        emit(missing.toSet())
        LocalCache.getEventStream().newEventBundles.collect { bundle ->
            var changed = false
            bundle.forEach { note ->
                if (missing.remove(note.idHex)) changed = true
                note.unloadedChessStartOf(me)?.let { if (missing.add(it)) changed = true }
            }
            if (changed) emit(missing.toSet())
        }
    }.flowOn(Dispatchers.Default)

private fun Note.unloadedChessStartOf(me: HexKey): HexKey? {
    val move = event as? JesterEvent ?: return null
    if (!move.isMoveEvent() || (move.pubKey != me && me !in move.opponentKeys())) return null
    val startId = move.startEventId() ?: return null
    return startId.takeIf { LocalCache.getNoteIfExists(it)?.event == null }
}
