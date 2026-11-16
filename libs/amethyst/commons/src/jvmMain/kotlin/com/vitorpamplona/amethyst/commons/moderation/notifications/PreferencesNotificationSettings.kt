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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

/**
 * java.util.prefs-backed [NotificationSettings]. Node path is stable across
 * `amy` CLI and the desktop app so both see the same toggles.
 */
class PreferencesNotificationSettings(
    private val prefs: Preferences = Preferences.userRoot().node(NODE),
) : NotificationSettings {
    // Default OFF so first-time users see the "Set up" banner. They opt in
    // explicitly, which is important because notification delivery has real
    // platform quirks (macOS bundle-signing, DE-specific behavior on Linux).
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    private val _kinds = MutableStateFlow(loadKinds())
    private val _dnd =
        MutableStateFlow(
            DndState(
                manualUntilEpochSec = prefs.getLong(KEY_DND_UNTIL, 0L).takeIf { it > 0 },
            ),
        )
    private val _previewInToast = MutableStateFlow(prefs.getBoolean(KEY_PREVIEW, false))

    override val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    override val kinds: StateFlow<KindToggles> = _kinds.asStateFlow()
    override val dnd: StateFlow<DndState> = _dnd.asStateFlow()
    override val previewInToast: StateFlow<Boolean> = _previewInToast.asStateFlow()

    override fun setEnabled(v: Boolean) {
        _enabled.value = v
        prefs.putBoolean(KEY_ENABLED, v)
        // Track explicit user intent so the Settings screen can auto-enable
        // on next visit for users who never touched the switch, while
        // respecting users who deliberately turned it off. Only false
        // → "explicit disable"; going from off to on clears the flag so
        // subsequent auto-enable heuristics work normally.
        if (v) {
            prefs.remove(KEY_EXPLICITLY_DISABLED)
        } else {
            prefs.putBoolean(KEY_EXPLICITLY_DISABLED, true)
        }
    }

    override fun wasExplicitlyDisabled(): Boolean = prefs.getBoolean(KEY_EXPLICITLY_DISABLED, false)

    override fun setKindToggle(
        kind: NotifKind,
        v: Boolean,
    ) {
        val next = _kinds.value.with(kind, v)
        _kinds.value = next
        prefs.putBoolean(keyForKind(kind), v)
    }

    override fun setManualDndUntil(epochSec: Long?) {
        _dnd.value = DndState(epochSec)
        if (epochSec == null || epochSec <= 0) {
            prefs.remove(KEY_DND_UNTIL)
        } else {
            prefs.putLong(KEY_DND_UNTIL, epochSec)
        }
    }

    override fun setPreviewInToast(v: Boolean) {
        _previewInToast.value = v
        prefs.putBoolean(KEY_PREVIEW, v)
    }

    private fun loadKinds(): KindToggles =
        KindToggles(
            zap = prefs.getBoolean(keyForKind(NotifKind.ZAP), true),
            dm = prefs.getBoolean(keyForKind(NotifKind.DM), false),
            reply = prefs.getBoolean(keyForKind(NotifKind.REPLY), true),
            mention = prefs.getBoolean(keyForKind(NotifKind.MENTION), true),
            repost = prefs.getBoolean(keyForKind(NotifKind.REPOST), false),
            reaction = prefs.getBoolean(keyForKind(NotifKind.REACTION), false),
            follow = prefs.getBoolean(keyForKind(NotifKind.FOLLOW), false),
        )

    companion object {
        const val NODE = "com/vitorpamplona/amethyst/notifications"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_EXPLICITLY_DISABLED = "explicitly_disabled"
        private const val KEY_DND_UNTIL = "dnd_until"
        private const val KEY_PREVIEW = "preview_in_toast"

        private fun keyForKind(kind: NotifKind): String = "kind_" + kind.name.lowercase()
    }
}

/** Wall-clock seconds. JVM-only until KMP targets need cross-platform time. */
fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000
