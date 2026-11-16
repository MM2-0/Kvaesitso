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
package com.vitorpamplona.amethyst.commons.ui.connectedApps.consent

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.connectedApps.consent.PendingConsent
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConnectInfo
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConsentInfo
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppIcon
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.napplet_connect_block
import com.vitorpamplona.amethyst.commons.resources.napplet_connect_button
import com.vitorpamplona.amethyst.commons.resources.napplet_connect_how_handle
import com.vitorpamplona.amethyst.commons.resources.napplet_connect_subtitle
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_allow_always
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_fewer_options
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_hide_event
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_more_options
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_show_event
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_waiting_long
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_wants_to
import com.vitorpamplona.amethyst.commons.resources.napplet_policy_full_trust
import com.vitorpamplona.amethyst.commons.resources.napplet_policy_full_trust_desc
import com.vitorpamplona.amethyst.commons.resources.napplet_policy_paranoid
import com.vitorpamplona.amethyst.commons.resources.napplet_policy_paranoid_desc
import com.vitorpamplona.amethyst.commons.resources.napplet_policy_reasonable
import com.vitorpamplona.amethyst.commons.resources.napplet_policy_reasonable_desc
import com.vitorpamplona.amethyst.commons.resources.napplet_signer_allow_24h
import com.vitorpamplona.amethyst.commons.resources.napplet_signer_allow_30d
import com.vitorpamplona.amethyst.commons.resources.napplet_signer_allow_all
import com.vitorpamplona.amethyst.commons.resources.napplet_signer_allow_once
import com.vitorpamplona.amethyst.commons.resources.napplet_signer_allow_session
import com.vitorpamplona.amethyst.commons.resources.napplet_signer_deny_once
import com.vitorpamplona.amethyst.commons.resources.napplet_signer_deny_op
import com.vitorpamplona.amethyst.commons.resources.nip46_connect_requests_title
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_batch_allow
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_batch_deny
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_batch_remember
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_batch_select_all
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_batch_select_none
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_batch_signing_as
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_batch_title
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_messages_with
import com.vitorpamplona.amethyst.commons.ui.components.LocalReadOnlyPreview
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.ShowFullTextCache
import com.vitorpamplona.amethyst.commons.ui.components.blockInteractions
import com.vitorpamplona.amethyst.commons.ui.connectedApps.consent.ConnectedAccountRow
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NoteBody
import com.vitorpamplona.amethyst.commons.ui.note.observeEdits
import com.vitorpamplona.amethyst.commons.ui.note.types.ReplyRenderType
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay

/**
 * The per-operation consent prompt: who asks, what it would sign, encrypt or decrypt, a preview of
 * it, and every grant the user can choose. A host puts it in its own dialog or window.
 */
@Composable
fun SignerConsentCard(
    info: SignerConsentInfo,
    accountViewModel: AccountViewModel?,
    onGrant: (SignerOpGrant) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMoreOptions by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier =
            modifier
                .verticalScroll(scrollState)
                .padding(vertical = 24.dp),
    ) {
        // Centered header: icon + title + description
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val isBrowser = info.coordinate.startsWith("browser:")
            FavoriteAppIcon(
                app =
                    if (isBrowser) {
                        FavoriteApp.WebApp(info.coordinate.substringAfter(':'), info.appletTitle, 0L, info.iconUrl)
                    } else {
                        FavoriteApp.NostrApp(info.coordinate, info.appletTitle, 0L, info.iconUrl)
                    },
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp),
            )
            Text(
                info.appletTitle,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                stringRes(Res.string.napplet_consent_wants_to, info.operationSummary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            WaitingTooLongNote(info.requestedAtMillis)
            // Show WHICH account would sign/encrypt/decrypt (avatar + name), not the coordinate hex.
            info.accountName?.let { name ->
                ConnectedAccountRow(name, info.accountPicture, info.accountPubKey)
            }
            // For a decrypt request, WHOSE conversation is being read is the decision. Show
            // that person as an avatar + name, never as nothing.
            info.counterpartyName?.let { name ->
                Text(
                    stringRes(Res.string.nip46_signer_messages_with),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ConnectedAccountRow(name, info.counterpartyPicture, info.counterpartyPubKey)
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.padding(horizontal = 24.dp)) {
            SignerConsentPreview(info, accountViewModel)
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        // Primary: the NARROWEST "remember" available. For decrypt that is "always allow for
        // Alice" — one broad decrypt grant would otherwise hand over every conversation
        // forever, and scoping the op itself would mean a prompt per conversation.
        val narrowOp = info.narrowOp
        val narrowOpLabel = info.narrowOpLabel
        if (narrowOp != null && narrowOpLabel != null) {
            Button(
                onClick = { onGrant(SignerOpGrant.AllowForOp(narrowOp)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                Text(narrowOpLabel)
            }
            // The broad grant stays available, but demoted below the scoped one.
            OutlinedButton(
                onClick = { onGrant(SignerOpGrant.AllowForOp(info.op)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                Text(stringRes(Res.string.napplet_consent_allow_always))
            }
        } else {
            Button(
                onClick = { onGrant(SignerOpGrant.AllowForOp(info.op)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                Text(stringRes(Res.string.napplet_consent_allow_always))
            }
        }

        // Secondary: allow just once
        OutlinedButton(
            onClick = { onGrant(SignerOpGrant.AllowOnce) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        ) {
            Text(stringRes(Res.string.napplet_signer_allow_once))
        }

        // "More options" toggle: session and time-bound grants
        TextButton(
            onClick = { showMoreOptions = !showMoreOptions },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (showMoreOptions) {
                        stringRes(Res.string.napplet_consent_fewer_options)
                    } else {
                        stringRes(Res.string.napplet_consent_more_options)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Icon(
                    if (showMoreOptions) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (showMoreOptions) {
            OutlinedButton(
                onClick = { onGrant(SignerOpGrant.AllowForSession(info.op)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                Text(stringRes(Res.string.napplet_signer_allow_session))
            }
            OutlinedButton(
                onClick = { onGrant(SignerOpGrant.AllowUntil(info.op, TimeUtils.now() + 86_400L)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                Text(stringRes(Res.string.napplet_signer_allow_24h))
            }
            OutlinedButton(
                onClick = { onGrant(SignerOpGrant.AllowUntil(info.op, TimeUtils.now() + 30L * 86_400L)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                Text(stringRes(Res.string.napplet_signer_allow_30d))
            }
            OutlinedButton(
                onClick = { onGrant(SignerOpGrant.AllowAll) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                Text(stringRes(Res.string.napplet_signer_allow_all))
            }
        }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { onGrant(SignerOpGrant.DenyOnce) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(stringRes(Res.string.napplet_signer_deny_once))
        }
        OutlinedButton(
            onClick = { onGrant(SignerOpGrant.DenyForOp(info.op)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            // Names what is remembered (the op), which can be broader than this request's headline.
            Text(stringRes(Res.string.napplet_signer_deny_op, info.rememberedOpLabel ?: info.operationSummary))
        }
    }
}

/**
 * The "what you're acting on" block: the unsigned event rendered as a note (what it will look like
 * once signed, see [UnsignedNotePreview]) with a JSON toggle for sign/publish, or the raw content / decrypted plaintext
 * for encrypt/decrypt. Shared by the single-request dialog and each expanded batch row so a user can
 * always inspect exactly what they are signing/encrypting/decrypting. Best-effort: if the main Activity
 * is gone (only the foreground signer service alive) the note is skipped and the JSON stands in.
 */
@Composable
private fun SignerConsentPreview(
    info: SignerConsentInfo,
    accountViewModel: AccountViewModel?,
) {
    var showRawData by remember(info) { mutableStateOf(false) }
    val previewNav = remember { EmptyNav() }
    val previewNote =
        remember(info, accountViewModel) {
            val template = info.previewTemplate
            val author = info.accountPubKey ?: accountViewModel?.account?.signer?.pubKey
            if (template != null && author != null && accountViewModel != null) {
                runCatching {
                    val unsigned = RumorAssembler.assembleRumor<Event>(author, template)
                    accountViewModel.createTempDraftNote(unsigned, LocalCache.getOrCreateUser(author))
                }.getOrNull()
            } else {
                null
            }
        }

    val hasContent = previewNote != null || info.contentPreview.isNotBlank() || info.rawData.isNotBlank()
    if (!hasContent) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            info.changeSummary?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (info.changeIsWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (previewNote != null && accountViewModel != null) {
                UnsignedNotePreview(previewNote, accountViewModel, previewNav)
            } else if (info.contentPreview.isNotBlank()) {
                Text("“${info.contentPreview}”", style = MaterialTheme.typography.bodyMedium)
            }
            if (info.rawData.isNotBlank()) {
                if (showRawData) {
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        SelectionContainer {
                            Text(
                                info.rawData,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                softWrap = false,
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { showRawData = !showRawData },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text(
                        if (showRawData) {
                            stringRes(Res.string.napplet_consent_hide_event)
                        } else {
                            stringRes(Res.string.napplet_consent_show_event)
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/**
 * The event as it will read once signed: author line and content, nothing else. Deliberately not
 * [com.vitorpamplona.amethyst.commons.ui.note.NoteCompose], which wraps a note in a reaction row,
 * a long-press quick-action popup and a ⋮ options menu — controls that make no sense on a consent
 * prompt, and would react to, zap or broadcast an event that does not exist yet. For the same
 * reason a reply names its parent in one line ([ReplyRenderType.LINE]) instead of embedding it as
 * a full note, and quotes are not expanded (`quotesLeft = 0`). Content-level actions are blocked
 * too ([blockInteractions]).
 */
@Composable
private fun UnsignedNotePreview(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Renderers fade long content into this color, so it must match the preview Surface.
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val backgroundColor = remember(surface) { mutableStateOf(surface) }
    val editState = observeEdits(note, accountViewModel)
    // Everything being signed must be readable without a tap (taps are blocked below), so the
    // text renderer starts expanded instead of cutting long content behind "Show more".
    remember(note.idHex) { ShowFullTextCache.cache.put(note.idHex, true) }

    // Read-only: renderers carry their own actions (poll votes, RSVPs, badge accepts, profile and
    // link taps) that would act on an event that does not exist yet. Drags still scroll the dialog.
    CompositionLocalProvider(LocalReadOnlyPreview provides true) {
        Column(Modifier.blockInteractions()) {
            NoteBody(
                baseNote = note,
                showAuthorPicture = true,
                unPackReply = ReplyRenderType.LINE,
                showSecondRow = false,
                quotesLeft = 0,
                backgroundColor = backgroundColor,
                editState = editState,
                accountViewModel = accountViewModel,
                nav = nav,
                // An empty slot replaces the default ⋮ options menu.
                moreOptions = {},
            )
        }
    }
}

/**
 * Most web apps stop waiting for a signer after a minute or two; a signature granted later is never
 * used. Once a prompt has been open that long, say so, so the user knows to check the app or retry.
 */
@Composable
private fun WaitingTooLongNote(requestedAtMillis: Long) {
    val minutes by produceState(0, requestedAtMillis) {
        while (true) {
            value = ((TimeUtils.nowMillis() - requestedAtMillis) / 60_000L).toInt()
            delay(5_000)
        }
    }
    if (minutes >= 1) {
        Text(
            pluralStringRes(Res.plurals.napplet_consent_waiting_long, minutes, minutes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Shown when more than one request is awaiting consent at once (the signer services requests
 * concurrently). Lists each with a checkbox — all selected by default — and resolves the selected
 * ones together as Allow or Deny. "Remember" makes an Allow persist per-op ([SignerOpGrant.AllowForOp]);
 * off is a one-time [SignerOpGrant.AllowOnce]. Requests left unselected stay pending and re-render
 * (as this list, or the single-request dialog once one remains).
 */
@Composable
fun BatchedConsentCard(
    pending: List<PendingConsent>,
    accountViewModel: AccountViewModel?,
    onResolve: (tokens: List<String>, grant: SignerOpGrant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = pending.map { it.token }.toSet()
    // Seed all-selected ONCE for the initial batch the user opened. The signer services requests
    // concurrently, so `tokens` can change under an open sheet; reconcile incrementally instead of
    // re-seeding — drop resolved tokens but KEEP the user's deselections, and never auto-select a
    // newly-arrived request. Otherwise a request landing (or resolving) mid-decision would silently
    // re-check everything, and an "Allow selected" tap would grant ops the user deselected or never saw.
    var selected by remember { mutableStateOf(tokens) }
    LaunchedEffect(tokens) { selected = selected intersect tokens }
    var rememberChoice by remember { mutableStateOf(false) }
    // Tokens whose full preview (rendered event + JSON, or encrypt/decrypt plaintext) is expanded.
    var expanded by remember { mutableStateOf(emptySet<String>()) }

    Column(modifier = modifier.padding(vertical = 20.dp)) {
        // The sheet is single-account (grouped upstream), so the account is a header, not a
        // per-row label. It says WHO every request in this sheet would act as.
        val account = pending.first().info
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            account.accountName?.let { name ->
                RobohashFallbackAsyncImage(
                    robot = account.accountPubKey ?: name,
                    model = account.accountPicture,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp).clip(CircleShape),
                    loadProfilePicture = true,
                    loadRobohash = true,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    pluralStringRes(Res.plurals.nip46_signer_batch_title, pending.size, pending.size),
                    style = MaterialTheme.typography.titleLarge,
                )
                account.accountName?.let { name ->
                    Text(
                        stringRes(Res.string.nip46_signer_batch_signing_as, name),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        TextButton(
            onClick = {
                selected = if (selected.size == pending.size) emptySet() else pending.map { it.token }.toSet()
            },
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
        ) {
            Text(
                stringRes(
                    if (selected.size == pending.size) Res.string.nip46_signer_batch_select_none else Res.string.nip46_signer_batch_select_all,
                ),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Column(
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
        ) {
            pending.forEach { p ->
                val isExpanded = p.token in expanded
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expanded = if (isExpanded) expanded - p.token else expanded + p.token
                                }.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Checkbox handles its own tap (select); tapping elsewhere on the row expands.
                        Checkbox(
                            checked = p.token in selected,
                            onCheckedChange = { on -> selected = if (on) selected + p.token else selected - p.token },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${p.info.appletTitle} · ${p.info.operationSummary}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                            if (p.info.contentPreview.isNotBlank()) {
                                Text(
                                    p.info.contentPreview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        Icon(
                            if (isExpanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (isExpanded) {
                        Box(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                            SignerConsentPreview(p.info, accountViewModel)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Switch(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
            Text(
                stringRes(Res.string.nip46_signer_batch_remember),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                val tokens = pending.filter { it.token in selected }
                // Per-op remember uses each request's own op; one-time is a single AllowOnce.
                if (rememberChoice) {
                    tokens.forEach { onResolve(listOf(it.token), SignerOpGrant.AllowForOp(it.info.narrowOp ?: it.info.op)) }
                } else {
                    onResolve(tokens.map { it.token }, SignerOpGrant.AllowOnce)
                }
            },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        ) {
            Text(stringRes(Res.string.nip46_signer_batch_allow, selected.size))
        }
        OutlinedButton(
            onClick = { onResolve(pending.filter { it.token in selected }.map { it.token }, SignerOpGrant.DenyOnce) },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(stringRes(Res.string.nip46_signer_batch_deny, selected.size))
        }
    }
}

/**
 * The first-connect prompt for an app asking to use the signer: which account, what it declared it
 * needs, and how much to trust it. A host puts it in its own dialog or window.
 */
@Composable
fun SignerConnectCard(
    info: SignerConnectInfo,
    onConnect: (AppSignerPolicy) -> Unit,
    onBlock: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(AppSignerPolicy.REASONABLE) }

    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
    ) {
        // Centered header: icon + app name + connect subtitle
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val isBrowser = info.coordinate.startsWith("browser:")
            FavoriteAppIcon(
                app =
                    if (isBrowser) {
                        FavoriteApp.WebApp(info.coordinate.substringAfter(':'), info.appletTitle, 0L, info.iconUrl)
                    } else {
                        FavoriteApp.NostrApp(info.coordinate, info.appletTitle, 0L, info.iconUrl)
                    },
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp),
            )
            Text(
                info.appletTitle,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                stringRes(Res.string.napplet_connect_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            // Show WHICH account is being connected (avatar + name), not a raw pubkey.
            val accountName = info.accountName
            if (accountName != null) {
                ConnectedAccountRow(accountName, info.accountPicture, info.accountPubKey)
            } else {
                Text(
                    info.domain,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // What the app declared it needs (nostrconnect `perms`) — so consent is informed,
        // not a silent grant. Approving connects and pre-grants exactly these.
        if (info.requestedPermissions.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringRes(Res.string.nip46_connect_requests_title),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    info.requestedPermissions.forEach { perm ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                MaterialSymbols.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(perm, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text(
            stringRes(Res.string.napplet_connect_how_handle),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(8.dp))

        // Trust level options
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PolicyOption(
                selected = selected == AppSignerPolicy.FULL_TRUST,
                symbol = MaterialSymbols.LockOpen,
                label = stringRes(Res.string.napplet_policy_full_trust),
                description = stringRes(Res.string.napplet_policy_full_trust_desc),
                onClick = { selected = AppSignerPolicy.FULL_TRUST },
            )
            PolicyOption(
                selected = selected == AppSignerPolicy.REASONABLE,
                symbol = MaterialSymbols.Shield,
                label = stringRes(Res.string.napplet_policy_reasonable),
                description = stringRes(Res.string.napplet_policy_reasonable_desc),
                onClick = { selected = AppSignerPolicy.REASONABLE },
            )
            PolicyOption(
                selected = selected == AppSignerPolicy.PARANOID,
                symbol = MaterialSymbols.Lock,
                label = stringRes(Res.string.napplet_policy_paranoid),
                description = stringRes(Res.string.napplet_policy_paranoid_desc),
                onClick = { selected = AppSignerPolicy.PARANOID },
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringRes(Res.string.cancel))
            }
            Button(onClick = { onConnect(selected) }, modifier = Modifier.weight(1f)) {
                Text(stringRes(Res.string.napplet_connect_button))
            }
        }

        OutlinedButton(
            onClick = onBlock,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(
                stringRes(Res.string.napplet_connect_block, info.appletTitle),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PolicyOption(
    selected: Boolean,
    symbol: MaterialSymbol,
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                symbol = symbol,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(
                    symbol = MaterialSymbols.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
