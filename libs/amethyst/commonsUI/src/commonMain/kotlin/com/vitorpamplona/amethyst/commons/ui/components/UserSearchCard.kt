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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.accessibility_navigate
import com.vitorpamplona.amethyst.commons.resources.accessibility_user_avatar
import org.jetbrains.compose.resources.stringResource

/**
 * A card displaying user search result with avatar, name, and nip05/pubkey.
 * Shared between Android and Desktop search screens.
 *
 * @param badge Optional overlay drawn on top of the avatar (bottom-right
 *   by convention). Used by Desktop for the WoT trust-score chip; Android
 *   call sites leave it null. Forwarded to [UserAvatar].
 *
 * Loads the user's kind-0 metadata only while the card is composed via
 * [observeUserInfo], so a search-results list only fetches metadata for the
 * users currently on screen (coalesced into the shared finder's batched REQs).
 * Requires [LocalUserFinder]/[LocalUserFinderAccount] in scope — provided at
 * the Desktop logged-in roots; this card is Desktop-only.
 */
@Composable
fun UserSearchCard(
    user: User,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: @Composable (BoxScope.() -> Unit)? = null,
) {
    val metadata by observeUserInfo(user)

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UserAvatar(
                userHex = user.pubkeyHex,
                pictureUrl = metadata?.info?.picture ?: user.profilePicture(),
                size = 40.dp,
                contentDescription = stringResource(Res.string.accessibility_user_avatar),
                badge = badge,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    metadata?.info?.bestName() ?: user.toBestDisplayName(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val nip05 = metadata?.info?.nip05 ?: user.metadataOrNull()?.nip05()
                if (nip05 != null) {
                    Text(
                        nip05,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        user.pubkeyDisplayHex(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Icon(
                MaterialSymbols.AutoMirrored.ArrowForward,
                contentDescription = stringResource(Res.string.accessibility_navigate),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
