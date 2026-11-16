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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.ShowImageUploadGallery
import com.vitorpamplona.amethyst.commons.ui.components.FormImageTile
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import org.jetbrains.compose.resources.StringResource

/**
 * Square cover-image picker shared by the music track and playlist composers.
 *
 * Three states, in priority order:
 *  - a freshly picked local file ([cover]) → upload preview (tap to swap, close button to remove);
 *  - otherwise an already-published cover ([existingUrl], set when editing) → the remote image with
 *    the same tap-to-swap / remove affordances, so editing a track/playlist shows its current art;
 *  - otherwise the dashed upload placeholder.
 *
 * While an upload is in flight (`enabled = false`) the tap/delete gestures are dropped so the user
 * can't mutate the selection mid-upload, but the preview stays visible so they can see what's being
 * sent.
 */
@Composable
fun CoverImagePicker(
    cover: MultiOrchestrator?,
    existingUrl: String?,
    onPick: () -> Unit,
    onDelete: () -> Unit,
    accountViewModel: AccountViewModel,
    enabled: Boolean,
    ctaRes: StringResource,
    hintRes: StringResource,
) {
    FormImageTile {
        when {
            cover != null ->
                Box(modifier = if (enabled) Modifier.clickable(onClick = onPick) else Modifier) {
                    ShowImageUploadGallery(
                        list = cover,
                        onDelete = { if (enabled) onDelete() },
                        accountViewModel = accountViewModel,
                    )
                }

            !existingUrl.isNullOrBlank() ->
                ExistingCoverPreview(
                    url = existingUrl,
                    onPick = onPick,
                    onDelete = onDelete,
                    enabled = enabled,
                )

            else ->
                UploadPlaceholder(
                    iconSymbol = MaterialSymbols.AddPhotoAlternate,
                    ctaRes = ctaRes,
                    hintRes = hintRes,
                    onClick = onPick,
                    enabled = enabled,
                )
        }
    }
}

/**
 * Renders the already-published cover (a remote URL) as a square tile matching the upload preview:
 * tap anywhere to pick a replacement, or use the corner button to clear it. Used in edit mode so
 * the composer reflects the cover the event already carries instead of showing an empty placeholder.
 */
@Composable
private fun ExistingCoverPreview(
    url: String,
    onPick: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .let { if (enabled) it.clickable(onClick = onPick) else it },
    ) {
        val painter = rememberAsyncImagePainter(model = url)
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        if (enabled) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(onClick = onDelete)
                        .padding(4.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Shared upload-placeholder card. The cover pickers use a 1:1 aspect ratio (square upload tile);
 * the audio picker leaves [aspectRatio] null so the row hugs the icon + text content.
 */
@Composable
fun UploadPlaceholder(
    iconSymbol: MaterialSymbol,
    ctaRes: StringResource,
    hintRes: StringResource,
    onClick: () -> Unit,
    aspectRatio: Float? = 1f,
    enabled: Boolean = true,
) {
    val baseModifier =
        Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp),
            ).let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(24.dp)

    val finalModifier =
        if (aspectRatio != null) baseModifier.aspectRatio(aspectRatio) else baseModifier

    Box(
        modifier = finalModifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                symbol = iconSymbol,
                contentDescription = null,
                modifier = Modifier.size(if (aspectRatio != null) 56.dp else 36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Box(modifier = Modifier.height(12.dp))
            Text(
                text = stringRes(ctaRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Box(modifier = Modifier.height(4.dp))
            Text(
                text = stringRes(hintRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Banner shown at the top of a composer form while the send coroutine is in flight. The coroutine
 * runs on `accountViewModel.viewModelScope` so it survives the screen; this banner is the user's
 * primary feedback that something IS happening.
 */
@Composable
fun UploadInProgressBanner(messageRes: StringResource) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Box(modifier = Modifier.size(12.dp))
        Text(
            text = stringRes(messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
