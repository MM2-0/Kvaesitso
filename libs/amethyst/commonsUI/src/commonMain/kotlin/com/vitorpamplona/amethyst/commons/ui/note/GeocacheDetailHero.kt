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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_unnamed
import org.jetbrains.compose.resources.stringResource

/**
 * The banner a cache's own page opens with.
 *
 * Built to the same rule as the feed card — one hero, photo when there is one and the map
 * demoted to a corner inset, the map alone at 16:9 when there is not — because a detail screen
 * that opens with less presence than the card that led to it reads as a downgrade. The
 * difference from the card is that here the title lives *on* the hero, over a scrim, which buys
 * back the vertical space the card spends on a separate title row and makes the name and the
 * place one object rather than two.
 *
 * The state colour runs as a 3dp rule under the image rather than tinting anything: at banner
 * size a wash would fight the photo, while a rule reads instantly and costs no legibility. An
 * out-of-play cache fades the whole hero instead, which says "finished" without hiding it.
 */
@Composable
fun GeocacheDetailHero(
    name: String,
    photo: String?,
    point: Pair<Double, Double>?,
    accent: Color,
    emoji: String,
    distance: String?,
    isOver: Boolean,
    subtitle: @Composable () -> Unit,
    image: @Composable (url: String, modifier: Modifier) -> Unit,
    map: GeocacheMap,
) {
    val heroAlpha = if (isOver) GeocachePalette.OVER_ALPHA else 1f

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(HERO_RATIO)
                .clip(HeroShape)
                .alpha(heroAlpha),
        ) {
            when {
                photo != null -> {
                    image(photo, Modifier.fillMaxSize())
                    if (point != null) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .size(INSET_SIZE)
                                .clip(InsetShape),
                        ) {
                            map(point.first, point.second, accent, emoji, 1f, 1f)
                        }
                    }
                }

                point != null -> map(point.first, point.second, accent, emoji, 1f, HERO_RATIO)

                else -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }

            // A bottom-up scrim so white text survives a bright photo. Only painted where the
            // text sits, so the top two thirds of the picture stay untouched.
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .aspectRatio(HERO_RATIO / SCRIM_FRACTION)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)),
                        ),
                    ),
            )

            distance?.let {
                Text(
                    text = it,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Text(text = emoji, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = name.ifEmpty { stringResource(Res.string.geocache_unnamed) },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                subtitle()
            }
        }

        // height, not size: size() after fillMaxWidth() overrides the width too and leaves a
        // 3dp dot instead of a rule.
        Box(
            Modifier
                .fillMaxWidth()
                .height(ACCENT_RULE)
                .background(accent.copy(alpha = if (isOver) GeocachePalette.OVER_ALPHA else 1f)),
        )
    }
}

private val HeroShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
private val InsetShape = RoundedCornerShape(10.dp)
private val INSET_SIZE = 76.dp
private val ACCENT_RULE = 3.dp
private const val HERO_RATIO = 16f / 9f

/** How much of the hero's height the scrim covers, bottom-up. */
private const val SCRIM_FRACTION = 0.45f
