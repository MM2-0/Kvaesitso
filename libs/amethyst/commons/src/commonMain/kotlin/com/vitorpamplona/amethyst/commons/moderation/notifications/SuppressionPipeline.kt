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
package com.vitorpamplona.amethyst.commons.moderation.notifications

import androidx.compose.runtime.Immutable

/** Platform-neutral context passed through the suppression pipeline. */
@Immutable
data class ToastEventCtx(
    val kind: NotifKind,
    val eventId: String,
    val authorPubKey: String,
    val targetId: String?,
    val createdAtSec: Long,
    val signatureValid: Boolean,
    val zapAmountSats: Long? = null,
)

/**
 * Decides whether an incoming event should trigger an OS notification.
 * Checks are ordered so security filters run before any string formatting
 * or logging, and cheapest checks short-circuit first.
 *
 * Not thread-safe by design — call from a single collector coroutine.
 */
class SuppressionPipeline(
    private val settings: NotificationSettings,
    private val ownPubKey: () -> String?,
    private val isMuted: (String) -> Boolean,
    private val sessionStartSec: Long,
    private val isWindowFocused: () -> Boolean,
    private val nowSec: () -> Long,
    private val coldBootFreshnessSec: Long = 30,
    private val dedupeWindowSec: Long = 30,
    private val dedupeMemorySec: Long = 300,
) {
    private val recentToasts = HashMap<String, Long>()

    fun shouldToast(ctx: ToastEventCtx): Boolean {
        // Security first — never format / log muted or unsigned content.
        if (!ctx.signatureValid) return false
        if (isMuted(ctx.authorPubKey)) return false

        if (!settings.enabled.value) return false
        if (!settings.kinds.value.enabledFor(ctx.kind)) return false

        val now = nowSec()
        if (settings.dnd.value.isActive(now)) return false
        if (isWindowFocused()) return false
        if (isColdBoot(ctx, now)) return false
        if (ctx.authorPubKey == ownPubKey()) return false
        if (isDuplicate(ctx, now)) return false

        return true
    }

    private fun isColdBoot(
        ctx: ToastEventCtx,
        now: Long,
    ): Boolean {
        val stale = (now - ctx.createdAtSec) > coldBootFreshnessSec
        val preSession = ctx.createdAtSec < sessionStartSec
        return stale || preSession
    }

    private fun isDuplicate(
        ctx: ToastEventCtx,
        now: Long,
    ): Boolean {
        val key = ctx.kind.name + "|" + (ctx.targetId ?: ctx.eventId)
        // Bound the map — drop entries older than the memory window.
        val cutoff = now - dedupeMemorySec
        recentToasts.entries.removeAll { it.value < cutoff }
        val last = recentToasts[key]
        if (last != null && (now - last) < dedupeWindowSec) return true
        recentToasts[key] = now
        return false
    }
}
