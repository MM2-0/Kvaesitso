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
package com.vitorpamplona.amethyst.navigation

import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.BottomBarCategories
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.NavBarCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The settings picker builds its grouped "Available" list from [BottomBarCategories], not from the raw
 * catalog. If a newly added [NavBarCatalog] destination isn't placed in a category, it would silently
 * vanish from the picker — this pins that every catalog id appears in exactly one category.
 */
class BottomBarCategoriesTest {
    @Test
    fun everyCatalogItemAppearsInExactlyOneCategory() {
        val categorized = BottomBarCategories.flatMap { it.items }

        // No duplicates across categories.
        assertEquals("an item is listed in more than one category", categorized.size, categorized.toSet().size)

        // Exact coverage of the catalog.
        assertEquals(NavBarCatalog.keys.toSet(), categorized.toSet())
    }
}
