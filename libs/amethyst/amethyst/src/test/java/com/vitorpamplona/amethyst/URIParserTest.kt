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
package com.vitorpamplona.amethyst

import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.uri.connectedAppRoute
import com.vitorpamplona.amethyst.commons.model.navigation.uri.isConnectedAppRoute
import com.vitorpamplona.amethyst.commons.model.navigation.uri.urlRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

class URIParserTest {
    @Test
    fun parsesEncodedUrlRoutes() {
        val url = "HTTPS://Example.com/path?b=2&a=1#section"
        val route = urlRoute("nostr:url?id=${URLEncoder.encode(url, Charsets.UTF_8.name())}")

        assertEquals(Route.Url("https://example.com/path?b=2&a=1"), route)
    }

    @Test
    fun ignoresMalformedEncodedUrlRoutes() {
        assertNull(urlRoute("nostr:url?id=%"))
    }

    @Test
    fun parsesNappletConnectedAppRoute() {
        val coordinate = "abc123:my-app"
        val route = connectedAppRoute("nostr:connectedapp?coordinate=${URLEncoder.encode(coordinate, Charsets.UTF_8.name())}")

        assertEquals(Route.ConnectedAppDetail(coordinate), route)
    }

    @Test
    fun parsesBrowserConnectedAppRoute() {
        val coordinate = "browser:https://example.com"
        val route = connectedAppRoute("nostr:connectedapp?coordinate=${URLEncoder.encode(coordinate, Charsets.UTF_8.name())}")

        assertEquals(Route.ConnectedAppDetail(coordinate), route)
    }

    @Test
    fun ignoresBlankConnectedAppCoordinate() {
        assertNull(connectedAppRoute("nostr:connectedapp?coordinate="))
    }

    @Test
    fun recognizesConnectedAppRoutePrefixes() {
        assertTrue(isConnectedAppRoute("connectedapp?coordinate=abc"))
        assertTrue(isConnectedAppRoute("nostr:connectedapp?coordinate=abc"))
        assertFalse(isConnectedAppRoute("nostr:url?id=abc"))
    }
}
