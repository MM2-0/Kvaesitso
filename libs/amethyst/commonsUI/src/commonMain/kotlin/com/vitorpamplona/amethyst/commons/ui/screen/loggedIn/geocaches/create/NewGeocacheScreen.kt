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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.create

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.geocaches.create.NewGeocacheViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_log_add_photo
import com.vitorpamplona.amethyst.commons.resources.geocache_new_description
import com.vitorpamplona.amethyst.commons.resources.geocache_new_difficulty
import com.vitorpamplona.amethyst.commons.resources.geocache_new_hint
import com.vitorpamplona.amethyst.commons.resources.geocache_new_hint_preview
import com.vitorpamplona.amethyst.commons.resources.geocache_new_hint_scramble
import com.vitorpamplona.amethyst.commons.resources.geocache_new_ladder
import com.vitorpamplona.amethyst.commons.resources.geocache_new_location_warning
import com.vitorpamplona.amethyst.commons.resources.geocache_new_mission
import com.vitorpamplona.amethyst.commons.resources.geocache_new_modifiers
import com.vitorpamplona.amethyst.commons.resources.geocache_new_name
import com.vitorpamplona.amethyst.commons.resources.geocache_new_pick_location
import com.vitorpamplona.amethyst.commons.resources.geocache_new_precision_warning
import com.vitorpamplona.amethyst.commons.resources.geocache_new_proof_ready
import com.vitorpamplona.amethyst.commons.resources.geocache_new_publish
import com.vitorpamplona.amethyst.commons.resources.geocache_new_require_proof
import com.vitorpamplona.amethyst.commons.resources.geocache_new_section_extras
import com.vitorpamplona.amethyst.commons.resources.geocache_new_section_proof
import com.vitorpamplona.amethyst.commons.resources.geocache_new_section_what
import com.vitorpamplona.amethyst.commons.resources.geocache_new_section_where
import com.vitorpamplona.amethyst.commons.resources.geocache_new_terrain
import com.vitorpamplona.amethyst.commons.resources.geocache_owner_qr_warning
import com.vitorpamplona.amethyst.commons.resources.geocache_photo_remove
import com.vitorpamplona.amethyst.commons.resources.route_edit_geocache
import com.vitorpamplona.amethyst.commons.resources.route_new_geocache
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.GallerySelectSingle
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ActionTopBar
import com.vitorpamplona.amethyst.commons.ui.note.geocacheEmoji
import com.vitorpamplona.amethyst.commons.ui.note.geocacheLabelRes
import com.vitorpamplona.amethyst.commons.ui.note.platform.LocationPreviewMap
import com.vitorpamplona.amethyst.commons.ui.platform.GeohashLocationPickerDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.tags.geohash.toGeoHash
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheGeohash
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheSize
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheType
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.TypeModifier
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The cache composer, in five sections, and the screen with the most at stake.
 *
 * Publishing a cache means publishing a permanent, public, five-metre-accurate location of a
 * real place, signed by the user's own key. The location warning is therefore not a footnote: it
 * sits directly under the picker, un-dismissable, and says that the event cannot be recalled,
 * because that is the sentence an author needs before they tap Publish rather than after.
 *
 * The hint preview exists for the same reason at a smaller scale. Amethyst writes ROT13 — the
 * reference client's convention — and an author who cannot see the scrambled form has no way to
 * know whether their hint survives it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewGeocacheScreen(
    nav: INav,
    accountViewModel: AccountViewModel,
    prefillGeohash: String? = null,
    editKind: Int? = null,
    editPubKeyHex: String? = null,
    editDTag: String? = null,
) {
    val model: NewGeocacheViewModel = rememberViewModel { NewGeocacheViewModel() }
    val scope = rememberCoroutineScope()

    remember(editKind, editPubKeyHex, editDTag, prefillGeohash) {
        model.init(accountViewModel.account)
        if (editKind != null && editPubKeyHex != null && editDTag != null) {
            model.loadForEdit(editKind, editPubKeyHex, editDTag)
        } else {
            model.prefillGeohash(prefillGeohash)
        }
        true
    }

    var pickingLocation by remember { mutableStateOf(false) }

    var pickingPhoto by rememberSaveable { mutableStateOf(false) }
    if (pickingPhoto) {
        GallerySelectSingle(imagesOnly = true) { media ->
            pickingPhoto = false
            if (media != null) scope.launch { model.uploadImage(media.uri, media.mimeType, accountViewModel.mediaUploader) }
        }
    }

    Scaffold(
        topBar = {
            ActionTopBar(
                postRes = if (model.isEditing) Res.string.save else Res.string.geocache_new_publish,
                titleRes = if (model.isEditing) Res.string.route_edit_geocache else Res.string.route_new_geocache,
                isActive = { model.isValid() && !model.isPublishing.value },
                onCancel = { nav.popBack() },
                onPost = { scope.launch { if (model.publish()) nav.popBack() } },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())
                .consumeWindowInsets(pad)
                .imePaddingSafe()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ComposerSection(stringRes(Res.string.geocache_new_section_where), 1)

            val point =
                remember(model.geohash.value) {
                    runCatching {
                        model.geohash.value
                            .takeIf { it.isNotEmpty() }
                            ?.toGeoHash()
                    }.getOrNull()
                }

            if (point != null) {
                LocationPreviewMap(
                    latitude = point.centerLat,
                    longitude = point.centerLon,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                    aspectRatio = 16f / 9f,
                    pinColor = MaterialTheme.colorScheme.primary,
                    pinEmoji = model.type.value.geocacheEmoji(),
                )
            }

            OutlinedButton(onClick = { pickingLocation = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringRes(Res.string.geocache_new_pick_location))
            }

            WarningBox(stringRes(Res.string.geocache_new_location_warning))

            if (model.hasLocation()) {
                Text(
                    text = stringRes(Res.string.geocache_new_ladder, model.geohash.value),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!model.isPreciseEnough()) {
                    Text(
                        // NIP-CC asks for 8 characters, 9 for a micro. Below that a finder is
                        // searching a block rather than a hiding place.
                        text = stringRes(Res.string.geocache_new_precision_warning, GeocacheGeohash.minPublishPrecision(model.size.value)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            ComposerSection(stringRes(Res.string.geocache_new_section_what), 2)

            OutlinedTextField(
                value = model.name.value,
                onValueChange = { model.name.value = it },
                label = { Text(stringRes(Res.string.geocache_new_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            SpellCheckedOutlinedTextField(
                value = model.description.value,
                onValueChange = { model.description.value = it },
                label = { Text(stringRes(Res.string.geocache_new_description)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CacheType.entries.forEach { entry ->
                    FilterChip(
                        selected = model.type.value == entry,
                        onClick = { model.type.value = entry },
                        label = { Text(entry.geocacheEmoji() + "  " + entry.geocacheLabelRes()?.let { stringResource(it) }.orEmpty()) },
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CacheSize.entries.forEach { entry ->
                    FilterChip(
                        selected = model.size.value == entry,
                        onClick = { model.size.value = entry },
                        label = { Text(stringResource(entry.geocacheLabelRes())) },
                    )
                }
            }

            StarRow(stringRes(Res.string.geocache_new_difficulty), model.difficulty.value) { model.difficulty.value = it }
            StarRow(stringRes(Res.string.geocache_new_terrain), model.terrain.value) { model.terrain.value = it }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    enabled = !model.isUploading.value,
                    onClick = { pickingPhoto = true },
                ) {
                    if (model.isUploading.value) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("📷  " + stringRes(Res.string.geocache_log_add_photo))
                    }
                }
            }

            // Thumbnails with a delete affordance. Without one the composer could only ever add,
            // so an owner editing a cache was stuck with a photo they no longer wanted -- and on
            // a listing the photos are the first thing a finder sees.
            if (model.images.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(model.images.toList(), key = { it }) { url ->
                        Box {
                            MyAsyncImage(
                                imageUrl = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                mainImageModifier = Modifier,
                                loadedImageModifier = Modifier.size(88.dp).clip(RoundedCornerShape(10.dp)),
                                accountViewModel = accountViewModel,
                                onLoadingBackground = null,
                                onError = null,
                            )
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                    .clickable { model.removeImage(url) }
                                    .padding(3.dp),
                            ) {
                                Icon(
                                    symbol = MaterialSymbols.Close,
                                    contentDescription = stringRes(Res.string.geocache_photo_remove),
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            ComposerSection(stringRes(Res.string.geocache_new_section_extras), 3)

            SpellCheckedOutlinedTextField(
                value = model.hint.value,
                onValueChange = { model.hint.value = it },
                label = { Text(stringRes(Res.string.geocache_new_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            if (model.hint.value.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringRes(Res.string.geocache_new_hint_scramble),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = model.scrambleHint.value, onCheckedChange = { model.scrambleHint.value = it })
                }
                Text(
                    text = stringRes(Res.string.geocache_new_hint_preview),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = model.hintPreview(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            SpellCheckedOutlinedTextField(
                value = model.mission.value,
                onValueChange = { model.mission.value = it },
                label = { Text(stringRes(Res.string.geocache_new_mission)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Text(
                text = stringRes(Res.string.geocache_new_modifiers),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TypeModifier.entries.forEach { entry ->
                    FilterChip(
                        selected = model.modifiers.contains(entry),
                        onClick = { model.toggleModifier(entry) },
                        label = { Text(entry.code) },
                    )
                }
            }

            ComposerSection(stringRes(Res.string.geocache_new_section_proof), 4)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringRes(Res.string.geocache_new_require_proof),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = model.requireProof.value, onCheckedChange = { model.setRequireProof(it) })
            }

            model.generatedPrivateKey.value?.let { secret ->
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    QrCodeDrawer(secret)
                    Text(
                        text = stringRes(Res.string.geocache_new_proof_ready),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    WarningBox(stringRes(Res.string.geocache_owner_qr_warning))
                }
            }
        }
    }

    if (pickingLocation) {
        GeohashLocationPickerDialog(
            initialGeohash = model.geohash.value.ifBlank { null },
            // A cache is a point to walk up to, not an area: always the finest tag NIP-CC asks for.
            precision = GeocacheGeohash.MAX_TAGGED,
            onDismiss = { pickingLocation = false },
            onConfirm = {
                model.geohash.value = it
                pickingLocation = false
            },
        )
    }
}

/**
 * A numbered step header.
 *
 * The numbers are not decoration: hiding a cache genuinely is a sequence — you cannot judge
 * whether a geohash is precise enough until you know the size, and the verification QR only
 * makes sense once there is a cache to put it in. Numbering says "there are five of these and
 * you are on the second", which a bare bold heading does not.
 */
@Composable
private fun ComposerSection(
    title: String,
    step: Int,
) {
    Row(
        modifier = Modifier.padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "$step",
            modifier =
                Modifier
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .wrapContentSize(Alignment.Center),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun WarningBox(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(10.dp),
        )
    }
}

/** A 1–5 rating as five taps, which is how every geocaching client on earth shows D and T. */
@Composable
private fun StarRow(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Row {
            (1..5).forEach { star ->
                Text(
                    text = if (star <= value) "★" else "☆",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onChange(star) }.padding(horizontal = 2.dp),
                )
            }
        }
    }
}
