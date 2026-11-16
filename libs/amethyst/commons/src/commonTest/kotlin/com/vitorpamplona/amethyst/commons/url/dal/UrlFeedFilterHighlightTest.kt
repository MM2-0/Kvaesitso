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
package com.vitorpamplona.amethyst.commons.url.dal

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlFeedFilterHighlightTest {
    private val scope = "https://medium.com/feenk/rewilding-software-engineering-083b86c81c43"

    @Test
    fun matchesTheSamePageSpelledDifferently() {
        assertTrue(UrlFeedFilter.isSamePage(scope, scope))
        assertTrue(UrlFeedFilter.isSamePage("$scope/", scope))
        assertTrue(UrlFeedFilter.isSamePage("HTTPS://Medium.com/feenk/rewilding-software-engineering-083b86c81c43", scope))
        assertTrue(UrlFeedFilter.isSamePage("$scope?utm_source=twitter&fbclid=abc", scope))
        assertTrue(UrlFeedFilter.isSamePage("$scope#:~:text=When%20you%20find", scope))
    }

    @Test
    fun rejectsOtherPages() {
        assertFalse(UrlFeedFilter.isSamePage("https://medium.com/feenk/another-post-1234abcd5678", scope))
        assertFalse(UrlFeedFilter.isSamePage("$scope?page=2", scope))
        assertFalse(UrlFeedFilter.isSamePage("not a url", scope))
    }
}
