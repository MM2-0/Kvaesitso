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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.prefs.Preferences

/**
 * Pins the semantics of the new [NotificationSettings.wasExplicitlyDisabled]
 * flag added to unblock the "Enable OS notifications button doesn't work on
 * desktop" bug's second failure mode.
 *
 * The Settings screen auto-enables the master notifications switch when it
 * detects that the OS permission is fine and the switch is off. That's the
 * common path for users who click the "Enable OS notifications" button and
 * expect it to fully take effect (button label promise). But it MUST NOT
 * override users who deliberately turned notifications off. The
 * [wasExplicitlyDisabled] flag is how we distinguish the two.
 */
class PreferencesNotificationSettingsExplicitDisableTest {
    private fun freshNode(): Preferences {
        // Use a UUID-scoped node so tests never share state and never
        // pollute the real user prefs on the machine running CI/dev builds.
        return Preferences.userRoot().node("amethyst-test-" + UUID.randomUUID())
    }

    @Test
    fun `fresh install defaults to not-explicitly-disabled`() {
        val settings = PreferencesNotificationSettings(freshNode())
        assertFalse(
            "First launch must not look like a deliberate opt-out; otherwise auto-enable stays off forever",
            settings.wasExplicitlyDisabled(),
        )
    }

    @Test
    fun `turning off marks explicitly disabled`() {
        val prefs = freshNode()
        val settings = PreferencesNotificationSettings(prefs)
        settings.setEnabled(false)
        assertTrue(settings.wasExplicitlyDisabled())
    }

    @Test
    fun `turning on clears the explicit-disable flag`() {
        val prefs = freshNode()
        val settings = PreferencesNotificationSettings(prefs)
        settings.setEnabled(false)
        assertTrue(settings.wasExplicitlyDisabled())
        settings.setEnabled(true)
        assertFalse(
            "Toggling back on must clear the flag so subsequent OFF->auto-enable cycles work",
            settings.wasExplicitlyDisabled(),
        )
    }

    @Test
    fun `flag persists across new instances on the same prefs node`() {
        val prefs = freshNode()
        PreferencesNotificationSettings(prefs).setEnabled(false)
        // Second instance opens the same node \u2014 flag must survive process restart.
        val reopened = PreferencesNotificationSettings(prefs)
        assertTrue(reopened.wasExplicitlyDisabled())
    }
}
