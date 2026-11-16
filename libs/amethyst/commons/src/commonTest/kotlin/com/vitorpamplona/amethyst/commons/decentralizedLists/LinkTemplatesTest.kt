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
package com.vitorpamplona.amethyst.commons.decentralizedLists

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Brainstorm's link-template rules (`lib/linkTemplates.ts`, conventions §5.3). */
class LinkTemplatesTest {
    private val templateId = "f19f39daf75388ff0f19cc37dde5ae1b90124b0d60e35659160c48dc690deca4"

    @Test
    fun aHeaderPinsATemplateAndBindsOnlyDeclaredFields() {
        val tags =
            arrayOf(
                arrayOf("required", "github-username"),
                arrayOf("link", templateId.uppercase(), "wss://dcosl.brainstorm.world", "username", "github-username", "other", "undeclared"),
                arrayOf("link", "not-an-id", "", "username", "github-username"),
            )

        val refs = tags.linkRefs(setOf("github-username"))

        assertEquals(1, refs.size)
        assertEquals(templateId, refs.single().templateId)
        assertEquals("wss://dcosl.brainstorm.world", refs.single().relay)
        assertEquals(listOf("username" to "github-username"), refs.single().bindings)
    }

    @Test
    fun onlyLevelOneTemplatesOnALiteralHttpsHost() {
        assertEquals(listOf("username"), templatePlaceholders("https://github.com/{username}"))
        assertEquals(listOf("a", "b"), templatePlaceholders("https://x.org/{a}/{b}/{a}"))
        assertNull(templatePlaceholders("http://github.com/{username}"))
        assertNull(templatePlaceholders("https://{host}/x"))
        assertNull(templatePlaceholders("https://x.org/{+path}"))
        assertNull(templatePlaceholders("https://x.org/{?q}"))
        assertNull(templatePlaceholders("https://x.org/{a,b}"))
        assertNull(templatePlaceholders("https://x.org/{a"))
    }

    @Test
    fun valuesArePercentEncodedSoTheyCannotLeaveThePath() {
        assertEquals("https://github.com/..%2Fevil%3Fx%23y", expandTemplate("https://github.com/{u}", mapOf("u" to "../evil?x#y")))
        assertEquals("https://github.com/caf%C3%A9%20bar", expandTemplate("https://github.com/{u}", mapOf("u" to " café bar ")))
        assertNull(expandTemplate("https://github.com/{u}", mapOf("u" to " ")))
        assertNull(expandTemplate("https://github.com/{u}", emptyMap()))
    }

    @Test
    fun anItemsLinksComeFromTheTemplatesItsHeaderNames() {
        val refs = listOf(LinkRef(templateId, null, listOf("username" to "github-username")))
        val template = urlTemplateOf(templateId, arrayOf(arrayOf("url-template", "https://github.com/{username}"), arrayOf("name", "GitHub profile")))!!
        val item = arrayOf(arrayOf("github-username", "vcavallo"))

        val links = itemLinks(item, refs + refs, mapOf(templateId to template))

        assertEquals(listOf(DListLink("GitHub profile", "https://github.com/vcavallo", "github.com")), links)
        // A template not fetched yet, or an item that leaves the field empty, gives no link.
        assertTrue(itemLinks(item, refs, emptyMap()).isEmpty())
        assertTrue(itemLinks(arrayOf(arrayOf("description", "x")), refs, mapOf(templateId to template)).isEmpty())
    }

    /** A host with userinfo or a backslash would read as one host and open another. */
    @Test
    fun templatesWhoseHostHidesAnotherAreRefused() {
        assertNull(templatePlaceholders("https://github.com@evil.com/{u}"))
        assertNull(templatePlaceholders("https://evil.com\\x.github.com/{u}"))
        assertEquals(listOf("u"), templatePlaceholders("https://github.com:443/{u}"))
    }

    /** `.` and `..` pass percent-encoding untouched and would climb the template's path. */
    @Test
    fun dotSegmentsAreNoValue() {
        assertNull(expandTemplate("https://github.com/{u}", mapOf("u" to "..")))
        assertNull(expandTemplate("https://github.com/{u}", mapOf("u" to ".")))
        assertEquals("https://github.com/a.b", expandTemplate("https://github.com/{u}", mapOf("u" to "a.b")))
    }
}
