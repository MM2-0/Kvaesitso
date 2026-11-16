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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.marmot_add_photo
import com.vitorpamplona.amethyst.commons.resources.marmot_change_photo
import com.vitorpamplona.amethyst.commons.resources.marmot_group_icon
import com.vitorpamplona.amethyst.commons.resources.marmot_remove_photo
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.GallerySelectSingle
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.rememberMarmotGroupIconUrl
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.marmot.client.MarmotGroupImage
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * A circular group-avatar editor used by the create and edit metadata screens.
 *
 * Renders (in priority order) the freshly-[pickedMedia] image, a placeholder when the
 * icon is [removeRequested], or the group's current (decrypted) avatar. Tapping the
 * avatar opens the system photo picker; a text button below removes the current icon.
 * All selection state is hoisted so the parent screen can turn it into a
 * [com.vitorpamplona.marmot.client.MarmotGroupIconChange]
 * at save time.
 */
@Composable
fun MarmotGroupIconEditor(
    groupId: HexKey,
    existingImage: MarmotGroupImage?,
    pickedMedia: SelectedMedia?,
    removeRequested: Boolean,
    enabled: Boolean,
    accountViewModel: AccountViewModel,
    onPick: (SelectedMedia) -> Unit,
    onRemove: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    if (showPicker) {
        GallerySelectSingle(imagesOnly = true) { media ->
            showPicker = false
            media?.let(onPick)
        }
    }

    val model =
        when {
            pickedMedia != null -> pickedMedia.uri.toString()
            removeRequested -> null
            else -> rememberMarmotGroupIconUrl(existingImage, accountViewModel)
        }

    val hasIcon = pickedMedia != null || (existingImage != null && !removeRequested)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RobohashFallbackAsyncImage(
            robot = groupId,
            model = model,
            contentDescription = stringRes(Res.string.marmot_group_icon),
            modifier =
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .let { if (enabled) it.clickable { showPicker = true } else it },
            loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
            loadRobohash = LocalDisplaySettings.current.loadRobohash,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(
                enabled = enabled,
                onClick = { showPicker = true },
            ) {
                Text(stringRes(if (hasIcon) Res.string.marmot_change_photo else Res.string.marmot_add_photo))
            }

            if (hasIcon) {
                TextButton(
                    enabled = enabled,
                    onClick = onRemove,
                ) {
                    Text(stringRes(Res.string.marmot_remove_photo))
                }
            }
        }
    }
}
