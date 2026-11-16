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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.concord_create_about
import com.vitorpamplona.amethyst.commons.resources.concord_create_icon_hint
import com.vitorpamplona.amethyst.commons.resources.concord_create_name
import com.vitorpamplona.amethyst.commons.resources.concord_create_title
import com.vitorpamplona.amethyst.commons.resources.concord_edit_banner_hint
import com.vitorpamplona.amethyst.commons.resources.concord_name_budget
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_media_no_details
import com.vitorpamplona.amethyst.commons.resources.remove
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.GallerySelectSingle
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberLongNotice
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.RenderRelayIcon
import com.vitorpamplona.amethyst.commons.ui.platform.rememberConcordImageModel
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.RelayUrlEditField
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.MediumRelayIconModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordLimits
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The shared metadata form for creating and editing a Concord community — a large circular icon
 * hero at the top (tap to pick an image, which is AES-256-GCM-encrypted and uploaded to Blossom as
 * a CORD-02 §6 [ImagePointer], see [ConcordImageUploader]), then the name and description fields.
 * Mirrors the NIP-29 `GroupImagePicker` hero + `GroupMetadataFields` layout so the two features feel
 * consistent. Callers own the state and add the surrounding scaffold, relays section (create only),
 * and the create/save action.
 */
@Composable
fun ConcordMetadataFields(
    name: MutableState<String>,
    about: MutableState<String>,
    icon: MutableState<ImagePointer?>,
    robotSeed: String,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    banner: MutableState<ImagePointer?>? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        banner?.let { ConcordBannerHero(banner = it, accountViewModel = accountViewModel) }

        ConcordIconHero(
            robotSeed = robotSeed,
            icon = icon,
            displayName = name.value,
            accountViewModel = accountViewModel,
        )

        OutlinedTextField(
            value = name.value,
            onValueChange = { name.value = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringRes(Res.string.concord_create_name)) },
            isError = !concordNameFits(name.value),
            supportingText = { ConcordNameBudget(name.value) },
        )
        SpellCheckedOutlinedTextField(
            value = about.value,
            onValueChange = { about.value = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
            label = { Text(stringRes(Res.string.concord_create_about)) },
        )
    }
}

/**
 * Whether [name] fits the protocol-wide name cap (CORD-02 §6: 64 UTF-8 bytes, for the Community,
 * Channels and Roles). Every reader drops an edition past it, so a form must not submit one.
 */
fun concordNameFits(name: String): Boolean = ConcordLimits.nameFits(name.trim())

/** A text field's `used / 64` byte counter for a Concord name, in the error color once over the cap. */
@Composable
fun ConcordNameBudget(name: String) {
    Text(
        stringRes(Res.string.concord_name_budget, ConcordLimits.utf8Size(name.trim()), ConcordLimits.NAME_MAX_BYTES),
        color = if (concordNameFits(name)) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
    )
}

/**
 * The circular community-icon hero: shows the current (decrypted) icon over a stable robohash
 * placeholder, and on tap opens the photo picker → encrypts + uploads the chosen image and updates
 * [icon] to the resulting encrypted pointer. A spinner covers the hero while the upload is in flight.
 */
@Composable
private fun ConcordIconHero(
    robotSeed: String,
    icon: MutableState<ImagePointer?>,
    displayName: String,
    accountViewModel: AccountViewModel,
) {
    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    val notice = rememberLongNotice()
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    val iconModel = rememberConcordImageModel(icon.value, accountViewModel)

    var picking by rememberSaveable { mutableStateOf(false) }
    if (picking) {
        GallerySelectSingle(imagesOnly = true) { media ->
            picking = false
            if (media == null) return@GallerySelectSingle
            uploading = true
            scope.launch {
                try {
                    icon.value = ConcordImageUploader(accountViewModel.account).uploadEncrypted(media, accountViewModel.mediaUploader)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("ConcordImageUpload", "Community icon upload failed", e)
                    notice.show(e.message?.takeIf { it.isNotBlank() } ?: loadStringRes(Res.string.failed_to_upload_media_no_details))
                } finally {
                    uploading = false
                }
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !uploading) { picking = true },
            contentAlignment = Alignment.Center,
        ) {
            RobohashFallbackAsyncImage(
                robot = robotSeed,
                model = iconModel,
                contentDescription = displayName.ifBlank { stringRes(Res.string.concord_create_title) },
                modifier = Modifier.size(104.dp).clip(CircleShape),
                loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                loadRobohash = LocalDisplaySettings.current.loadRobohash,
                autoPlayGif = autoPlayGif,
            )
            if (uploading) CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
        Text(
            text = stringRes(Res.string.concord_create_icon_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .padding(top = 8.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !uploading) { picking = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * A wide community-banner hero (a 3:1 header image): shows the current decrypted banner, and on tap
 * opens the photo picker → AES-256-GCM-encrypts + uploads the image and updates [banner] to the
 * resulting CORD-02 §6 encrypted pointer. Tapping when a banner is set replaces it; a small remove
 * button clears it. A spinner covers the hero while the upload is in flight.
 */
@Composable
private fun ConcordBannerHero(
    banner: MutableState<ImagePointer?>,
    accountViewModel: AccountViewModel,
) {
    val notice = rememberLongNotice()
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    val bannerModel = rememberConcordImageModel(banner.value, accountViewModel)

    var picking by rememberSaveable { mutableStateOf(false) }
    if (picking) {
        GallerySelectSingle(imagesOnly = true) { media ->
            picking = false
            if (media == null) return@GallerySelectSingle
            uploading = true
            scope.launch {
                try {
                    banner.value = ConcordImageUploader(accountViewModel.account).uploadEncrypted(media, accountViewModel.mediaUploader)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("ConcordImageUpload", "Community banner upload failed", e)
                    notice.show(e.message?.takeIf { it.isNotBlank() } ?: loadStringRes(Res.string.failed_to_upload_media_no_details))
                } finally {
                    uploading = false
                }
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = !uploading) { picking = true },
        contentAlignment = Alignment.Center,
    ) {
        if (bannerModel != null) {
            AsyncImage(
                model = bannerModel,
                contentDescription = stringRes(Res.string.concord_edit_banner_hint),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(3f),
            )
        }
        if (uploading) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        } else if (bannerModel == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolIcon(
                    symbol = MaterialSymbols.AddPhotoAlternate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringRes(Res.string.concord_edit_banner_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        if (bannerModel != null && !uploading) {
            IconButton(
                onClick = { banner.value = null },
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                SymbolIcon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = stringRes(Res.string.remove),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * The community's bootstrap-relay list editor, shared by the create and edit screens. Each relay is
 * shown the same way the Relay Settings screens show them — the relay's NIP-11 favicon, its
 * advertised name, and its host — and tapping the row opens the full relay-info page ([Route.RelayInfo]),
 * so a community relay is a first-class, inspectable relay rather than a bare URL string. A trailing
 * ✕ removes it; the [RelayUrlEditField] below adds one. State is owned by the caller.
 */
@Composable
fun ConcordRelayListEditor(
    relays: List<NormalizedRelayUrl>,
    onRemove: (NormalizedRelayUrl) -> Unit,
    onAdd: (NormalizedRelayUrl) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    relays.forEach { relay ->
        ConcordRelayRow(relay, { onRemove(relay) }, accountViewModel, nav)
    }
    RelayUrlEditField(
        onNewRelay = onAdd,
        modifier = Modifier.fillMaxWidth(),
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConcordRelayRow(
    relay: NormalizedRelayUrl,
    onRemove: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // The NIP-11 relay-info doc (icon + display name), fetched + cached exactly like the settings rows.
    val relayInfo by loadRelayInfo(relay)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RenderRelayIcon(
            displayUrl = relayInfo.id ?: relay.displayUrl(),
            iconUrl = relayInfo.icon,
            loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
            loadRobohash = LocalDisplaySettings.current.loadRobohash,
            pingInMs = 0,
            iconModifier = MediumRelayIconModifier,
        )
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = { nav.nav(Route.RelayInfo(relay.url)) },
                    onLongClick = { scope.launch { clipboard.setText(relay.url) } },
                ),
        ) {
            relayInfo.name?.takeIf { it.isNotBlank() }?.let { name ->
                Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = relay.displayUrl(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        IconButton(onClick = onRemove) {
            SymbolIcon(symbol = MaterialSymbols.Close, contentDescription = stringRes(Res.string.remove))
        }
    }
}
