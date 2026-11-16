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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.marmot.ui.MarmotRetentionChoice
import com.vitorpamplona.amethyst.commons.marmot.ui.MarmotRetentionPicker
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.description
import com.vitorpamplona.amethyst.commons.resources.marmot_create_group_footer
import com.vitorpamplona.amethyst.commons.resources.marmot_create_group_title
import com.vitorpamplona.amethyst.commons.resources.marmot_failed_to_create_group
import com.vitorpamplona.amethyst.commons.resources.marmot_group_description_placeholder
import com.vitorpamplona.amethyst.commons.resources.marmot_group_name
import com.vitorpamplona.amethyst.commons.resources.marmot_keypackage_relays_not_set_message
import com.vitorpamplona.amethyst.commons.resources.marmot_keypackage_relays_not_set_title
import com.vitorpamplona.amethyst.commons.resources.marmot_skip_for_now
import com.vitorpamplona.amethyst.commons.resources.marmot_use_outbox_relays
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberLongNotice
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.CreatingTopBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.send.uploadMarmotGroupIcon
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

@Composable
fun CreateGroupScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var groupName by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    var pickedIcon by remember { mutableStateOf<SelectedMedia?>(null) }
    // Disappearing messages (`0x8005`). Chosen here and only here: promoting a
    // component to required later needs its state installed by a prior commit,
    // which this screen does not make.
    var disappearing by remember { mutableStateOf(MarmotRetentionChoice.OFF) }
    // Stable seed for the placeholder avatar shown before an icon is picked. The real
    // group id is generated per creation attempt (so retries don't collide), so this is
    // a separate cosmetic seed rather than "".
    val avatarSeed = remember { RandomInstance.bytes(32).toHexKey() }
    var isCreating by remember { mutableStateOf(false) }
    var showKeyPackageRelayDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val longNotice = rememberLongNotice()

    /**
     * Create the group, optionally after [prepare].
     *
     * [prepare] runs *inside* the same coroutine and is awaited, which is the whole point: the
     * KeyPackage relay list it writes is what the creation below depends on. Launching the two
     * side by side raced them, and the loser was silent -- `isCreating` had already latched true,
     * so the top bar's `isActive` gate left Create inert with no error and no group, and only
     * Cancel could leave the screen. Awaiting also puts a failure to save on the same Toast path
     * as a failure to create, instead of dropping it in a coroutine nobody reads.
     */
    fun proceedWithCreate(prepare: (suspend () -> Unit)? = null) {
        isCreating = true
        scope.launch(Dispatchers.IO) {
            try {
                prepare?.invoke()
                val nostrGroupId = RandomInstance.bytes(32).toHexKey()
                val icon = pickedIcon
                accountViewModel.createMarmotGroup(
                    nostrGroupId,
                    groupName.trim(),
                    groupDescription.trim(),
                    disappearing.seconds,
                    uploadIcon =
                        icon?.let { media ->
                            { gid -> accountViewModel.uploadMarmotGroupIcon(gid, media.uri, media.mimeType, accountViewModel.mediaUploader) }
                        },
                )
                nav.popUpTo(Route.MarmotGroupChat(nostrGroupId), Route.CreateMarmotGroup::class)
            } catch (e: Exception) {
                isCreating = false
                currentCoroutineContext().ensureActive()
                launch(Dispatchers.Main) {
                    longNotice.show(loadStringRes(Res.string.marmot_failed_to_create_group, e.message))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CreatingTopBar(
                onCancel = { nav.popBack() },
                onPost = {
                    if (!accountViewModel.hasKeyPackageRelayList()) {
                        showKeyPackageRelayDialog = true
                    } else {
                        proceedWithCreate()
                    }
                },
                isActive = { !isCreating },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePaddingSafe()
                    // The form is taller than the window once the IME is up, so
                    // `imePaddingSafe` alone just clips the retention picker and
                    // the footer off the bottom. Scrolling is what makes them
                    // reachable while the keyboard covers half the screen.
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringRes(Res.string.marmot_create_group_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )

            MarmotGroupIconEditor(
                groupId = avatarSeed,
                existingImage = null,
                pickedMedia = pickedIcon,
                removeRequested = false,
                enabled = !isCreating,
                accountViewModel = accountViewModel,
                onPick = { pickedIcon = it },
                onRemove = { pickedIcon = null },
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text(stringRes(Res.string.marmot_group_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isCreating,
            )

            Spacer(modifier = Modifier.height(16.dp))

            SpellCheckedOutlinedTextField(
                value = groupDescription,
                onValueChange = { groupDescription = it },
                label = { Text(stringRes(Res.string.description)) },
                placeholder = { Text(stringRes(Res.string.marmot_group_description_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                enabled = !isCreating,
            )

            Spacer(modifier = Modifier.height(16.dp))

            MarmotRetentionPicker(
                selected = disappearing,
                onSelect = { disappearing = it },
                enabled = !isCreating,
            )

            Text(
                stringRes(Res.string.marmot_create_group_footer),
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showKeyPackageRelayDialog) {
        MissingKeyPackageRelayListDialog(
            onConfirm = {
                showKeyPackageRelayDialog = false
                proceedWithCreate { accountViewModel.saveKeyPackageRelayListFromOutbox() }
            },
            onDismiss = {
                showKeyPackageRelayDialog = false
                proceedWithCreate()
            },
            onCancel = {
                showKeyPackageRelayDialog = false
            },
        )
    }
}

@Composable
private fun MissingKeyPackageRelayListDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringRes(Res.string.marmot_keypackage_relays_not_set_title)) },
        text = {
            Text(stringRes(Res.string.marmot_keypackage_relays_not_set_message))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(Res.string.marmot_use_outbox_relays))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.marmot_skip_for_now))
            }
        },
    )
}
