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
package com.vitorpamplona.amethyst.service.playback.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.error_video_open_in_browser
import com.vitorpamplona.amethyst.commons.resources.error_video_playback_failed
import com.vitorpamplona.amethyst.commons.resources.error_video_playback_failed_description
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The "open in browser" fallback is the only thing this overlay offers the user, so it has to
 * survive whatever box the media layout hands it.
 *
 * [Column] measures children in declaration order against the remaining height, so the button —
 * being last — is what starved when the content was taller than the box. A note in the feed is
 * inset under the 55dp author column (screenWidth - 89dp), and with no imeta `dim` the media box
 * is 16:9, which on a 411dp phone is 322dp wide and only 181dp tall. That was enough to squeeze
 * the button down to 0.38dp — present in the tree, invisible and untappable on screen — while the
 * same note opened in the thread (full bleed, screenWidth - 26dp, so a 217dp box) rendered it at
 * 35.8dp.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackErrorOverlayFitTest {
    @get:Rule
    val rule = createComposeRule()

    /**
     * Built outside composition on purpose: the mock and its error state are fixtures for the whole
     * test, not per-composition state. Creating them inside `setContent` would rebuild both on every
     * recomposition (and trips Compose's UnrememberedMutableState lint).
     */
    private fun failedControllerState() =
        MediaControllerState(
            controller = mockk<Player>(relaxed = true),
            playbackError =
                mutableStateOf(
                    PlaybackException(
                        "Malformed HLS manifest",
                        null,
                        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                    ),
                ),
        )

    private fun renderInBox(
        width: Dp,
        height: Dp,
        fontScale: Float = 1f,
    ) {
        val controllerState = failedControllerState()

        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                Box(Modifier.width(width).height(height)) {
                    RenderPlaybackError(
                        controllerState = controllerState,
                        videoUri = "https://streamstr.net/x/hls/live.m3u8",
                    )
                }
            }
        }
    }

    private fun assertButtonUsable() {
        rule
            .onNodeWithText(runBlocking { loadStringRes(Res.string.error_video_open_in_browser) })
            .assertIsDisplayed()
            .assertHeightIsAtLeast(ButtonDefaults.MinHeight)
    }

    @Test
    fun buttonAndTitleSurviveTheFeedsSixteenByNineBox() {
        renderInBox(width = 322.dp, height = 181.dp)
        assertButtonUsable()
        rule
            .onNodeWithText(runBlocking { loadStringRes(Res.string.error_video_playback_failed) })
            .assertIsDisplayed()
    }

    @Test
    fun buttonSurvivesTheThreadsSixteenByNineBox() {
        renderInBox(width = 385.dp, height = 217.dp)
        assertButtonUsable()
    }

    @Test
    fun shortBoxKeepsTheButtonAndDropsTheDescription() {
        // A 3:1 banner-ish stream, or a narrow quote card: far less height than 16:9 gives. A
        // weighted Text handed less than one line's height draws it clipped through the middle,
        // which looks broken — under that much pressure it should not be emitted at all.
        renderInBox(width = 322.dp, height = 110.dp)
        assertButtonUsable()
        rule
            .onNodeWithText(
                runBlocking {
                    loadStringRes(
                        Res.string.error_video_playback_failed_description,
                        "ERROR_CODE_PARSING_MANIFEST_MALFORMED",
                    )
                },
            ).assertDoesNotExist()
    }

    @Test
    fun buttonSurvivesLargeFontScale() {
        // At fontScale 2 the text wants far more height than the dp thresholds were tuned for.
        // The button must still get its intrinsic height, because it is measured before the
        // weighted text block — the guarantee is structural, not numeric.
        renderInBox(width = 322.dp, height = 195.dp, fontScale = 2f)
        assertButtonUsable()
    }

    @Test
    fun shrinkingTheBoxNeverShrinksTheButton() {
        // ButtonDefaults.MinHeight alone does not pin the guarantee: a button squeezed from its
        // intrinsic 53dp down to 40dp at fontScale 2 still clears that floor. What actually has to
        // hold is that the box height cannot influence the button's height at all, because the
        // button is measured before the weighted text block that absorbs the shortfall. Measure
        // the same button roomy and then at its tightest, and require the two to agree.
        val boxHeight = mutableStateOf(400.dp)
        val controllerState = failedControllerState()

        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                Box(Modifier.width(322.dp).height(boxHeight.value)) {
                    RenderPlaybackError(
                        controllerState = controllerState,
                        videoUri = "https://streamstr.net/x/hls/live.m3u8",
                    )
                }
            }
        }

        val roomy = buttonHeight()

        // 190dp is the worst case for the old threshold-only layout: just enough to keep the icon,
        // not enough to pay for it, so the shortfall landed on the button.
        rule.runOnUiThread { boxHeight.value = 190.dp }
        rule.waitForIdle()

        val tight = buttonHeight()
        assertTrue(
            "button shrank from $roomy to $tight when the box did",
            tight >= roomy - 1.dp,
        )
    }

    @Test
    fun theIconNeverCostsTheTitleItsHeight() {
        // The icon is non-weighted and declared first, so inside the weighted text block it is
        // measured before the title. With a fixed 190dp gate, a box of 190dp to 199dp at fontScale 2
        // was just tall enough to keep the icon and not tall enough to pay for it, so the title
        // rendered sliced. Decoration must yield before words do.
        val boxHeight = mutableStateOf(400.dp)
        val controllerState = failedControllerState()

        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                Box(Modifier.width(322.dp).height(boxHeight.value)) {
                    RenderPlaybackError(
                        controllerState = controllerState,
                        videoUri = "https://streamstr.net/x/hls/live.m3u8",
                    )
                }
            }
        }

        val roomy = titleHeight()

        rule.runOnUiThread { boxHeight.value = 195.dp }
        rule.waitForIdle()

        val tight = titleHeight()
        assertTrue(
            "title sliced from $roomy to $tight to make room for the decorative icon",
            tight >= roomy - 1.dp,
        )
    }

    private fun titleHeight(): Dp {
        val bounds =
            rule
                .onNodeWithText(runBlocking { loadStringRes(Res.string.error_video_playback_failed) })
                .getUnclippedBoundsInRoot()
        return bounds.bottom - bounds.top
    }

    private fun buttonHeight(): Dp {
        val bounds =
            rule
                .onNodeWithText(runBlocking { loadStringRes(Res.string.error_video_open_in_browser) })
                .getUnclippedBoundsInRoot()
        return bounds.bottom - bounds.top
    }
}
