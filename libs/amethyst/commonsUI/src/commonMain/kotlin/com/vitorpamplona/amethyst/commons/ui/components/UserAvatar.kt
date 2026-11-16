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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings

/**
 * Wrapper class for profile picture URLs that signals Coil to use the thumbnail
 * disk cache fetcher instead of the normal network pipeline. Passing this as the
 * model to AsyncImage avoids string concatenation/parsing and lets Coil route
 * by type via Fetcher.Factory<ProfilePictureUrl>.
 */
data class ProfilePictureUrl(
    val url: String,
)

/**
 * True when the host's Coil pipeline registers a `Fetcher.Factory<ProfilePictureUrl>` (the
 * avatar thumbnail cache) and the local Blossom cache bridge that strips the profile-picture
 * request marker. Avatars then load http(s) pictures through that cache. Hosts without them
 * (desktop, iOS) keep the default `false`, so avatars load the plain URL.
 */
val LocalProfilePictureCache = staticCompositionLocalOf { false }

/**
 * The shared user avatar: the profile picture clipped to a circle, with a Robohash (or a generic
 * face icon) while it loads, when it fails, or when pictures are turned off.
 *
 * @param userHex The user's public key hex (used for Robohash generation)
 * @param pictureUrl Optional URL to the user's profile picture
 * @param size Size of the avatar (both width and height)
 * @param modifier Additional modifiers to apply
 * @param contentDescription Accessibility description
 * @param loadProfilePicture Whether to load the profile picture (false = show robohash only).
 *   Defaults to the user's display settings ([LocalDisplaySettings]), as do the next two.
 * @param loadRobohash Whether to generate robohash (false = show generic icon)
 * @param autoPlayGif Whether animated (GIF/AVIF) pictures play. Only Android can animate them;
 *   desktop shows their first frame either way.
 * @param pictureFallbacks NIP-92 `fallback` URLs tried, in order, when [pictureUrl] fails to load.
 * @param badge Optional overlay drawn on top of the avatar (bottom-right by
 *   convention). Used by Desktop for the WoT trust-score chip; Android call
 *   sites leave it null. When null the avatar renders as before (no extra
 *   `Box` wrapper).
 */
@Composable
fun UserAvatar(
    userHex: String,
    pictureUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    loadProfilePicture: Boolean = LocalDisplaySettings.current.showProfilePictures,
    loadRobohash: Boolean = LocalDisplaySettings.current.loadRobohash,
    autoPlayGif: Boolean = LocalDisplaySettings.current.autoPlayVideos,
    pictureFallbacks: List<String> = emptyList(),
    badge: @Composable (BoxScope.() -> Unit)? = null,
) {
    if (badge != null) {
        Box(modifier = modifier.size(size)) {
            UserAvatar(
                userHex = userHex,
                pictureUrl = pictureUrl,
                size = size,
                modifier = Modifier,
                contentDescription = contentDescription,
                loadProfilePicture = loadProfilePicture,
                loadRobohash = loadRobohash,
                autoPlayGif = autoPlayGif,
                pictureFallbacks = pictureFallbacks,
                badge = null,
            )
            badge()
        }
        return
    }

    val avatarModifier =
        remember(size, modifier) {
            modifier
                .size(size)
                .clip(shape = CircleShape)
        }

    if (pictureFallbacks.isEmpty()) {
        // Almost every profile: no state to keep and no error hook for the feed's avatars.
        AvatarImage(
            userHex = userHex,
            pictureUrl = pictureUrl,
            contentDescription = contentDescription,
            modifier = avatarModifier,
            loadProfilePicture = loadProfilePicture,
            loadRobohash = loadRobohash,
            autoPlayGif = autoPlayGif,
            onError = null,
        )
    } else {
        val picture = rememberFallbackUrlState(pictureUrl, pictureFallbacks)

        AvatarImage(
            userHex = userHex,
            pictureUrl = picture.url,
            contentDescription = contentDescription,
            modifier = avatarModifier,
            loadProfilePicture = loadProfilePicture,
            loadRobohash = loadRobohash,
            autoPlayGif = autoPlayGif,
            onError = picture::onError,
        )
    }
}

/**
 * Draws the avatar picture into an already sized and clipped [modifier]. [onError] is called when
 * [pictureUrl] fails to load, so the caller can move on to the next fallback URL.
 */
@Composable
internal expect fun AvatarImage(
    userHex: String,
    pictureUrl: String?,
    contentDescription: String?,
    modifier: Modifier,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
    autoPlayGif: Boolean,
    onError: (() -> Unit)?,
)
