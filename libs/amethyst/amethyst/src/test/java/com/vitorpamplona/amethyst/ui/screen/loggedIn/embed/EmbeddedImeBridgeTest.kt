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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the host-side half of the page↔host IME contract: how [parseImeEvent] and
 * [parseSelectionGeometry] read the shim's `ime.*` envelopes, including the defaulting
 * behavior for absent/mistyped fields the kotlinx.serialization migration settled on
 * (total accessors — a field the shim never sent, or sent malformed, degrades to the
 * documented default instead of throwing). The page-side half (which envelopes real
 * browser gestures produce) lives in `tools/ime-test/shim-events.mjs`.
 */
class EmbeddedImeBridgeTest {
    private fun geom(raw: String) = parseSelectionGeometry(Json.parseToJsonElement(raw).jsonObject)

    // ---- malformed / unrecognized payloads ----

    @Test
    fun rejectsPayloadsThatAreNotJsonObjects() {
        assertNull(parseImeEvent("not json"))
        assertNull(parseImeEvent(""))
        assertNull(parseImeEvent("[1,2]"))
        assertNull(parseImeEvent("\"ime.blur\""))
        assertNull(parseImeEvent("42"))
        assertNull(parseImeEvent("null"))
    }

    @Test
    fun rejectsUnknownOrMissingType() {
        assertNull(parseImeEvent("""{"type":"ime.unknown"}"""))
        assertNull(parseImeEvent("""{"id":"1"}"""))
        assertNull(parseImeEvent("""{"type":7}""")) // coerces to "7", which matches nothing
    }

    // ---- payload-free events ----

    @Test
    fun parsesPayloadFreeEvents() {
        assertEquals(ImeEvent.WantKeyboard, parseImeEvent("""{"type":"ime.wantkb"}"""))
        assertEquals(ImeEvent.Blur, parseImeEvent("""{"type":"ime.blur"}"""))
    }

    // ---- focus / refocus ----

    @Test
    fun parsesFocusWithAllFields() {
        val event =
            parseImeEvent(
                """{"type":"ime.focus","inputType":"email","enterKeyHint":"send","multiline":true,
                   "readOnly":true,"hasNext":true,"text":"gm","selStart":1,"selEnd":2,
                   "geom":{"l":1,"t":2,"r":3,"b":4,"sx":5,"sb":6,"ex":7,"eb":8,"vw":360}}""",
            ) as ImeEvent.Focus
        assertEquals("email", event.inputType)
        assertEquals("send", event.enterKeyHint)
        assertTrue(event.multiline)
        assertTrue(event.readOnly)
        assertTrue(event.hasNext)
        assertEquals("gm", event.text)
        assertEquals(1, event.selStart)
        assertEquals(2, event.selEnd)
        assertEquals(360f, event.geometry!!.viewportWidth)
    }

    @Test
    fun focusDefaultsEveryAbsentField() {
        val event = parseImeEvent("""{"type":"ime.focus"}""") as ImeEvent.Focus
        assertEquals("text", event.inputType)
        assertEquals("", event.enterKeyHint)
        assertFalse(event.multiline)
        assertFalse(event.readOnly)
        assertFalse(event.hasNext)
        assertEquals("", event.text)
        assertEquals(0, event.selStart)
        assertEquals(0, event.selEnd)
        assertNull(event.geometry)
    }

    @Test
    fun refocusWrapsAFocus() {
        val event = parseImeEvent("""{"type":"ime.refocus","inputType":"url","text":"a"}""") as ImeEvent.ReFocus
        assertEquals("url", event.focus.inputType)
        assertEquals("a", event.focus.text)
    }

    // ---- state / pagesel / scroll / carettap ----

    @Test
    fun parsesState() {
        val event = parseImeEvent("""{"type":"ime.state","text":"abc","selStart":1,"selEnd":3}""") as ImeEvent.State
        assertEquals("abc", event.text)
        assertEquals(1, event.selStart)
        assertEquals(3, event.selEnd)
        assertNull(event.geometry)
    }

    @Test
    fun stateToleratesExplicitNullGeom() {
        val event = parseImeEvent("""{"type":"ime.state","text":"a","selStart":0,"selEnd":0,"geom":null}""") as ImeEvent.State
        assertNull(event.geometry)
    }

    @Test
    fun parsesPageSelection() {
        val event =
            parseImeEvent(
                """{"type":"ime.pagesel","active":true,"text":"copied",
                   "geom":{"l":0,"t":0,"r":10,"b":10,"sx":0,"sb":10,"ex":10,"eb":10,"vw":360}}""",
            ) as ImeEvent.PageSelection
        assertTrue(event.active)
        assertEquals("copied", event.text)
        assertEquals(10f, event.geometry!!.right)
    }

    @Test
    fun parsesScroll() {
        assertTrue((parseImeEvent("""{"type":"ime.scroll","active":true}""") as ImeEvent.Scroll).active)
        assertFalse((parseImeEvent("""{"type":"ime.scroll"}""") as ImeEvent.Scroll).active)
    }

    @Test
    fun parsesCaretTap() {
        val event =
            parseImeEvent(
                """{"type":"ime.carettap","geom":{"l":1,"t":2,"r":3,"b":4,"sx":1,"sb":4,"ex":3,"eb":4,"vw":360}}""",
            ) as ImeEvent.CaretTap
        assertEquals(1f, event.geometry!!.left)
        assertNull((parseImeEvent("""{"type":"ime.carettap"}""") as ImeEvent.CaretTap).geometry)
    }

    // ---- geometry ----

    @Test
    fun geometryNullForAbsentObject() {
        assertNull(parseSelectionGeometry(null))
    }

    @Test
    fun geometryDefaultsAbsentFieldsToZero() {
        val g = geom("""{}""")!!
        assertEquals(0f, g.left)
        assertEquals(0f, g.viewportWidth)
        assertFalse(g.isRange)
    }

    @Test
    fun geometryCaretFieldsAreNullOnlyWhenAbsent() {
        val without = geom("""{"l":1}""")!!
        assertNull(without.caretX)
        assertNull(without.caretTop)
        assertNull(without.caretBottom)

        val with = geom("""{"cx":10.5,"ct":20,"cb":30,"rng":true}""")!!
        assertEquals(10.5f, with.caretX)
        assertEquals(20f, with.caretTop)
        assertEquals(30f, with.caretBottom)
        assertTrue(with.isRange)
    }

    // ---- coercion behavior for mistyped fields (deliberate: total accessors, no throwing) ----

    @Test
    fun mistypedFieldsDegradeToDefaultsInsteadOfThrowing() {
        val event =
            parseImeEvent(
                // selStart as a numeric string parses; selEnd as a fraction does NOT truncate
                // (unlike org.json's optInt) — it falls back to 0; text as a number coerces to
                // its literal text; readOnly as the string "true" parses as a boolean.
                """{"type":"ime.focus","text":7,"selStart":"5","selEnd":5.7,"readOnly":"true","geom":[1,2]}""",
            ) as ImeEvent.Focus
        assertEquals("7", event.text)
        assertEquals(5, event.selStart)
        assertEquals(0, event.selEnd)
        assertTrue(event.readOnly)
        assertNull(event.geometry) // an array where an object belongs is treated as absent
    }

    // ---- host → page envelopes ----

    @Test
    fun resyncRequestSendsTheBareEnvelope() {
        var sent: String? = null
        val bridge =
            object : EmbeddedImeBridge {
                override var onImeEvent: ((ImeEvent) -> Unit)? = null

                override fun sendImeOp(json: String) {
                    sent = json
                }
            }
        bridge.requestImeResync()
        assertEquals("""{"type":"ime.resync"}""", sent)
    }
}
