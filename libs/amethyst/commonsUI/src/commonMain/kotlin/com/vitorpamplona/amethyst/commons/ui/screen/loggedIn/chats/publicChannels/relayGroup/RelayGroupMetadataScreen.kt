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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_channel_create_title
import com.vitorpamplona.amethyst.commons.resources.buzz_channel_flag_private
import com.vitorpamplona.amethyst.commons.resources.buzz_channel_flag_private_desc
import com.vitorpamplona.amethyst.commons.resources.buzz_forum_create_title
import com.vitorpamplona.amethyst.commons.resources.relay_group_add_photo
import com.vitorpamplona.amethyst.commons.resources.relay_group_change_photo
import com.vitorpamplona.amethyst.commons.resources.relay_group_create_title
import com.vitorpamplona.amethyst.commons.resources.relay_group_edit_title
import com.vitorpamplona.amethyst.commons.resources.relay_group_field_about
import com.vitorpamplona.amethyst.commons.resources.relay_group_field_banner
import com.vitorpamplona.amethyst.commons.resources.relay_group_field_geohash
import com.vitorpamplona.amethyst.commons.resources.relay_group_field_geohash_hint
import com.vitorpamplona.amethyst.commons.resources.relay_group_field_name
import com.vitorpamplona.amethyst.commons.resources.relay_group_field_picture
import com.vitorpamplona.amethyst.commons.resources.relay_group_field_topics
import com.vitorpamplona.amethyst.commons.resources.relay_group_field_topics_hint
import com.vitorpamplona.amethyst.commons.resources.relay_group_flag_hidden
import com.vitorpamplona.amethyst.commons.resources.relay_group_flag_hidden_desc
import com.vitorpamplona.amethyst.commons.resources.relay_group_flag_invite_only
import com.vitorpamplona.amethyst.commons.resources.relay_group_flag_invite_only_desc
import com.vitorpamplona.amethyst.commons.resources.relay_group_flag_private
import com.vitorpamplona.amethyst.commons.resources.relay_group_flag_private_desc
import com.vitorpamplona.amethyst.commons.resources.relay_group_flag_restricted
import com.vitorpamplona.amethyst.commons.resources.relay_group_flag_restricted_desc
import com.vitorpamplona.amethyst.commons.resources.relay_group_location_add
import com.vitorpamplona.amethyst.commons.resources.relay_group_location_add_desc
import com.vitorpamplona.amethyst.commons.resources.relay_group_location_clear
import com.vitorpamplona.amethyst.commons.resources.relay_group_location_manual
import com.vitorpamplona.amethyst.commons.resources.relay_group_relay_no_nip29
import com.vitorpamplona.amethyst.commons.resources.relay_group_section_discovery
import com.vitorpamplona.amethyst.commons.resources.relay_group_section_discovery_desc
import com.vitorpamplona.amethyst.commons.resources.relay_group_section_permissions
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.GallerySelectSingle
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.CreatingTopBar
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadCityName
import com.vitorpamplona.amethyst.commons.ui.note.platform.LocationPreviewMap
import com.vitorpamplona.amethyst.commons.ui.platform.GeohashLocationPickerDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.LoadRelayGroupChannel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupCardWarmupSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/**
 * Create a new NIP-29 group on [relayUrl]: publishes kind 9007 + 9002 with every
 * metadata field the user sets (name, description, picture, and the four status
 * flags), then opens the new group. The user becomes its first admin.
 */
@Composable
fun RelayGroupCreateScreen(
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    // Buzz only: pre-select the `forum` channel type (the Forums section's "+" routes here with true).
    isForum: Boolean = false,
) {
    val relay = remember(relayUrl) { RelayUrlNormalizer.normalizeOrNull(relayUrl) } ?: return
    val viewModel: RelayGroupMetadataViewModel = rememberViewModel(key = "RelayGroupCreate:$relayUrl") { RelayGroupMetadataViewModel() }
    LaunchedEffect(relay) {
        viewModel.initCreate(accountViewModel, relay)
        if (isForum) viewModel.isForum = true
    }

    // A group only works if the relay actually runs NIP-29 (otherwise it stores our 9007/9002 as
    // ordinary events, never emits metadata/roster, and the "group" is a dead hex id). Gate creation
    // on the relay advertising NIP-29 in its NIP-11 `supported_nips`. Tri-state: null = still checking,
    // false = confirmed unsupported (or unreachable), true = advertised.
    val nip29Support by produceState<Boolean?>(initialValue = null, relay) {
        LocalCache.appHost.nip11Cache.loadRelayInfo(
            relay = relay,
            onInfo = { info -> value = info.supported_nips?.any { it == "29" } ?: false },
            onError = { _, _, _ -> value = false },
        )
    }

    RelayGroupMetadataScaffold(
        viewModel = viewModel,
        accountViewModel = accountViewModel,
        nav = nav,
        nip29Support = nip29Support,
        onSuccess = {
            // A new forum opens on its thread list, where its posts live — the same screen the
            // workspace list opens a forum on — not on the chat timeline a stream channel uses.
            val destination =
                if (viewModel.isForum) {
                    Route.RelayGroupThreads(viewModel.groupId, relay.url)
                } else {
                    Route.RelayGroup(viewModel.groupId, relay.url)
                }
            nav.popUpTo(destination, Route.RelayGroupCreate::class)
        },
    )
}

/**
 * Edit a group's relay-signed metadata (kind 9002, admin only). Pre-fills from the
 * group's current metadata reactively (so a late first load still populates), then
 * saves and returns.
 */
@Composable
fun RelayGroupEditScreen(
    id: String,
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val relay = remember(relayUrl) { RelayUrlNormalizer.normalizeOrNull(relayUrl) } ?: return
    val groupId = remember(id, relay) { GroupId(id, relay) }
    val viewModel: RelayGroupMetadataViewModel = rememberViewModel(key = "RelayGroupEdit:${groupId.toKey()}") { RelayGroupMetadataViewModel() }

    LoadRelayGroupChannel(groupId) { channel ->
        // Keep the relay-signed metadata fresh while editing so a late load prefills.
        RelayGroupCardWarmupSubscription(channel, accountViewModel.dataSources().relayGroupCardWarmup, accountViewModel)

        val channelState by channel
            .flow()
            .metadata.stateFlow
            .collectAsStateWithLifecycle()
        val liveChannel = channelState.channel as? RelayGroupChannel ?: channel

        LaunchedEffect(liveChannel.groupId) { viewModel.initEdit(accountViewModel, liveChannel) }
        // Re-seed when the metadata event changes, unless the user has already edited.
        LaunchedEffect(liveChannel.event?.id) { viewModel.prefillFrom(liveChannel) }

        RelayGroupMetadataScaffold(
            viewModel = viewModel,
            accountViewModel = accountViewModel,
            nav = nav,
            onSuccess = { nav.popBack() },
        )
    }
}

@Composable
private fun RelayGroupMetadataScaffold(
    viewModel: RelayGroupMetadataViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    onSuccess: () -> Unit,
    // Whether the target relay advertises NIP-29. null = still checking, false = confirmed
    // unsupported (block + warn), true = supported. Only meaningful when creating; editing an
    // existing group already implies a working relay, so callers pass true.
    nip29Support: Boolean? = true,
) {
    val scrollState = rememberScrollState()

    // NIP-29 §Subgroups relay support detection: `"nip29": { "subgroups": true }` in the NIP-11.
    val subgroupsSupported by produceState(initialValue = false, viewModel.relay) {
        val relay = viewModel.relay ?: return@produceState
        LocalCache.appHost.nip11Cache.loadRelayInfo(
            relay = relay,
            onInfo = { info -> value = info.nip29?.subgroups == true },
            onError = { _, _, _ -> value = false },
        )
    }

    var wantsToPickImage by remember { mutableStateOf(false) }
    if (wantsToPickImage) {
        GallerySelectSingle(
            onImageUri = { media ->
                wantsToPickImage = false
                if (media != null) viewModel.pickMedia(media)
            },
        )
    }

    val onSubmit: () -> Unit = {
        viewModel.submit(
            uploader = accountViewModel.mediaUploader,
            onSuccess = onSuccess,
            onError = accountViewModel.toastManager::toast,
        )
    }

    Scaffold(
        topBar = {
            if (viewModel.isNewGroup) {
                // The type is fixed by the caller (the community's per-section "+"), so the title names
                // it — "New forum" vs "New channel" on Buzz — rather than offering a toggle to change it.
                CreatingTopBar(
                    titleRes =
                        when {
                            !viewModel.isBuzzRelay -> Res.string.relay_group_create_title
                            viewModel.isForum -> Res.string.buzz_forum_create_title
                            else -> Res.string.buzz_channel_create_title
                        },
                    isActive = { viewModel.canPost && (nip29Support == true || viewModel.isBuzzRelay) },
                    onCancel = nav::popBack,
                    onPost = onSubmit,
                )
            } else {
                SavingTopBar(
                    titleRes = Res.string.relay_group_edit_title,
                    isActive = viewModel::canPost,
                    onCancel = nav::popBack,
                    onPost = onSubmit,
                )
            }
        },
    ) { pad ->
        Surface(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePaddingSafe(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (nip29Support == false && !viewModel.isBuzzRelay) {
                    NoNip29Warning()
                    Spacer(Modifier.height(16.dp))
                }

                if (!viewModel.isBuzzRelay) {
                    GroupImagePicker(viewModel) { wantsToPickImage = true }
                    Spacer(Modifier.height(16.dp))
                }

                GroupMetadataFields(viewModel)

                // Sub-groups are a NIP-29 relation; Buzz has no parent channel. Offer the parent picker
                // only when the relay advertises `nip29: { subgroups: true }` in its NIP-11 (otherwise
                // it'd reject the `parent` tag), or when this group already has a parent so an admin
                // can still detach it.
                if (!viewModel.isBuzzRelay && (subgroupsSupported || viewModel.parentGroupId != null)) {
                    Spacer(Modifier.height(16.dp))
                    ParentGroupSection(viewModel, accountViewModel)
                }
            }
        }
    }
}

/** A warning shown when the target relay doesn't advertise NIP-29, blocking creation. */
@Composable
private fun NoNip29Warning() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringRes(Res.string.relay_group_relay_no_nip29),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/** The tap-to-upload group avatar hero at the top of the form. */
@Composable
private fun GroupImagePicker(
    viewModel: RelayGroupMetadataViewModel,
    onPick: () -> Unit,
) {
    val picked = viewModel.pickedMedia
    val currentUrl = viewModel.picture.value.text
    val model: Any? = picked?.uri ?: currentUrl.ifBlank { null }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = stringRes(Res.string.relay_group_field_picture),
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable(onClick = onPick),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable(onClick = onPick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    symbol = MaterialSymbols.AddPhotoAlternate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text =
                if (model != null) {
                    stringRes(Res.string.relay_group_change_photo)
                } else {
                    stringRes(Res.string.relay_group_add_photo)
                },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onPick),
        )
    }
}

@Composable
private fun GroupMetadataFields(viewModel: RelayGroupMetadataViewModel) {
    OutlinedTextField(
        value = viewModel.name.value,
        onValueChange = {
            viewModel.name.value = it
            viewModel.markTouched()
        },
        singleLine = true,
        label = { Text(stringRes(Res.string.relay_group_field_name)) },
        modifier = Modifier.fillMaxWidth(),
    )

    SpellCheckedOutlinedTextField(
        value = viewModel.about.value,
        onValueChange = {
            viewModel.about.value = it
            viewModel.markTouched()
        },
        label = { Text(stringRes(Res.string.relay_group_field_about)) },
        minLines = 2,
        maxLines = 6,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )

    // Everything below is NIP-29 vocabulary. A Buzz relay stores only `name`, `about` and a
    // two-valued `visibility` (its 9002 handler accepts nothing else), so offering a banner, hashtags,
    // a geohash, or the invite-only/restricted/hidden flags there would be controls that look like
    // they configure the channel and are silently dropped by the relay.
    if (!viewModel.isBuzzRelay) {
        OutlinedTextField(
            value = viewModel.banner.value,
            onValueChange = {
                viewModel.banner.value = it
                viewModel.markTouched()
            },
            singleLine = true,
            label = { Text(stringRes(Res.string.relay_group_field_banner)) },
            placeholder = { Text("https://…") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringRes(Res.string.relay_group_section_discovery),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringRes(Res.string.relay_group_section_discovery_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )

        OutlinedTextField(
            value = viewModel.topics.value,
            onValueChange = {
                viewModel.topics.value = it
                viewModel.markTouched()
            },
            singleLine = true,
            label = { Text(stringRes(Res.string.relay_group_field_topics)) },
            placeholder = { Text(stringRes(Res.string.relay_group_field_topics_hint)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        GroupLocationField(viewModel)
    }

    Spacer(Modifier.height(12.dp))
    Text(
        text = stringRes(Res.string.relay_group_section_permissions),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )

    // Buzz's `visibility`: open = searchable and anyone may join, private = hidden and invite-only.
    // One switch covers both, so it keeps NIP-29's private flag but says what Buzz actually does.
    LabeledSwitchRow(
        label = stringRes(if (viewModel.isBuzzRelay) Res.string.buzz_channel_flag_private else Res.string.relay_group_flag_private),
        description = stringRes(if (viewModel.isBuzzRelay) Res.string.buzz_channel_flag_private_desc else Res.string.relay_group_flag_private_desc),
        checked = viewModel.isPrivate,
    ) {
        viewModel.isPrivate = it
        viewModel.markTouched()
    }

    // A Buzz channel's type (chat vs forum) is fixed by the caller — the community's per-section "+" —
    // and isn't editable (the relay's 9002 has no `channel_type` key), so there's no toggle here. The
    // remaining vanilla NIP-29 flags (invite-only / restricted below) don't apply to Buzz either, so
    // stop here for a Buzz relay.
    if (viewModel.isBuzzRelay) return

    LabeledSwitchRow(
        label = stringRes(Res.string.relay_group_flag_invite_only),
        description = stringRes(Res.string.relay_group_flag_invite_only_desc),
        checked = viewModel.isClosed,
    ) {
        viewModel.isClosed = it
        viewModel.markTouched()
    }
    LabeledSwitchRow(
        label = stringRes(Res.string.relay_group_flag_restricted),
        description = stringRes(Res.string.relay_group_flag_restricted_desc),
        checked = viewModel.isRestricted,
    ) {
        viewModel.isRestricted = it
        viewModel.markTouched()
    }
    LabeledSwitchRow(
        label = stringRes(Res.string.relay_group_flag_hidden),
        description = stringRes(Res.string.relay_group_flag_hidden_desc),
        checked = viewModel.isHidden,
    ) {
        viewModel.isHidden = it
        viewModel.markTouched()
    }
}

/**
 * The group's discovery location. Map-first: an inviting card opens a full-screen
 * map picker ([GeohashLocationPickerDialog]) that turns a tapped/searched place
 * into the geohash the ViewModel already stores. A collapsed "enter manually"
 * field keeps the paste-a-known-geohash path for power users. The geohash text in
 * [RelayGroupMetadataViewModel.geohash] stays the single source of truth.
 */
@Composable
private fun GroupLocationField(viewModel: RelayGroupMetadataViewModel) {
    var showPicker by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }

    val geohash =
        viewModel.geohash.value.text
            .trim()

    if (geohash.isNotBlank()) {
        SelectedLocationCard(
            geohash = geohash,
            onEdit = { showPicker = true },
            onClear = {
                viewModel.geohash.value = TextFieldValue("")
                viewModel.markTouched()
            },
        )
    } else {
        AddLocationCard(onClick = { showPicker = true })
    }

    TextButton(
        onClick = { showManual = !showManual },
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Edit,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringRes(Res.string.relay_group_location_manual),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
    if (showManual) {
        OutlinedTextField(
            value = viewModel.geohash.value,
            onValueChange = {
                viewModel.geohash.value = it
                viewModel.markTouched()
            },
            singleLine = true,
            label = { Text(stringRes(Res.string.relay_group_field_geohash)) },
            placeholder = { Text(stringRes(Res.string.relay_group_field_geohash_hint)) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }

    if (showPicker) {
        GeohashLocationPickerDialog(
            initialGeohash = geohash.ifBlank { null },
            onDismiss = { showPicker = false },
            onConfirm = { cell ->
                viewModel.geohash.value = TextFieldValue(cell)
                viewModel.markTouched()
                showPicker = false
            },
        )
    }
}

/** Empty-state call to action inviting the user to pin the group on a map. */
@Composable
private fun AddLocationCard(onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    symbol = MaterialSymbols.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringRes(Res.string.relay_group_location_add),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringRes(Res.string.relay_group_location_add_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                symbol = MaterialSymbols.AutoMirrored.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Filled-state card: a themed map thumbnail + the resolved place name and geohash. */
@Composable
private fun SelectedLocationCard(
    geohash: String,
    onEdit: () -> Unit,
    onClear: () -> Unit,
) {
    val decoded = remember(geohash) { GeoHash.decode(geohash) }

    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        if (decoded != null) {
            LocationPreviewMap(
                latitude = decoded.centerLat,
                longitude = decoded.centerLon,
                zoom = 12.0,
                aspectRatio = 2.4f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f)) {
                LoadCityName(geohashStr = geohash) { cityName ->
                    Text(
                        text = cityName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                    )
                }
                Text(
                    text = "#$geohash",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClear) {
                Icon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = stringRes(Res.string.relay_group_location_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun LabeledSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
