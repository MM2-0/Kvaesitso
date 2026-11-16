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
import kotlinx.coroutines.flow.StateFlow

enum class NotifKind {
    ZAP,
    DM,
    REPLY,
    MENTION,
    REPOST,
    REACTION,
    FOLLOW,

    /** A calendar reminder. Its switch is in the calendar settings, not among these toggles. */
    CALENDAR,

    /** The reader's front page is ready, or needs them. Its switch is the schedule itself. */
    FRONT_PAGE,
}

@Immutable
data class KindToggles(
    val zap: Boolean = true,
    // DM defaults OFF until NIP-17 gift-wrap decryption is wired.
    val dm: Boolean = false,
    val reply: Boolean = true,
    val mention: Boolean = true,
    val repost: Boolean = false,
    val reaction: Boolean = false,
    val follow: Boolean = false,
) {
    fun enabledFor(kind: NotifKind): Boolean =
        when (kind) {
            NotifKind.ZAP -> zap
            NotifKind.DM -> dm
            NotifKind.REPLY -> reply
            NotifKind.MENTION -> mention
            NotifKind.REPOST -> repost
            NotifKind.REACTION -> reaction
            NotifKind.FOLLOW -> follow
            NotifKind.CALENDAR -> true
            NotifKind.FRONT_PAGE -> true
        }

    fun with(
        kind: NotifKind,
        v: Boolean,
    ): KindToggles =
        when (kind) {
            NotifKind.ZAP -> copy(zap = v)
            NotifKind.DM -> copy(dm = v)
            NotifKind.REPLY -> copy(reply = v)
            NotifKind.MENTION -> copy(mention = v)
            NotifKind.REPOST -> copy(repost = v)
            NotifKind.REACTION -> copy(reaction = v)
            NotifKind.FOLLOW -> copy(follow = v)
            NotifKind.CALENDAR -> this
            NotifKind.FRONT_PAGE -> this
        }
}

@Immutable
data class DndState(
    val manualUntilEpochSec: Long? = null,
) {
    fun isActive(nowEpochSec: Long): Boolean = manualUntilEpochSec != null && nowEpochSec < manualUntilEpochSec
}

/**
 * Read-only view of notification settings. Concrete implementations live in
 * platform source sets (JVM uses java.util.prefs).
 */
interface NotificationSettings {
    val enabled: StateFlow<Boolean>
    val kinds: StateFlow<KindToggles>
    val dnd: StateFlow<DndState>
    val previewInToast: StateFlow<Boolean>

    fun setEnabled(v: Boolean)

    /**
     * True iff the user has taken an explicit action to disable
     * notifications (i.e. flipped the master switch OFF at some point).
     * Used by the Settings screen to distinguish "master switch is off
     * because it defaults to off on first launch" from "master switch
     * is off because the user asked for it to be off". Only the former
     * gets auto-enabled when the OS permission check passes.
     */
    fun wasExplicitlyDisabled(): Boolean

    fun setKindToggle(
        kind: NotifKind,
        v: Boolean,
    )

    fun setManualDndUntil(epochSec: Long?)

    fun setPreviewInToast(v: Boolean)
}
