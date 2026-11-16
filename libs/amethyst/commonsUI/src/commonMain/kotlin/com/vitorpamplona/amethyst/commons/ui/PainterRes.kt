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
package com.vitorpamplona.amethyst.commons.ui

import androidx.collection.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// Caches the few bitmaps and vectors every feed row draws, so a scroll does not reload them.
private val iconCache = LruCache<DrawableResource, LruCache<Int, Painter>>(30)

/**
 * The Compose-resources twin of the Android app's `painterRes`: [painterResource] behind a cache
 * keyed by resource and [sizeReference]. A cached painter can be the only copy on the screen, so
 * callers drawing it at different sizes pass different references.
 */
@Composable
fun painterRes(
    resource: DrawableResource,
    sizeReference: Int,
): Painter {
    val bySize = iconCache[resource]
    bySize?.get(sizeReference)?.let { return it }

    val loaded = painterResource(resource)

    val sizes = bySize ?: LruCache<Int, Painter>(10).also { iconCache.put(resource, it) }
    sizes.put(sizeReference, loaded)

    return loaded
}
