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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.quartz.buzz.stream.CanvasEvent
import com.vitorpamplona.quartz.buzz.stream.tags.ExpectedRevisionTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndCollectResults
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Publishes a Buzz canvas (kind 40100) edit with the writer discipline Buzz's own clients use
 * (`set_canvas` in `desktop/src-tauri/src/commands/canvas.rs`):
 *
 * - the write asserts the head the editor was opened on with `["expected-revision", <head id>]`,
 *   or `none` when there was no canvas yet, so the relay refuses it (`conflict: …`) instead of
 *   silently overwriting someone else's newer revision;
 * - it is stamped `created_at = max(now, head.created_at + 1)` ([CanvasEvent.writeCreatedAt]) so
 *   it sorts strictly ahead of that head, and is refused outright when the head sits too far in
 *   the future to ratchet past.
 *
 * Unlike the fire-and-forget broadcaster, this waits for the relay's OK: a rejected write must
 * not be folded into [BuzzWorkspaceStates] as the new head, and the conflict has to reach the UI.
 */
object BuzzCanvasWriter {
    sealed interface Outcome {
        /** The relay stored the revision; it is now the local head too. */
        data class Saved(
            val event: CanvasEvent,
        ) : Outcome

        /** The canvas changed since the editor loaded it (the relay's `conflict:` rejection). */
        data class Conflict(
            val message: String,
        ) : Outcome

        /** The loaded head is timestamped too far in the future to write after; nothing was sent. */
        data object HeadTooFarInFuture : Outcome

        /** Any other rejection, or no answer at all. */
        data class Failed(
            val message: String,
        ) : Outcome
    }

    /**
     * Signs and publishes [markdown] as the new canvas of [channelId] on [relay], asserting the
     * head the editor started from ([headId] / [headCreatedAt], both null when there was none).
     */
    suspend fun save(
        account: Account,
        relay: NormalizedRelayUrl,
        channelId: String,
        markdown: String,
        headId: HexKey?,
        headCreatedAt: Long?,
        now: Long = TimeUtils.now(),
    ): Outcome {
        val createdAt = CanvasEvent.writeCreatedAt(headCreatedAt, now) ?: return Outcome.HeadTooFarInFuture
        val template = CanvasEvent.build(channelId, markdown, createdAt, expectedRevision = headId ?: ExpectedRevisionTag.NONE)
        val signed = account.signer.sign(template)

        var results = account.client.publishAndCollectResults(signed, setOf(relay))
        // A cold socket answers the first write with `auth-required` while our AUTH lands; the
        // relay does not replay it, so send once more on the now-authenticated connection.
        if (results.values.none { it.accepted } && results.values.any { it.message.contains("auth-required", ignoreCase = true) }) {
            results = account.client.publishAndCollectResults(signed, setOf(relay))
        }

        val outcome = classify(signed, results.values)
        if (outcome is Outcome.Saved) account.cache.justConsumeMyOwnEvent(signed)
        return outcome
    }

    /** Maps the relay's OK answers to an [Outcome]: any acceptance wins, then a `conflict:`, then the first reason. */
    fun classify(
        event: CanvasEvent,
        results: Collection<PublishResult>,
    ): Outcome {
        if (results.any { it.accepted }) return Outcome.Saved(event)
        results.firstOrNull { CanvasEvent.isConflict(it.message) }?.let { return Outcome.Conflict(it.message) }
        return Outcome.Failed(results.firstOrNull()?.message ?: PublishResult.NO_RESPONSE)
    }
}
