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
package com.vitorpamplona.amethyst.commons.ui.actions.uploads

import androidx.compose.foundation.layout.height
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.upload_image
import com.vitorpamplona.amethyst.commons.resources.upload_media
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.StringResource

@Composable
fun SelectFromGallery(
    isUploading: Boolean,
    enabled: Boolean = true,
    tint: Color,
    modifier: Modifier,
    onImageChosen: (ImmutableList<SelectedMedia>) -> Unit,
) {
    var showGallerySelect by remember { mutableStateOf(false) }
    if (showGallerySelect) {
        GallerySelect(
            onImageUri = { uri ->
                showGallerySelect = false
                if (uri.isNotEmpty()) {
                    onImageChosen(uri)
                }
            },
        )
    }

    GallerySelectButton(isUploading, enabled, tint, Res.string.upload_media, modifier) { showGallerySelect = true }
}

@Composable
fun SelectSingleFromGallery(
    isUploading: Boolean,
    tint: Color,
    modifier: Modifier,
    onImageChosen: (SelectedMedia) -> Unit,
) {
    var showGallerySelect by remember { mutableStateOf(false) }
    if (showGallerySelect) {
        GallerySelectSingle(
            onImageUri = { media ->
                showGallerySelect = false
                if (media != null) {
                    onImageChosen(media)
                }
            },
        )
    }

    GallerySelectButton(isUploading, true, tint, Res.string.upload_image, modifier) { showGallerySelect = true }
}

@Composable
private fun GallerySelectButton(
    isUploading: Boolean,
    enabled: Boolean,
    tint: Color,
    label: StringResource,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    HoverTooltip(stringRes(id = label)) {
        IconButton(
            modifier = modifier,
            enabled = enabled && !isUploading,
            onClick = { onClick() },
        ) {
            if (!isUploading) {
                Icon(
                    symbol = MaterialSymbols.AddPhotoAlternate,
                    contentDescription = stringRes(id = label),
                    modifier = Modifier.height(25.dp),
                    tint = tint,
                )
            } else {
                LoadingAnimation()
            }
        }
    }
}

/** Opens the platform's photo/video picker (up to 10 items); reports an empty list on cancel. */
@Composable
expect fun GallerySelect(onImageUri: (ImmutableList<SelectedMedia>) -> Unit = {})

/** Opens the platform's photo/video picker for one item ([imagesOnly]: photos only); reports null on cancel. */
@Composable
expect fun GallerySelectSingle(
    imagesOnly: Boolean = false,
    onImageUri: (SelectedMedia?) -> Unit = {},
)
