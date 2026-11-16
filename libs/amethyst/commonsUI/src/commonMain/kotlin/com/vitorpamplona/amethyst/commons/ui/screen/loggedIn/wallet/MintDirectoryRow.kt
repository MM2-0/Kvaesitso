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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.nip60Cashu.CashuMintDirectoryEntry
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cashu_mint_no_recs
import com.vitorpamplona.amethyst.commons.resources.cashu_mint_plus_others
import com.vitorpamplona.amethyst.commons.resources.cashu_mint_recommended_by
import com.vitorpamplona.amethyst.commons.resources.cashu_mint_recommended_by_others
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * Shared mint directory row. Used by the wallet's add-cashu-mint
 * autocomplete and any other surface that needs to render a single
 * mint with the trust signal you'd actually act on — who in your
 * follow set has recommended it.
 *
 * Layout:
 *   - Mint display name (announcement content) or URL
 *   - URL (only when the display name was something else)
 *   - "Recommended by [up to N follow avatars] +X others" — the
 *     +X tail rolls in the non-followed recommendations so a row
 *     reflects the full kind:38000 picture at a glance. When no
 *     follows have recommended but strangers have, falls back to
 *     "Recommended by X others". Silent when nobody has.
 *   - Trailing slot for the caller's action button (Add, Pick,
 *     Already added, etc.) — keeps this composable agnostic about
 *     how the picked entry is consumed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MintDirectoryRow(
    entry: CashuMintDirectoryEntry,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val displayName =
                entry.announcement
                    ?.content
                    ?.takeIf { it.isNotBlank() && it.length < 80 }
            Text(
                text = displayName ?: entry.url,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (displayName != null) {
                Text(
                    text = entry.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            RecommendersLine(entry = entry, accountViewModel = accountViewModel, nav = nav)
        }
        Spacer(modifier = Modifier.width(12.dp))
        trailing()
    }
}

/**
 * "Recommended by [avatars] +N" / "Recommended by N others" / silent.
 * Avatar gallery shows up to MAX_FOLLOWS_RECOMMENDER_AVATARS follows;
 * any remaining follows + every non-followed recommender roll into the
 * +N suffix. Avatars are tappable (route to the user profile) via the
 * standard UserPicture composable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecommendersLine(
    entry: CashuMintDirectoryEntry,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val shownFollows = entry.followsRecommenderPubkeys
    // followsRecommendationCount may exceed shownFollows.size when the
    // gallery was capped; the remainder still needs to be counted into
    // the "+N" suffix alongside non-followed recommenders.
    val hiddenFollows = (entry.followsRecommendationCount - shownFollows.size).coerceAtLeast(0)
    val nonFollows = (entry.recommendationCount - entry.followsRecommendationCount).coerceAtLeast(0)
    val plusOthers = hiddenFollows + nonFollows

    when {
        shownFollows.isNotEmpty() -> {
            FlowRow(
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringRes(Res.string.cashu_mint_recommended_by),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                shownFollows.forEach { pubkeyHex ->
                    UserPicture(
                        userHex = pubkeyHex,
                        size = Size20dp,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
                if (plusOthers > 0) {
                    Text(
                        text = pluralStringRes(Res.plurals.cashu_mint_plus_others, plusOthers, plusOthers),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
        entry.recommendationCount > 0 -> {
            Text(
                text =
                    pluralStringRes(
                        Res.plurals.cashu_mint_recommended_by_others,
                        entry.recommendationCount,
                        entry.recommendationCount,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> {
            Text(
                text = stringRes(Res.string.cashu_mint_no_recs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
