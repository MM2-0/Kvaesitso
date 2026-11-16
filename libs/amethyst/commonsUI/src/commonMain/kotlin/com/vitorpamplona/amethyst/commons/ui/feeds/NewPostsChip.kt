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
package com.vitorpamplona.amethyst.commons.ui.feeds

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.new_posts_available_description
import com.vitorpamplona.amethyst.commons.resources.new_posts_chip
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.launch

/**
 * Twitter/Mastodon-style "New posts" floating pill chip. Slides down from above
 * its anchor when [NewPostsChipState.visible] flips true; slides back up on
 * dismissal. Tapping triggers an animated smooth-scroll to position 0 of the
 * associated [androidx.compose.foundation.lazy.LazyListState] and acknowledges
 * the new top so the chip exits.
 *
 * Pair with [rememberNewPostsChipState]. The caller is responsible for
 * placement — typically inside a [androidx.compose.foundation.layout.Box]
 * overlay aligned to the top of the feed area, offset below any sticky header.
 */
@Composable
fun NewPostsChip(
    state: NewPostsChipState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val visible by state.visible

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter =
            slideInVertically(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                initialOffsetY = { fullHeight -> -fullHeight - 16 },
            ) + fadeIn(animationSpec = tween(durationMillis = 220)),
        exit =
            slideOutVertically(
                animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing),
                targetOffsetY = { fullHeight -> -fullHeight - 16 },
            ) + fadeOut(animationSpec = tween(durationMillis = 180)),
    ) {
        val newPostsDescription = stringRes(Res.string.new_posts_available_description)
        Surface(
            onClick = { scope.launch { state.dismissAndScrollToTop() } },
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
            modifier =
                Modifier
                    .height(36.dp)
                    .semantics {
                        contentDescription = newPostsDescription
                    },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringRes(Res.string.new_posts_chip),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
