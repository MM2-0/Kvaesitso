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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.attestation
import com.vitorpamplona.amethyst.commons.resources.attestation_invalid
import com.vitorpamplona.amethyst.commons.resources.attestation_request
import com.vitorpamplona.amethyst.commons.resources.attestation_requests_attestation_to
import com.vitorpamplona.amethyst.commons.resources.attestation_status_revoked
import com.vitorpamplona.amethyst.commons.resources.attestation_status_verifying
import com.vitorpamplona.amethyst.commons.resources.attestation_valid
import com.vitorpamplona.amethyst.commons.resources.attestation_valid_from
import com.vitorpamplona.amethyst.commons.resources.attestation_valid_to
import com.vitorpamplona.amethyst.commons.resources.attestor_proficiency
import com.vitorpamplona.amethyst.commons.resources.attestor_recommendation
import com.vitorpamplona.amethyst.commons.resources.attestor_recommendation_for_kinds
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_title
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.UserCompose
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.KindChip
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.experimental.attestations.attestation.tags.AttestationStatus
import com.vitorpamplona.quartz.experimental.attestations.proficiency.AttestorProficiencyEvent
import com.vitorpamplona.quartz.experimental.attestations.recommendation.AttestorRecommendationEvent
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent

@Preview
@Composable
fun RenderAttestationPreview() {
    val event =
        AttestationEvent(
            id = "f8e05e4fa964d9dfbeb339ae0450d0b3424aaf8f6083d95f38c780335c3dbd56",
            pubKey = "c4f5e7a75a8ce3683d529cff06368439c529e5243c6b125ba68789198856cac7",
            createdAt = 1773941524,
            content = "This is Frank's new npub.",
            sig = "61dde5dc5738bfa637aa04451eec63d45f649902a0772abbaf55711aad5b7ce376bc7712c937f9ffc504655c4fd7e39f78139dc3f1a90953d150b43f3e2428fb",
            tags =
                arrayOf(
                    arrayOf("d", "af5aa898:fe108febb997:1773941524"),
                    arrayOf("e", "fe108febb99796c4091775e00aa1fc3ffc489ad22fdf1f8c559b2472815c09c7"),
                    arrayOf("s", "valid"),
                    arrayOf("client", "attestr.xyz"),
                ),
        )

    LocalCache.justConsume(event, null, true)
    val note = LocalCache.getOrCreateNote(event.id)

    ThemeComparisonColumn(
        toPreview = {
            RenderAttestation(
                baseNote = note,
                quotesLeft = 3,
                backgroundColor = remember { mutableStateOf(Color.Transparent) },
                accountViewModel = mockAccountViewModel(),
                nav = EmptyNav(),
            )
        },
    )
}

@Composable
fun RenderAttestation(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by baseNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val noteEvent = noteState.note.event as? AttestationEvent ?: return

    RenderAttestation(
        baseNote,
        noteEvent,
        quotesLeft,
        backgroundColor,
        accountViewModel,
        nav,
    )
}

@Composable
fun RenderAttestation(
    note: Note,
    noteEvent: AttestationEvent,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val status = remember(noteEvent) { noteEvent.status() }
    val validFrom = remember(noteEvent) { noteEvent.validFrom() }
    val validTo = remember(noteEvent) { noteEvent.validTo() }
    val content = remember(noteEvent) { noteEvent.content.ifBlank { null } }

    val statusColor = remember(status) { attestationColor(status) }
    val statusIcon = remember(status) { attestationIcon(status) }
    val statusLabel = attestationStatusLabel(status)

    val aboutAddress = remember(noteEvent) { noteEvent.assertionAddress() }
    val aboutEvent = remember(noteEvent) { noteEvent.assertionEventId() }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .background(statusColor.copy(alpha = 0.06f))
                .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                symbol = statusIcon,
                contentDescription = stringRes(Res.string.attestation),
                tint = statusColor,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = statusColor,
            )
        }

        if (validFrom != null || validTo != null) {
            Spacer(modifier = DoubleVertSpacer)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                validFrom?.let {
                    Text(
                        text = stringRes(Res.string.attestation_valid_from, formatDateTime(it * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.NONE)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                validTo?.let {
                    Text(
                        text = stringRes(Res.string.attestation_valid_to, formatDateTime(it * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.NONE)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (quotesLeft > 0) {
            if (aboutAddress != null) {
                LoadAddressableNote(aboutAddress) {
                    if (it != null) {
                        Spacer(modifier = DoubleVertSpacer)
                        NoteCompose(
                            baseNote = it,
                            modifier = MaterialTheme.colorScheme.replyModifier,
                            isQuotedNote = true,
                            unPackReply = ReplyRenderType.NONE,
                            makeItShort = true,
                            quotesLeft = quotesLeft - 1,
                            parentBackgroundColor = backgroundColor,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            } else if (aboutEvent != null) {
                LoadNote(aboutEvent) {
                    if (it != null) {
                        Spacer(modifier = DoubleVertSpacer)
                        NoteCompose(
                            baseNote = it,
                            modifier = MaterialTheme.colorScheme.replyModifier,
                            isQuotedNote = true,
                            unPackReply = ReplyRenderType.NONE,
                            makeItShort = true,
                            quotesLeft = quotesLeft - 1,
                            parentBackgroundColor = backgroundColor,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            }
        }

        content?.let {
            Spacer(modifier = DoubleVertSpacer)
            TranslatableRichTextViewer(
                content = it,
                canPreview = true,
                quotesLeft = quotesLeft,
                modifier = Modifier.fillMaxWidth(),
                tags = remember(note) { note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList },
                backgroundColor = backgroundColor,
                id = note.idHex,
                callbackUri = note.toNostrUri(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun RenderAttestationRequest(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by note
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val noteEvent = noteState.note.event as? AttestationRequestEvent ?: return

    val content = remember(noteEvent) { noteEvent.content.ifBlank { null } }

    val aboutAddress = remember(noteEvent) { noteEvent.assertionAddress() }
    val aboutEvent = remember(noteEvent) { noteEvent.assertionEventId() }
    val aboutPubkey = remember(noteEvent) { noteEvent.attestorPubKeys().firstOrNull() }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp),
                ).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.AutoMirrored.Send,
                contentDescription = stringRes(Res.string.attestation_request),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringRes(Res.string.attestation_request),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        content?.let {
            Spacer(modifier = DoubleVertSpacer)
            TranslatableRichTextViewer(
                content = it,
                canPreview = true,
                quotesLeft = quotesLeft,
                modifier = Modifier.fillMaxWidth(),
                tags = remember(note) { note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList },
                backgroundColor = backgroundColor,
                id = note.idHex,
                callbackUri = note.toNostrUri(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }

    if (quotesLeft > 0) {
        when {
            aboutAddress != null -> {
                LoadAddressableNote(aboutAddress) {
                    if (it != null) {
                        Spacer(modifier = DoubleVertSpacer)
                        Text(
                            text = stringRes(Res.string.attestation_requests_attestation_to),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        NoteCompose(
                            baseNote = it,
                            modifier = MaterialTheme.colorScheme.replyModifier,
                            isQuotedNote = true,
                            unPackReply = ReplyRenderType.NONE,
                            makeItShort = true,
                            quotesLeft = quotesLeft - 1,
                            parentBackgroundColor = backgroundColor,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            }

            aboutEvent != null -> {
                LoadNote(aboutEvent) {
                    if (it != null) {
                        Spacer(modifier = DoubleVertSpacer)
                        Text(
                            text = stringRes(Res.string.attestation_requests_attestation_to),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        NoteCompose(
                            baseNote = it,
                            modifier = MaterialTheme.colorScheme.replyModifier,
                            isQuotedNote = true,
                            unPackReply = ReplyRenderType.NONE,
                            makeItShort = true,
                            quotesLeft = quotesLeft - 1,
                            parentBackgroundColor = backgroundColor,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            }

            aboutPubkey != null -> {
                LoadUser(aboutPubkey) {
                    if (it != null) {
                        Spacer(modifier = DoubleVertSpacer)
                        Text(
                            text = stringRes(Res.string.attestation_requests_attestation_to),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        UserCompose(it, accountViewModel = accountViewModel, nav = nav)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderAttestorRecommendation(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by note
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val noteEvent = noteState.note.event as? AttestorRecommendationEvent ?: return

    val kinds = remember(noteEvent) { noteEvent.kinds() }
    val description = remember(noteEvent) { noteEvent.description() }
    val aboutPubKey = remember(noteEvent) { noteEvent.dTag() }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp),
                ).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.06f))
                .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.Recommend,
                contentDescription = stringRes(Res.string.attestor_recommendation),
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringRes(Res.string.attestor_recommendation),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        LoadUser(aboutPubKey) {
            if (it != null) {
                Spacer(modifier = DoubleVertSpacer)
                UserCompose(it, accountViewModel = accountViewModel, nav = nav)
            }
        }

        if (kinds.isNotEmpty()) {
            Spacer(modifier = DoubleVertSpacer)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringRes(Res.string.attestor_recommendation_for_kinds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Kinds
                kinds.forEach { kind ->
                    KindChip(kind)
                }
            }
        }

        description?.let {
            Spacer(modifier = DoubleVertSpacer)
            TranslatableRichTextViewer(
                content = it,
                canPreview = true,
                quotesLeft = quotesLeft,
                modifier = Modifier.fillMaxWidth(),
                tags = remember(note) { note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList },
                backgroundColor = backgroundColor,
                id = note.idHex,
                callbackUri = note.toNostrUri(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderAttestorProficiency(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by note
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val noteEvent = noteState.note.event as? AttestorProficiencyEvent ?: return

    val kinds = remember(noteEvent) { noteEvent.kinds() }
    val description = remember(noteEvent) { noteEvent.description() }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp),
                ).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f))
                .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.Star,
                contentDescription = stringRes(Res.string.attestor_proficiency),
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringRes(Res.string.attestor_proficiency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        if (kinds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                kinds.forEach { kind ->
                    Text(
                        text = stringRes(Res.string.unknown_kind_title, kind),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        description?.let {
            Spacer(modifier = Modifier.height(8.dp))
            TranslatableRichTextViewer(
                content = it,
                canPreview = true,
                quotesLeft = quotesLeft,
                modifier = Modifier.fillMaxWidth(),
                tags = remember(note) { note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList },
                backgroundColor = backgroundColor,
                id = note.idHex,
                callbackUri = note.toNostrUri(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

private fun attestationColor(status: AttestationStatus?): Color =
    when {
        status == AttestationStatus.INVALID -> Color(0xFFB71C1C)
        status == AttestationStatus.VALID -> Color(0xFF2E7D32)
        status == AttestationStatus.REVOKED -> Color(0xFFB21CB7)
        status == AttestationStatus.VERIFYING -> Color(0xFF173CF5)
        else -> Color(0xFF757575)
    }

private fun attestationIcon(status: AttestationStatus?): MaterialSymbol =
    when {
        status == AttestationStatus.INVALID -> MaterialSymbols.Close
        status == AttestationStatus.VALID -> MaterialSymbols.CheckCircle
        status == AttestationStatus.REVOKED -> MaterialSymbols.RemoveDone
        status == AttestationStatus.VERIFYING -> MaterialSymbols.HourglassTop
        else -> MaterialSymbols.ErrorOutline
    }

@Composable
private fun attestationStatusLabel(status: AttestationStatus?): String =
    when {
        status == AttestationStatus.INVALID -> stringRes(Res.string.attestation_invalid)
        status == AttestationStatus.VALID -> stringRes(Res.string.attestation_valid)
        status == AttestationStatus.REVOKED -> stringRes(Res.string.attestation_status_revoked)
        status == AttestationStatus.VERIFYING -> stringRes(Res.string.attestation_status_verifying)
        else -> stringRes(Res.string.attestation)
    }
