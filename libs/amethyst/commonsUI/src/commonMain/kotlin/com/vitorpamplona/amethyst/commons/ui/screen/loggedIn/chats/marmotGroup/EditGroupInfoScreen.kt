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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.description
import com.vitorpamplona.amethyst.commons.resources.marmot_avatar_url
import com.vitorpamplona.amethyst.commons.resources.marmot_avatar_url_footer
import com.vitorpamplona.amethyst.commons.resources.marmot_avatar_url_placeholder
import com.vitorpamplona.amethyst.commons.resources.marmot_edit_info_footer
import com.vitorpamplona.amethyst.commons.resources.marmot_failed_to_update
import com.vitorpamplona.amethyst.commons.resources.marmot_group_description_placeholder
import com.vitorpamplona.amethyst.commons.resources.marmot_group_info_updated
import com.vitorpamplona.amethyst.commons.resources.marmot_group_name
import com.vitorpamplona.amethyst.commons.resources.marmot_group_name_placeholder
import com.vitorpamplona.amethyst.commons.resources.marmot_legacy_group_no_avatar_url
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberLongNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ActionTopBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.send.uploadMarmotGroupIcon
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.marmot.client.MarmotGroupIconChange
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

@Composable
fun EditGroupInfoScreen(
    nostrGroupId: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val chatroom =
        remember(nostrGroupId) {
            accountViewModel.account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        }
    val currentName by chatroom.displayName.collectAsStateWithLifecycle()
    val currentDescription by chatroom.description.collectAsStateWithLifecycle()
    val currentImage by chatroom.image.collectAsStateWithLifecycle()
    val currentAvatarUrl by chatroom.avatarUrl.collectAsStateWithLifecycle()
    val isCurrentProfile by chatroom.isCurrentProfile.collectAsStateWithLifecycle()

    var name by remember(currentName) { mutableStateOf(currentName ?: "") }
    var description by remember(currentDescription) { mutableStateOf(currentDescription ?: "") }
    var avatarUrl by remember(currentAvatarUrl) { mutableStateOf(currentAvatarUrl?.url.orEmpty()) }
    var pickedIcon by remember { mutableStateOf<SelectedMedia?>(null) }
    var removeIcon by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val shortNotice = rememberShortNotice()
    val longNotice = rememberLongNotice()

    val iconChanged = pickedIcon != null || removeIcon
    val avatarUrlChanged = avatarUrl.trim() != currentAvatarUrl?.url.orEmpty()
    val hasChanges =
        name != (currentName ?: "") ||
            description != (currentDescription ?: "") ||
            iconChanged ||
            avatarUrlChanged

    Scaffold(
        topBar = {
            ActionTopBar(
                postRes = Res.string.save,
                onCancel = { nav.popBack() },
                onPost = {
                    isSaving = true
                    scope.launch(Dispatchers.IO) {
                        try {
                            val iconChange =
                                pickedIcon?.let { media ->
                                    MarmotGroupIconChange.Set(
                                        accountViewModel.uploadMarmotGroupIcon(nostrGroupId, media.uri, media.mimeType, accountViewModel.mediaUploader),
                                    )
                                } ?: if (removeIcon) MarmotGroupIconChange.Clear else MarmotGroupIconChange.Keep
                            accountViewModel.updateMarmotGroupMetadata(
                                nostrGroupId = nostrGroupId,
                                name = name.trim(),
                                description = description.trim(),
                                icon = iconChange,
                            )
                            // A separate component (`0x8007`) and therefore a
                            // separate commit — only made when it actually
                            // changed, so saving a rename does not also
                            // rewrite the avatar state.
                            // `isCurrentProfile` is belt-and-braces: the field
                            // is not shown on a legacy group, so the value
                            // cannot have changed. Guarding the call as well
                            // means a future edit to the form cannot turn a
                            // hidden field into a refused commit on save.
                            if (avatarUrlChanged && isCurrentProfile) {
                                accountViewModel.setMarmotGroupAvatarUrl(nostrGroupId, avatarUrl.trim())
                            }
                            launch(Dispatchers.Main) {
                                shortNotice.show(loadStringRes(Res.string.marmot_group_info_updated))
                            }
                            nav.popBack()
                        } catch (e: Exception) {
                            isSaving = false
                            currentCoroutineContext().ensureActive()
                            launch(Dispatchers.Main) {
                                longNotice.show(loadStringRes(Res.string.marmot_failed_to_update, e.message))
                            }
                        }
                    }
                },
                isActive = { !isSaving && hasChanges && name.isNotBlank() },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePaddingSafe()
                    // Same reason as CreateGroupScreen: name + description +
                    // avatar-url + footer is taller than what is left once the
                    // IME is up, so the padding alone would clip the lower
                    // fields with no way to reach them.
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            MarmotGroupIconEditor(
                groupId = nostrGroupId,
                existingImage = currentImage,
                pickedMedia = pickedIcon,
                removeRequested = removeIcon,
                enabled = !isSaving,
                accountViewModel = accountViewModel,
                onPick = {
                    pickedIcon = it
                    removeIcon = false
                },
                onRemove = {
                    pickedIcon = null
                    removeIcon = true
                },
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringRes(Res.string.marmot_group_name)) },
                placeholder = { Text(stringRes(Res.string.marmot_group_name_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSaving,
            )

            Spacer(modifier = Modifier.height(16.dp))

            SpellCheckedOutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringRes(Res.string.description)) },
                placeholder = { Text(stringRes(Res.string.marmot_group_description_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                enabled = !isSaving,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // The plain-https avatar carrier. It wins over the uploaded
            // Blossom image while it is set, and clearing it falls the group
            // back to that image — so the two fields are not alternatives to
            // choose between, they stack.
            //
            // A legacy group has no carrier for `0x8007` at all, and cannot be
            // upgraded to one, so the field is replaced by the reason rather
            // than shown and then rejected on save. The uploaded image above
            // still works there, which is what makes this a missing option
            // rather than a missing feature.
            if (isCurrentProfile) {
                OutlinedTextField(
                    value = avatarUrl,
                    onValueChange = { avatarUrl = it },
                    label = { Text(stringRes(Res.string.marmot_avatar_url)) },
                    placeholder = { Text(stringRes(Res.string.marmot_avatar_url_placeholder)) },
                    supportingText = { Text(stringRes(Res.string.marmot_avatar_url_footer)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSaving,
                )
            } else {
                Text(
                    text = stringRes(Res.string.marmot_legacy_group_no_avatar_url),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringRes(Res.string.marmot_edit_info_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}
