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
package com.vitorpamplona.amethyst.commons.ui.navigation.host

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.vitorpamplona.amethyst.commons.model.navigation.NavBackStacks
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A back press in a dark theme over a light window: the pop fades (and on phones scales) both
 * screens, and what is under the host must not show through as a light flash.
 */
class NavigationHostBackgroundTest {
    private val ms = 1_000_000L

    @AfterTest
    fun resetTier() {
        NavTransitionTier.isLargeScreen = false
    }

    private fun Image.brightness(
        x: Int,
        y: Int,
    ): Int {
        val color = Bitmap.makeFromImage(this).getColor(x, y)
        return (((color shr 16) and 0xFF) + ((color shr 8) and 0xFF) + (color and 0xFF)) / 3
    }

    private fun brightestPopFrame(largeScreen: Boolean): Int {
        NavTransitionTier.isLargeScreen = largeScreen
        val nav = Nav(NavBackStacks(Route.Home), CoroutineScope(Dispatchers.Unconfined))
        val destinations =
            NavDestinations().apply {
                composableFromEnd<Route.Home> { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) }
                composableFromEnd<Route.Settings> { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) }
            }
        val owner =
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        val scene =
            ImageComposeScene(200, 200, Density(1f)) {
                // The window under the app: light, whatever theme the user picked.
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    AmethystPreviewTheme(dark = true) {
                        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                            NavigationHost(nav, destinations)
                        }
                    }
                }
            }
        try {
            var time = 0L

            fun frame() = scene.render(time).also { time += 16 * ms }

            repeat(3) { frame() }
            nav.stacks.push(Route.Settings)
            repeat(40) { frame() }
            nav.stacks.pop()
            // The brightest the corner and the centre get while the pop plays.
            var brightest = 0
            repeat(30) {
                val image = frame()
                brightest = maxOf(brightest, image.brightness(2, 2), image.brightness(100, 100))
            }
            return brightest
        } finally {
            scene.close()
            owner.viewModelStore.clear()
        }
    }

    @Test
    fun aPhonePopStaysDark() {
        val brightest = brightestPopFrame(largeScreen = false)
        assertTrue(brightest < 40, "the window's light background showed through the pop (brightness $brightest)")
    }

    @Test
    fun aLargeScreenPopStaysDark() {
        val brightest = brightestPopFrame(largeScreen = true)
        assertTrue(brightest < 40, "the window's light background showed through the pop (brightness $brightest)")
    }
}
