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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.wot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.vitorpamplona.amethyst.commons.model.trustedAssertions.TrustProviderRow
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import com.vitorpamplona.amethyst.commons.viewmodels.WebOfTrustCopy
import com.vitorpamplona.amethyst.commons.viewmodels.WebOfTrustSetup
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetwork
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetworkProblem
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetworkSyncStatus
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderException
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderHttp
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderOnboardingStep
import com.vitorpamplona.amethyst.commons.wot.onboarding.brainstorm.BrainstormOnboarding
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkBuilder
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkHeader
import com.vitorpamplona.quartz.utils.Hex
import org.jetbrains.skia.EncodedImageFormat
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the Web of Trust settings headlessly in every state and both themes. Like the other
 * render tests it checks structure, not pixels: every string resolves (a missing one throws) and
 * the screen follows the theme. Set WOT_RENDER_DIR to also write the PNGs for a human to look at.
 */
class WebOfTrustRenderTest {
    private val width = 820
    private val height = 2600
    private val random = Random(3)

    private fun hex() = Hex.encode(random.nextBytes(32))

    private val providerKey = hex()
    private val provider = ServiceProviderTag(ProviderTypes.rank, providerKey, RelayUrlNormalizer.normalize("wss://scores.brainstorm.world"))
    private val brainstorm = BrainstormOnboarding(TrustProviderHttp.Unsupported)

    private val network: TrustNetwork by lazy {
        val builder = TrustNetworkBuilder(providerKey, initialCapacity = 160_000)
        repeat(151_153) {
            // A long tail of low scores, like real GrapeRank output.
            val rank = (1 + 100 * random.nextDouble() * random.nextDouble() * random.nextDouble()).toInt()
            builder.add(Event(hex(), providerKey, 1000, 30382, arrayOf(arrayOf("d", hex()), arrayOf("rank", rank.toString())), "", "0".repeat(128)))
        }
        val now = System.currentTimeMillis() / 1000
        TrustNetwork(TrustNetworkHeader(providerKey, provider.relayUrl.url, now, now - 3600, now - 3600), builder.build().first)
    }

    private val actions = WebOfTrustActions({}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {})

    private fun state(
        provider: ServiceProviderTag? = this.provider,
        network: TrustNetwork? = null,
        status: TrustNetworkSyncStatus = TrustNetworkSyncStatus(),
        setup: WebOfTrustSetup = WebOfTrustSetup.Idle,
        copy: WebOfTrustCopy = WebOfTrustCopy.Idle,
    ) = WebOfTrustUiState(
        provider = provider,
        providerName = provider?.let { "Brainstorm Assistant" },
        network = network,
        status = status,
        minScore = 5,
        guidedProviders = listOf(brainstorm),
        setup = setup,
        isPrivate = false,
        copy = copy,
    )

    private val scenarios: Map<String, () -> WebOfTrustUiState> =
        linkedMapOf(
            "off" to { state(provider = null) },
            "setting-up" to { state(provider = null, setup = WebOfTrustSetup.Running("brainstorm", TrustProviderOnboardingStep.REQUESTING_SCORES)) },
            "setup-failed" to { state(provider = null, setup = WebOfTrustSetup.Failed("brainstorm", TrustProviderException.Reason.UNREACHABLE, "timeout")) },
            "downloading" to { state(status = TrustNetworkSyncStatus(running = TrustNetworkSyncStatus.Kind.DOWNLOAD, verified = 63_000, expected = 151_831)) },
            "no-scores-yet" to { state(status = TrustNetworkSyncStatus(problem = TrustNetworkProblem.NoScoresYet)) },
            "waiting-wifi" to { state(status = TrustNetworkSyncStatus(waitingForUnmetered = true)) },
            "active" to { state(network = network) },
            "updating" to { state(network = network, status = TrustNetworkSyncStatus(running = TrustNetworkSyncStatus.Kind.UPDATE, verified = 1_200)) },
        )

    /** The whole screen, for a section far down it. */
    private fun renderTall(
        name: String,
        dark: Boolean,
        content: @Composable () -> Unit,
    ) = render(name, dark, height * 2, content)

    /** Stands in for the app's user search, which needs an account. */
    @Composable
    private fun UserPickerStub() {
        OutlinedTextField(value = "", onValueChange = {}, label = { Text("Search people, npub or name@domain") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }

    /** Stands in for the app's relay entry (RelayUrlEditField), which needs an account. */
    @Composable
    private fun RelayPickerStub() {
        OutlinedTextField(value = "", onValueChange = {}, label = { Text("Add a relay") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }

    private fun render(
        name: String,
        dark: Boolean,
        content: @Composable () -> Unit,
    ): BufferedImage = render(name, dark, height, content)

    private fun render(
        name: String,
        dark: Boolean,
        sceneHeight: Int,
        content: @Composable () -> Unit,
    ): BufferedImage {
        val scene =
            ImageComposeScene(width = width, height = sceneHeight, density = Density(2f)) {
                AmethystPreviewTheme(dark = dark) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        content()
                    }
                }
            }
        return try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
            System.getenv("WOT_RENDER_DIR")?.let { dir ->
                File(dir, "wot-$name-${if (dark) "dark" else "light"}.png").writeBytes(png)
            }
            ImageIO.read(ByteArrayInputStream(png))
        } finally {
            scene.close()
        }
    }

    private fun BufferedImage.distinctColours(): Int {
        val seen = mutableSetOf<Int>()
        for (x in 0 until width step 3) {
            for (y in 0 until height step 3) seen += getRGB(x, y)
        }
        return seen.size
    }

    @Test
    fun everyStateRendersInBothThemes() {
        scenarios.forEach { (name, state) ->
            val ui = state()
            val content: @Composable () -> Unit = {
                WebOfTrustContent(ui, actions, providerAvatar = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondary)) }, relayPicker = {}, userPicker = {})
            }
            val light = render(name, false, content)
            val dark = render(name, true, content)

            assertTrue(light.distinctColours() > 20, "$name: the light screen drew ${light.distinctColours()} colours")
            assertTrue(dark.distinctColours() > 20, "$name: the dark screen drew ${dark.distinctColours()} colours")
            assertTrue(light.getRGB(2, 2) != dark.getRGB(2, 2), "$name: the screen ignored the theme")
        }
    }

    @Test
    fun theHandWrittenRowsFormRenders() {
        // As copied from Brainstorm's default view: two rows Amethyst knows, two it does not.
        val house = "78ed0837eba0ba244384195ce41d2a21575476a8e99e43f02d6e9729860e29e6"
        val relay = RelayUrlNormalizer.normalize("wss://scores.brainstorm.world")
        val copied = WebOfTrustCopy.Copied(house, listOf("30382:rank", "30382:followers", "30392", "30393").map { TrustProviderRow(it, house, relay) })
        val content: @Composable () -> Unit = { ManualRowsForm(enabled = true, copied = copied, relayPicker = {}) {} }
        val light = render("manual-rows", false, content)
        val dark = render("manual-rows", true, content)
        assertTrue(light.distinctColours() > 20, "the light form drew ${light.distinctColours()} colours")
        assertTrue(light.getRGB(2, 2) != dark.getRGB(2, 2), "the form ignored the theme")
    }

    @Test
    fun theHandWrittenSetupRendersOpen() {
        val content: @Composable () -> Unit = {
            WebOfTrustContent(
                state(provider = null),
                actions,
                providerAvatar = {},
                relayPicker = { RelayPickerStub() },
                userPicker = { UserPickerStub() },
                manualExpanded = true,
            )
        }
        renderTall("manual-open", false, content)
        renderTall("manual-open", true, content)

        // A copy arrived: the key form, filled for review.
        val house = "78ed0837eba0ba244384195ce41d2a21575476a8e99e43f02d6e9729860e29e6"
        val relay = RelayUrlNormalizer.normalize("wss://scores.brainstorm.world")
        val copied = WebOfTrustCopy.Copied(house, listOf("30382:rank", "30382:followers", "30392", "30393").map { TrustProviderRow(it, house, relay) })
        val keyForm: @Composable () -> Unit = {
            WebOfTrustContent(state(provider = null, copy = copied), actions, providerAvatar = {}, relayPicker = { RelayPickerStub() }, userPicker = { UserPickerStub() }, manualExpanded = true)
        }
        renderTall("manual-key", false, keyForm)
        renderTall("manual-key", true, keyForm)
    }
}
