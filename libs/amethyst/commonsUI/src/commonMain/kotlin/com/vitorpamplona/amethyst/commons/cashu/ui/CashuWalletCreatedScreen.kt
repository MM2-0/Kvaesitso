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
package com.vitorpamplona.amethyst.commons.cashu.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cashu_created_pick_mints
import com.vitorpamplona.amethyst.commons.resources.cashu_created_subtitle
import com.vitorpamplona.amethyst.commons.resources.cashu_created_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Celebratory interstitial shown when the user chooses to create a new Cashu
 * wallet in the find-or-create wizard, before the mint picker. An animated
 * check badge springs in behind an expanding pulse ring, the "Wallet Created"
 * headline + subtitle fade up, and a "Pick mints" button continues to the mint
 * selection — turning the otherwise abrupt jump into the mint manager into a
 * small reward.
 *
 * Purely presentational: the kind:17375 isn't published until the user actually
 * adds a mint on the next screen, so "Pick mints" replaces this screen in the
 * back stack (no awkward return to a celebration the user has moved past).
 */
@Composable
fun CashuWalletCreatedScreen(nav: INav) {
    val haptic = LocalHapticFeedback.current

    val badgeScale = remember { Animatable(0f) }
    val ringScale = remember { Animatable(0.8f) }
    val ringAlpha = remember { Animatable(0.45f) }
    val contentAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        // Badge pops in with a bouncy overshoot.
        launch {
            badgeScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            )
        }
        // A ring bursts outward from the badge and fades, one-shot.
        launch { ringScale.animateTo(2.6f, tween(durationMillis = 650, easing = FastOutSlowInEasing)) }
        launch { ringAlpha.animateTo(0f, tween(durationMillis = 650)) }
        // Text + button fade up shortly after the badge lands.
        launch {
            delay(140)
            contentAlpha.animateTo(1f, tween(durationMillis = 450))
        }
    }

    Scaffold { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Pulse ring
                Box(
                    modifier =
                        Modifier
                            .size(112.dp)
                            .graphicsLayer {
                                scaleX = ringScale.value
                                scaleY = ringScale.value
                                alpha = ringAlpha.value
                            }.clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                )
                // Check badge
                Box(
                    modifier =
                        Modifier
                            .size(112.dp)
                            .graphicsLayer {
                                scaleX = badgeScale.value
                                scaleY = badgeScale.value
                            }.clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        symbol = MaterialSymbols.Check,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(
                text = stringRes(Res.string.cashu_created_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = contentAlpha.value },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringRes(Res.string.cashu_created_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = contentAlpha.value },
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = {
                    // Replace this interstitial in the back stack so backing out
                    // of the mint picker doesn't return to the celebration.
                    nav.popUpTo(Route.CashuWalletMints, Route.CashuWalletCreated::class)
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = contentAlpha.value },
            ) {
                Text(stringRes(Res.string.cashu_created_pick_mints))
            }
        }
    }
}
