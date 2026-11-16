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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.audio.AudioVisualizer
import com.vitorpamplona.amethyst.commons.audio.Spectrum
import com.vitorpamplona.amethyst.commons.audio.SyntheticSpectrum
import com.vitorpamplona.amethyst.commons.audio.VisualizerStyle
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.audio_visualizer_aurora
import com.vitorpamplona.amethyst.commons.resources.audio_visualizer_bars
import com.vitorpamplona.amethyst.commons.resources.audio_visualizer_classic
import com.vitorpamplona.amethyst.commons.resources.audio_visualizer_off
import com.vitorpamplona.amethyst.commons.resources.audio_visualizer_radial
import com.vitorpamplona.amethyst.commons.resources.audio_visualizer_settings
import com.vitorpamplona.amethyst.commons.resources.audio_visualizer_settings_description
import com.vitorpamplona.amethyst.commons.resources.audio_visualizer_static
import com.vitorpamplona.amethyst.commons.resources.audio_visualizer_waves
import com.vitorpamplona.amethyst.commons.ui.components.wavefront.FakeWaveformAnimation
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.flow.Flow

@Composable
fun AudioVisualizerSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = Res.string.audio_visualizer_settings), nav)
        },
    ) { padding ->
        AudioVisualizerSettingsContent(accountViewModel, Modifier.padding(padding))
    }
}

@Composable
fun AudioVisualizerSettingsContent(
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val selected by accountViewModel.audioVisualizerFlow().collectAsStateWithLifecycle()
    val previewSpectrum = remember { SyntheticSpectrum.flow(48) }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item {
            Text(
                text = stringRes(Res.string.audio_visualizer_settings_description),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(16.dp),
            )
        }
        items(VisualizerStyle.entries) { style ->
            VisualizerStyleRow(
                style = style,
                selected = style == selected,
                previewSpectrum = previewSpectrum,
                onClick = { accountViewModel.changeAudioVisualizer(style) },
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun VisualizerStyleRow(
    style: VisualizerStyle,
    selected: Boolean,
    previewSpectrum: Flow<Spectrum>,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(visualizerStyleName(style), style = MaterialTheme.typography.bodyLarge)
        }
        Box(
            modifier =
                Modifier
                    .size(width = 120.dp, height = 56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0C0C10)),
        ) {
            if (style == VisualizerStyle.CLASSIC) {
                val progress = remember { mutableFloatStateOf(0f) }
                LaunchedEffect(Unit) {
                    while (true) {
                        withFrameMillis { ms -> progress.floatValue = (ms % 1500L) / 1500f }
                    }
                }
                FakeWaveformAnimation(progress, 40, Modifier.fillMaxWidth().height(56.dp))
            } else {
                // OFF → OffRenderer (nothing), STATIC → frozen bars, others → live preview.
                AudioVisualizer(style = style, spectrum = previewSpectrum, modifier = Modifier.fillMaxWidth().height(56.dp))
            }
        }
    }
}

@Composable
private fun visualizerStyleName(style: VisualizerStyle): String =
    when (style) {
        VisualizerStyle.CLASSIC -> stringRes(Res.string.audio_visualizer_classic)
        VisualizerStyle.OFF -> stringRes(Res.string.audio_visualizer_off)
        VisualizerStyle.BARS -> stringRes(Res.string.audio_visualizer_bars)
        VisualizerStyle.WAVES -> stringRes(Res.string.audio_visualizer_waves)
        VisualizerStyle.RADIAL -> stringRes(Res.string.audio_visualizer_radial)
        VisualizerStyle.AURORA -> stringRes(Res.string.audio_visualizer_aurora)
        VisualizerStyle.STATIC -> stringRes(Res.string.audio_visualizer_static)
    }
