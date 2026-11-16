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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import com.vitorpamplona.amethyst.commons.wot.network.TrustVerdict
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** Renders every verdict the profile badge can show; set WOT_RENDER_DIR to keep the PNGs. */
class ProfileTrustBadgeRenderTest {
    @Test
    fun everyVerdictRendersInBothThemes() {
        listOf(false, true).forEach { dark ->
            val scene =
                ImageComposeScene(width = 820, height = 520, density = Density(2f)) {
                    AmethystPreviewTheme(dark = dark) {
                        Column(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TrustVerdictBadge(TrustVerdict.TRUSTED, rank = 42, onClick = {})
                            TrustVerdictBadge(TrustVerdict.FOLLOW, rank = null, onClick = {})
                            TrustVerdictBadge(TrustVerdict.BELOW_MIN_SCORE, rank = 2, onClick = {})
                            TrustVerdictBadge(TrustVerdict.NOT_IN_NETWORK, rank = null, onClick = {})
                            // These draw nothing.
                            TrustVerdictBadge(TrustVerdict.SELF, rank = 90, onClick = {})
                            TrustVerdictBadge(TrustVerdict.NO_NETWORK, rank = null, onClick = {})
                        }
                    }
                }
            try {
                val png = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
                System.getenv("WOT_RENDER_DIR")?.let { File(it, "profile-trust-${if (dark) "dark" else "light"}.png").writeBytes(png) }
            } finally {
                scene.close()
            }
        }
    }
}
