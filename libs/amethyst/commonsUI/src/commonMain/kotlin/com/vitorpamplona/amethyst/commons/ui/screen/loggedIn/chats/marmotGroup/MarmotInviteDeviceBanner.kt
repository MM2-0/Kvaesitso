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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.LatestKeyPackageOwner
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.marmot_invite_device_banner
import com.vitorpamplona.amethyst.commons.resources.marmot_invite_device_banner_dismiss
import com.vitorpamplona.amethyst.commons.resources.marmot_invite_device_failure
import com.vitorpamplona.amethyst.commons.resources.marmot_invite_device_publish
import com.vitorpamplona.amethyst.commons.resources.marmot_invite_device_rejected
import com.vitorpamplona.amethyst.commons.resources.marmot_invite_device_success
import com.vitorpamplona.amethyst.commons.ui.components.rememberLongNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * How long a passive owner check may reuse its previous answer. What it reports
 * only changes when another device publishes, which no user does often.
 */
private const val OWNER_CHECK_MAX_AGE_SECONDS = 15L * 60L

/**
 * Warns, on the Marmot groups screen, that another install of this account is
 * the one receiving its invites.
 *
 * This is the symptom's own screen. A user whose invites are landing on a
 * second device sees an empty group list and no new requests — exactly what
 * "nobody has invited me" looks like — so without a banner here the failure is
 * indistinguishable from nothing happening, and the diagnosis sits behind a
 * settings row nobody has a reason to open.
 *
 * Publishing stays a tap, never automatic: an inviter takes the newest
 * KeyPackage, so a banner that republished itself on sight would deadlock the
 * two installs against each other, each device's correction being the other's
 * trigger.
 */
@Composable
fun MarmotInviteDeviceBanner(
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    var owner by remember { mutableStateOf<LatestKeyPackageOwner?>(null) }
    // Saveable: a rotation is not a decision to un-dismiss a warning the user
    // has already read and waved away.
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val shortNotice = rememberShortNotice()
    val longNotice = rememberLongNotice()
    // A read-only login cannot publish at all, so the only action this banner
    // offers is impossible for it. Better to say nothing than to offer a button
    // that silently does nothing.
    val canPublish = accountViewModel.canPublish()

    LaunchedEffect(Unit) {
        if (!canPublish) return@LaunchedEffect
        try {
            owner =
                withContext(Dispatchers.IO) {
                    // Passive check: reuse a recent answer rather than fanning a
                    // REQ across the whole write set on every entry to this screen.
                    accountViewModel.latestKeyPackageOwner(maxAgeSeconds = OWNER_CHECK_MAX_AGE_SECONDS)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A relay we cannot reach is not evidence of anything, so it stays
            // silent rather than accusing a device that may well be this one.
            owner = null
        }
    }

    AnimatedVisibility(visible = owner == LatestKeyPackageOwner.OTHER_DEVICE && !dismissed) {
        // Message on its own line, actions under it. All three in one Row fit
        // only on a wide screen with default font scale; at phone width with
        // large fonts the label and the button fought for the same space.
        //
        // `surfaceVariant`, not `errorContainer`: this is an explanation, and
        // most users will never see it. The error palette would make it the
        // loudest thing on a screen whose actual subject is the group list.
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    symbol = MaterialSymbols.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringRes(Res.string.marmot_invite_device_banner),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp).weight(1f),
                )
                IconButton(onClick = { dismissed = true }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        symbol = MaterialSymbols.Close,
                        contentDescription = stringRes(Res.string.marmot_invite_device_banner_dismiss),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        // Hidden optimistically, but only *stays* hidden once a
                        // relay has accepted: `republishKeyPackage` waits for the
                        // OK, so a rejection, a read-only account or an empty relay
                        // set come back false instead of being reported as the
                        // success they are not.
                        owner = LatestKeyPackageOwner.THIS_DEVICE
                        scope.launch(Dispatchers.IO) {
                            val successMessage = loadStringRes(Res.string.marmot_invite_device_success)
                            val rejectedMessage = loadStringRes(Res.string.marmot_invite_device_rejected)
                            try {
                                val accepted = accountViewModel.republishKeyPackage()
                                launch(Dispatchers.Main) {
                                    if (accepted) {
                                        shortNotice.show(successMessage)
                                    } else {
                                        longNotice.show(rejectedMessage)
                                        owner = LatestKeyPackageOwner.OTHER_DEVICE
                                    }
                                }
                            } catch (e: CancellationException) {
                                // Leaving the screen mid-publish is not a failure to
                                // report, and swallowing it here would break the
                                // scope's cancellation as well as pop a toast for a
                                // banner that is already gone.
                                throw e
                            } catch (e: Exception) {
                                val failureMessage =
                                    loadStringRes(Res.string.marmot_invite_device_failure, e.message ?: "")
                                launch(Dispatchers.Main) {
                                    longNotice.show(failureMessage)
                                    // The warning was right after all, so put it back.
                                    owner = LatestKeyPackageOwner.OTHER_DEVICE
                                }
                            }
                        }
                    },
                ) {
                    Text(
                        text = stringRes(Res.string.marmot_invite_device_publish),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
