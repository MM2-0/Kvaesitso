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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.vitorpamplona.amethyst.commons.hashtags.Amethyst
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.icons.Like
import com.vitorpamplona.amethyst.commons.icons.Liked
import com.vitorpamplona.amethyst.commons.icons.Reply
import com.vitorpamplona.amethyst.commons.icons.Repost
import com.vitorpamplona.amethyst.commons.icons.Reposted
import com.vitorpamplona.amethyst.commons.icons.Search
import com.vitorpamplona.amethyst.commons.icons.Zap
import com.vitorpamplona.amethyst.commons.icons.following
import com.vitorpamplona.amethyst.commons.icons.symbols.AmethystIconGlyph
import com.vitorpamplona.amethyst.commons.icons.symbols.AmethystIcons
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.accessibility_download_for_offline
import com.vitorpamplona.amethyst.commons.resources.accessibility_play_username
import com.vitorpamplona.amethyst.commons.resources.accessibility_pushpin
import com.vitorpamplona.amethyst.commons.resources.app_logo
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.boost_or_quote_description
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.cashu
import com.vitorpamplona.amethyst.commons.resources.change_reaction
import com.vitorpamplona.amethyst.commons.resources.clear
import com.vitorpamplona.amethyst.commons.resources.copy_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.enter_picture_in_picture
import com.vitorpamplona.amethyst.commons.resources.following
import com.vitorpamplona.amethyst.commons.resources.lightning_address
import com.vitorpamplona.amethyst.commons.resources.like_description
import com.vitorpamplona.amethyst.commons.resources.note_options
import com.vitorpamplona.amethyst.commons.resources.onchain_zap_description
import com.vitorpamplona.amethyst.commons.resources.onchain_zap_pending
import com.vitorpamplona.amethyst.commons.resources.record_a_message
import com.vitorpamplona.amethyst.commons.resources.reply_description
import com.vitorpamplona.amethyst.commons.resources.search_button
import com.vitorpamplona.amethyst.commons.resources.share_or_save
import com.vitorpamplona.amethyst.commons.resources.website
import com.vitorpamplona.amethyst.commons.resources.zap_description
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.LikedColor
import com.vitorpamplona.amethyst.commons.ui.theme.RepostedColor
import com.vitorpamplona.amethyst.commons.ui.theme.Size19Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size30Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleButton
import org.jetbrains.compose.resources.StringResource

@Composable
fun AmethystIcon(iconSize: Dp) {
    Icon(
        imageVector = CustomHashTagIcons.Amethyst,
        contentDescription = stringRes(id = Res.string.app_logo),
        modifier = Modifier.size(iconSize),
        tint = Color.Unspecified,
    )
}

@Composable
fun FollowingIcon(modifier: Modifier) {
    // Deep, saturated accent shield + white figure, like the original #7F2EFF badge. Use the accent's
    // deep tone (its light-theme primary) in both themes: dark-theme `primary` is a pale pastel, so
    // the shield would read washed out — inversePrimary carries the deep tone in the dark scheme,
    // while `primary` already is it in light.
    val scheme = MaterialTheme.colorScheme
    val shield = if (scheme.isLight) scheme.primary else scheme.inversePrimary
    val icon = remember(shield) { following(shield, Color.White) }
    Icon(
        imageVector = icon,
        contentDescription = stringRes(id = Res.string.following),
        modifier = modifier,
        tint = Color.Unspecified,
    )
}

@Composable
fun ArrowBackIcon(tint: Color = MaterialTheme.colorScheme.grayText) {
    Icon(
        symbol = MaterialSymbols.AutoMirrored.ArrowBack,
        contentDescription = stringRes(Res.string.back),
        tint = tint,
    )
}

@Composable
fun DownloadForOfflineIcon(
    iconSize: Dp,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Icon(
        symbol = MaterialSymbols.DownloadForOffline,
        contentDescription = stringRes(id = Res.string.accessibility_download_for_offline),
        modifier = remember(iconSize) { Modifier.size(iconSize) },
        tint = tint,
    )
}

@Composable
fun LikedIcon(
    modifier: Modifier,
    tint: Color = LikedColor,
) {
    AmethystIconGlyph(
        symbol = AmethystIcons.Liked,
        contentDescription = stringRes(id = Res.string.like_description),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun ChangeReactionIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        symbol = MaterialSymbols.AddReaction,
        contentDescription = stringRes(id = Res.string.change_reaction),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun LikeIcon(
    iconSizeModifier: Modifier,
    grayTint: Color,
) {
    AmethystIconGlyph(
        symbol = AmethystIcons.Like,
        contentDescription = stringRes(id = Res.string.like_description),
        modifier = iconSizeModifier,
        tint = grayTint,
    )
}

@Composable
fun RepostIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) {
    AmethystIconGlyph(
        symbol = AmethystIcons.Repost,
        contentDescription = stringRes(id = Res.string.boost_or_quote_description),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun RepostedIcon(
    modifier: Modifier,
    tint: Color = RepostedColor,
) {
    AmethystIconGlyph(
        symbol = AmethystIcons.Reposted,
        contentDescription = stringRes(id = Res.string.boost_or_quote_description),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun LightningAddressIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        symbol = MaterialSymbols.Bolt,
        contentDescription = stringRes(Res.string.lightning_address),
        tint = tint,
        modifier = modifier,
    )
}

@Composable
fun ZappedIcon(iconSize: Dp) {
    ZappedIcon(modifier = remember(iconSize) { Modifier.size(iconSize) })
}

@Composable
fun ZappedIcon(modifier: Modifier) {
    ZapIcon(modifier = modifier, BitcoinOrange)
}

@Preview
@Composable
fun ReactionRowIconPreview() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CommentIcon(Size20Modifier, Color.Unspecified)
        RepostedIcon(Size20Modifier)
        LikeIcon(Size20Modifier, Color.Unspecified)
        OutlinedZapIcon(Size20Modifier)
        ZapIcon(Size20Modifier)
        ZappedIcon(Size20Modifier)
        ShareIcon(Size20Modifier, Color.Unspecified)
    }
}

@Composable
fun ZapIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
    contentDescriptor: StringResource = Res.string.zap_description,
) {
    Icon(
        symbol = MaterialSymbols.Bolt,
        contentDescription = stringRes(contentDescriptor),
        tint = tint,
        modifier = modifier,
    )
}

@Composable
fun OutlinedZapIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
    contentDescriptor: StringResource = Res.string.zap_description,
) {
    Icon(
        imageVector = Zap,
        contentDescription = stringRes(contentDescriptor),
        tint = tint,
        modifier = modifier,
    )
}

@Composable
fun ShareIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        symbol = MaterialSymbols.Share,
        modifier = modifier,
        contentDescription = stringRes(Res.string.share_or_save),
        tint = tint,
    )
}

@Composable
fun CashuIcon(modifier: Modifier) {
    Icon(
        imageVector = CustomHashTagIcons.Cashu,
        stringRes(Res.string.cashu),
        modifier = modifier,
    )
}

@Composable
fun CopyIcon(modifier: Modifier) {
    Icon(
        symbol = MaterialSymbols.ContentCopy,
        stringRes(id = Res.string.copy_to_clipboard),
        modifier = modifier,
    )
}

@Composable
fun OpenInNewIcon(modifier: Modifier) {
    Icon(
        symbol = MaterialSymbols.AutoMirrored.OpenInNew,
        stringRes(id = Res.string.copy_to_clipboard),
        modifier = modifier,
    )
}

@Composable
fun ExpandLessIcon(
    modifier: Modifier,
    contentDescriptor: StringResource,
) {
    Icon(
        symbol = MaterialSymbols.ExpandLess,
        contentDescription = stringRes(id = contentDescriptor),
        modifier = modifier,
        tint = MaterialTheme.colorScheme.subtleButton,
    )
}

@Composable
fun ExpandMoreIcon(
    modifier: Modifier,
    contentDescriptor: StringResource,
) {
    Icon(
        symbol = MaterialSymbols.ExpandMore,
        contentDescription = stringRes(id = contentDescriptor),
        modifier = modifier,
        tint = MaterialTheme.colorScheme.subtleButton,
    )
}

@Composable
fun VoiceReplyIcon(
    iconSizeModifier: Modifier,
    tint: Color,
) {
    Icon(
        symbol = MaterialSymbols.Mic,
        contentDescription = stringRes(id = Res.string.record_a_message),
        tint = tint,
        modifier = iconSizeModifier,
    )
}

@Composable
fun CommentIcon(
    iconSizeModifier: Modifier,
    tint: Color,
) {
    AmethystIconGlyph(
        symbol = AmethystIcons.Reply,
        contentDescription = stringRes(id = Res.string.reply_description),
        modifier = iconSizeModifier,
        tint = tint,
    )
}

@Composable
fun CancelIcon() {
    Icon(
        symbol = MaterialSymbols.Cancel,
        contentDescription = stringRes(id = Res.string.cancel),
        modifier = Size30Modifier,
        tint = MaterialTheme.colorScheme.placeholderText,
    )
}

@Composable
fun CloseIcon() {
    Icon(
        symbol = MaterialSymbols.Close,
        contentDescription = stringRes(id = Res.string.cancel),
        modifier = Size20Modifier,
    )
}

@Composable
fun SearchIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) {
    AmethystIconGlyph(
        symbol = AmethystIcons.Search,
        contentDescription = stringRes(id = Res.string.search_button),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun PlayIcon(
    modifier: Modifier,
    tint: Color,
) {
    Icon(
        symbol = MaterialSymbols.PlayCircle,
        contentDescription = stringRes(id = Res.string.accessibility_play_username),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun PinIcon(
    modifier: Modifier,
    tint: Color,
) {
    Icon(
        symbol = MaterialSymbols.PushPin,
        contentDescription = stringRes(id = Res.string.accessibility_pushpin),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun EnablePiP(
    modifier: Modifier,
    tint: Color,
) {
    Icon(
        symbol = MaterialSymbols.AutoMirrored.OpenInNew,
        contentDescription = stringRes(id = Res.string.enter_picture_in_picture),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun ClearTextIcon() {
    Icon(
        symbol = MaterialSymbols.Clear,
        contentDescription = stringRes(Res.string.clear),
    )
}

@Composable
fun LinkIcon(
    modifier: Modifier,
    tint: Color,
) {
    Icon(
        symbol = MaterialSymbols.Link,
        contentDescription = stringRes(Res.string.website),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun VerticalDotsIcon() {
    Icon(
        symbol = MaterialSymbols.MoreVert,
        contentDescription = stringRes(id = Res.string.note_options),
        modifier = Size19Modifier,
        tint = MaterialTheme.colorScheme.placeholderText,
    )
}

@Composable
fun OnchainZappedIcon(modifier: Modifier) {
    Icon(
        symbol = MaterialSymbols.CurrencyBitcoin,
        contentDescription = stringRes(Res.string.onchain_zap_description),
        tint = BitcoinOrange,
        modifier = modifier,
    )
}

@Composable
fun PendingClockBadge(modifier: Modifier) {
    Icon(
        symbol = MaterialSymbols.Schedule,
        contentDescription = stringRes(Res.string.onchain_zap_pending),
        tint = BitcoinOrange,
        modifier = modifier,
    )
}
