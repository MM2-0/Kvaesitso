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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.log

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.geocaches.log.LogGeocacheFindViewModel
import com.vitorpamplona.amethyst.commons.geocaches.log.ScanOutcome
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_log_add_photo
import com.vitorpamplona.amethyst.commons.resources.geocache_log_cache_label
import com.vitorpamplona.amethyst.commons.resources.geocache_log_mission_answer
import com.vitorpamplona.amethyst.commons.resources.geocache_log_no_location_attached
import com.vitorpamplona.amethyst.commons.resources.geocache_log_placeholder
import com.vitorpamplona.amethyst.commons.resources.geocache_log_proof_bad_code
import com.vitorpamplona.amethyst.commons.resources.geocache_log_proof_explain
import com.vitorpamplona.amethyst.commons.resources.geocache_log_proof_optional
import com.vitorpamplona.amethyst.commons.resources.geocache_log_proof_section
import com.vitorpamplona.amethyst.commons.resources.geocache_log_proof_verified
import com.vitorpamplona.amethyst.commons.resources.geocache_log_proof_wrong_cache
import com.vitorpamplona.amethyst.commons.resources.geocache_log_scan_again
import com.vitorpamplona.amethyst.commons.resources.geocache_log_scan_code
import com.vitorpamplona.amethyst.commons.resources.geocache_log_your_log
import com.vitorpamplona.amethyst.commons.resources.geocache_unnamed
import com.vitorpamplona.amethyst.commons.resources.route_log_geocache_find
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.GallerySelectSingle
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.commons.ui.note.rememberGeocachePalette
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.datasource.GeocachesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.SimpleQrCodeScanner
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import kotlinx.coroutines.launch

/**
 * The kind 7516 composer, and the only screen allowed to scan a cache's verification secret.
 *
 * That restriction matters: the app's general QR scanner maps what it reads to a navigation
 * route, which is right for a cache's published `naddr` and catastrophic for a private key. Here
 * the scanner is the raw-string variant, the result never becomes a route, and the key is
 * consumed inside the view model and dropped.
 *
 * The cache's name sits fixed at the top rather than being editable, so there is no way to log
 * the wrong cache, and the screen says out loud that the finder's own location is not attached —
 * the one privacy question a geocaching client owes an answer to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogGeocacheFindScreen(
    kind: Int,
    pubKeyHex: String,
    dTag: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    GeocachesFilterAssemblerSubscription(accountViewModel)

    val address = remember(kind, pubKeyHex, dTag) { Address(kind, pubKeyHex, dTag) }
    val model: LogGeocacheFindViewModel = rememberViewModel(key = "LogGeocacheFind-${address.toValue()}") { LogGeocacheFindViewModel() }
    val scope = rememberCoroutineScope()

    remember(address) {
        model.init(accountViewModel.account, address)
        true
    }

    val listing = model.listing()
    var scanning by remember { mutableStateOf(false) }

    var pickingPhoto by rememberSaveable { mutableStateOf(false) }
    if (pickingPhoto) {
        GallerySelectSingle(imagesOnly = true) { media ->
            pickingPhoto = false
            if (media != null) {
                scope.launch { model.uploadImage(media.uri, media.mimeType, accountViewModel.mediaUploader) }
            }
        }
    }

    Scaffold(
        topBar = {
            PostingTopBar(
                titleRes = Res.string.route_log_geocache_find,
                isActive = { model.isValid() && !model.isPublishing.value },
                onCancel = { nav.popBack() },
                onPost = { scope.launch { if (model.publish()) nav.popBack() } },
            )
        },
    ) { pad ->
        Column(
            modifier =
                Modifier
                    .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())
                    .consumeWindowInsets(pad)
                    .imePaddingSafe()
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = stringRes(Res.string.geocache_log_cache_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = listing?.cacheName()?.trim()?.ifBlank { null } ?: stringRes(Res.string.geocache_unnamed),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            SpellCheckedOutlinedTextField(
                value = model.message.value,
                onValueChange = { model.message.value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringRes(Res.string.geocache_log_your_log)) },
                placeholder = { Text(stringRes(Res.string.geocache_log_placeholder)) },
                minLines = 4,
            )

            if (listing?.hasMission() == true) {
                OutlinedTextField(
                    value = model.missionAnswer.value,
                    onValueChange = { model.missionAnswer.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringRes(Res.string.geocache_log_mission_answer)) },
                    minLines = 2,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !model.isUploading.value,
                    onClick = {
                        pickingPhoto = true
                    },
                ) {
                    if (model.isUploading.value) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringRes(Res.string.geocache_log_add_photo))
                    }
                }

                if (model.images.isNotEmpty()) {
                    Text(
                        text = "📷 ${model.images.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = stringRes(Res.string.geocache_log_proof_section),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (listing?.requiresVerification() != true) {
                Text(
                    text = stringRes(Res.string.geocache_log_proof_optional),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                when (model.scanOutcome.value) {
                    ScanOutcome.VERIFIED ->
                        ProofStamp(
                            text = stringRes(Res.string.geocache_log_proof_verified),
                            color = rememberGeocachePalette().proven,
                            glyph = "🔐",
                            celebrate = true,
                        )
                    ScanOutcome.NOT_A_KEY ->
                        ProofStamp(
                            text = stringRes(Res.string.geocache_log_proof_bad_code),
                            color = MaterialTheme.colorScheme.error,
                            glyph = "⚠️",
                            celebrate = false,
                        )
                    ScanOutcome.WRONG_CACHE ->
                        ProofStamp(
                            text = stringRes(Res.string.geocache_log_proof_wrong_cache),
                            color = MaterialTheme.colorScheme.error,
                            glyph = "⚠️",
                            celebrate = false,
                        )
                    ScanOutcome.NONE -> Unit
                }

                OutlinedButton(onClick = { scanning = true }) {
                    Icon(
                        symbol = MaterialSymbols.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        if (model.hasProof()) {
                            stringRes(Res.string.geocache_log_scan_again)
                        } else {
                            stringRes(Res.string.geocache_log_scan_code)
                        },
                    )
                }

                Text(
                    text = stringRes(Res.string.geocache_log_proof_explain),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (scanning) {
                SimpleQrCodeScanner { scanned ->
                    scanning = false
                    scope.launch { model.consumeScannedCode(scanned) }
                }
            }

            Text(
                text = stringRes(Res.string.geocache_log_no_location_attached),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The seal a verified find gets, and the one moment in this feature worth a flourish.
 *
 * Proving you physically stood at a cache is the thing no other NIP-CC client can do, and it
 * had been rendering as a tinted box with a sentence in it. It now reads as a stamp: the padlock
 * at display size, a border rather than only a wash so it sits *on* the page instead of behind
 * it, and a scale-in when it lands, because the scan is the one interaction here that should
 * feel like something happened.
 *
 * Failures use the same shape deliberately — same frame, error colour, no animation. A shape
 * that only ever appears on success trains people to stop reading it.
 */
@Composable
private fun ProofStamp(
    text: String,
    color: Color,
    glyph: String,
    celebrate: Boolean,
) {
    val scale = remember { Animatable(if (celebrate) 0.88f else 1f) }

    LaunchedEffect(celebrate, text) {
        if (celebrate) {
            scale.snapTo(0.88f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }.clip(RoundedCornerShape(10.dp))
                .border(1.5.dp, color.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.10f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = glyph, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
