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
package com.vitorpamplona.amethyst.napplethost

import android.view.KeyEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * The host-side registry of keyboard/command actions a napplet bound via `napplet.keys.registerAction`.
 * Each action maps an id to a [Binding] (e.g. `"Ctrl+S"`); when a matching hardware-key combo reaches
 * the host activity, [actionFor] returns the action id so the host can push a `keys.action` to the
 * applet. The applet never receives raw key events — only the named action it registered fires.
 *
 * On a touch-only device with no hardware keyboard these simply never match; the binding is honored
 * the moment a keyboard is present.
 */
class NappletKeyActions {
    private val actions = ConcurrentHashMap<String, Binding>()

    /** Registers [actionId] under [binding] (e.g. `"Ctrl+S"`). A null/unparseable binding is ignored. */
    fun register(
        actionId: String,
        binding: String?,
    ) {
        Binding.parse(binding)?.let { actions[actionId] = it }
    }

    fun unregister(actionId: String) {
        actions.remove(actionId)
    }

    fun clear() {
        actions.clear()
    }

    /** The action id bound to the combo in [event] (a key-down), or null if none matches. */
    fun actionFor(event: KeyEvent): String? {
        if (event.action != KeyEvent.ACTION_DOWN) return null
        val pressed = Binding.of(event) ?: return null
        return actions.entries.firstOrNull { it.value == pressed }?.key
    }

    /** A normalized key combo: the modifier flags plus a single key code. */
    data class Binding(
        val ctrl: Boolean,
        val shift: Boolean,
        val alt: Boolean,
        val meta: Boolean,
        val keyCode: Int,
    ) {
        companion object {
            /** Parses a combo string like `"Ctrl+Shift+K"` / `"Cmd+Enter"`; null if it has no usable key. */
            fun parse(binding: String?): Binding? {
                val tokens = binding?.split('+')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return null
                if (tokens.isEmpty()) return null
                var ctrl = false
                var shift = false
                var alt = false
                var meta = false
                var keyCode = KeyEvent.KEYCODE_UNKNOWN
                for (token in tokens) {
                    when (token.lowercase()) {
                        "ctrl", "control" -> ctrl = true
                        "shift" -> shift = true
                        "alt", "option", "opt" -> alt = true
                        "cmd", "command", "meta", "super", "win" -> meta = true
                        else -> keyCode = keyCodeOf(token) ?: return null
                    }
                }
                if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return null
                return Binding(ctrl, shift, alt, meta, keyCode)
            }

            /** The combo a key-down [event] represents. */
            fun of(event: KeyEvent): Binding? {
                if (event.keyCode == KeyEvent.KEYCODE_UNKNOWN) return null
                return Binding(
                    ctrl = event.isCtrlPressed,
                    shift = event.isShiftPressed,
                    alt = event.isAltPressed,
                    meta = event.isMetaPressed,
                    keyCode = event.keyCode,
                )
            }

            /** Maps a single key token (a letter/digit or a named key) to an Android key code. */
            private fun keyCodeOf(token: String): Int? {
                if (token.length == 1) {
                    val c = token[0].uppercaseChar()
                    return when (c) {
                        in 'A'..'Z' -> KeyEvent.KEYCODE_A + (c - 'A')
                        in '0'..'9' -> KeyEvent.KEYCODE_0 + (c - '0')
                        else -> null
                    }
                }
                return when (token.lowercase()) {
                    "enter", "return" -> KeyEvent.KEYCODE_ENTER
                    "space", "spacebar" -> KeyEvent.KEYCODE_SPACE
                    "tab" -> KeyEvent.KEYCODE_TAB
                    "esc", "escape" -> KeyEvent.KEYCODE_ESCAPE
                    "up", "arrowup" -> KeyEvent.KEYCODE_DPAD_UP
                    "down", "arrowdown" -> KeyEvent.KEYCODE_DPAD_DOWN
                    "left", "arrowleft" -> KeyEvent.KEYCODE_DPAD_LEFT
                    "right", "arrowright" -> KeyEvent.KEYCODE_DPAD_RIGHT
                    "delete", "del" -> KeyEvent.KEYCODE_FORWARD_DEL
                    "backspace" -> KeyEvent.KEYCODE_DEL
                    "comma" -> KeyEvent.KEYCODE_COMMA
                    "period", "dot" -> KeyEvent.KEYCODE_PERIOD
                    "slash" -> KeyEvent.KEYCODE_SLASH
                    else -> token.removePrefix("F").toIntOrNull()?.let { f -> if (f in 1..12) KeyEvent.KEYCODE_F1 + (f - 1) else null }
                }
            }
        }
    }
}
