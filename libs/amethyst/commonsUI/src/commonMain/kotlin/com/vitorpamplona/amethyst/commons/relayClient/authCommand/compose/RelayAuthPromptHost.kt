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
package com.vitorpamplona.amethyst.commons.relayClient.authCommand.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthPrompt
import com.vitorpamplona.amethyst.commons.relayClient.auth.UserAuthChoice
import com.vitorpamplona.amethyst.commons.relayClient.authCommand.compose.LoadRelayAuthUser
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurpose
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.relay_auth_also_holding_back
import com.vitorpamplona.amethyst.commons.resources.relay_auth_always_allow_everywhere
import com.vitorpamplona.amethyst.commons.resources.relay_auth_always_everywhere_body
import com.vitorpamplona.amethyst.commons.resources.relay_auth_always_everywhere_title
import com.vitorpamplona.amethyst.commons.resources.relay_auth_always_log_in
import com.vitorpamplona.amethyst.commons.resources.relay_auth_log_in
import com.vitorpamplona.amethyst.commons.resources.relay_auth_login_as
import com.vitorpamplona.amethyst.commons.resources.relay_auth_name_and_n_others
import com.vitorpamplona.amethyst.commons.resources.relay_auth_never
import com.vitorpamplona.amethyst.commons.resources.relay_auth_never_allow_everywhere
import com.vitorpamplona.amethyst.commons.resources.relay_auth_never_everywhere_body
import com.vitorpamplona.amethyst.commons.resources.relay_auth_never_everywhere_title
import com.vitorpamplona.amethyst.commons.resources.relay_auth_not_now
import com.vitorpamplona.amethyst.commons.resources.relay_auth_relay_asks
import com.vitorpamplona.amethyst.commons.resources.relay_auth_remember_relay
import com.vitorpamplona.amethyst.commons.resources.relay_auth_someone_unloaded
import com.vitorpamplona.amethyst.commons.resources.relay_auth_why_my_inbox
import com.vitorpamplona.amethyst.commons.resources.relay_auth_why_my_own_relay
import com.vitorpamplona.amethyst.commons.resources.relay_auth_why_notify_inbox
import com.vitorpamplona.amethyst.commons.resources.relay_auth_why_other
import com.vitorpamplona.amethyst.commons.resources.relay_auth_why_post_venue
import com.vitorpamplona.amethyst.commons.resources.relay_auth_why_read_outbox
import com.vitorpamplona.amethyst.commons.resources.relay_auth_why_read_venue
import com.vitorpamplona.amethyst.commons.resources.relay_auth_why_send_dm
import com.vitorpamplona.amethyst.commons.resources.relay_auth_why_thread
import com.vitorpamplona.amethyst.commons.resources.relay_auth_why_thread_with
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.RelayIconFilter
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/** Avatars shown in the facepile before the "+N" overflow badge. */
private const val FACEPILE_MAX = 5

/** Id of the avatar placeholder spliced into the reason sentence. */
private const val AVATAR_SLOT = "avatar"

/**
 * App-wide host for NIP-42 auth prompts. Collects [RelayAuthPromptBus.prompts] and shows one
 * dialog at a time explaining *why* a relay wants the user to log in (who it serves), letting the
 * user answer for this relay (once or for good, either way) or for every relay at once. Dismissing
 * answers [UserAuthChoice.DISMISS],
 * which the bus also falls back to on timeout, so a relay connection never blocks on the UI.
 */
@Composable
fun RelayAuthPromptHost(accountViewModel: AccountViewModel) {
    val coordinator = LocalAppServices.current.authCoordinator
    val bus = remember { coordinator.promptBus }
    val queue = remember { mutableStateListOf<RelayAuthPrompt>() }

    LaunchedEffect(bus) {
        bus.prompts.collect { prompt ->
            queue.add(prompt)
            // Drop the prompt whenever it resolves by any path (answered here, answered on another
            // account's dialog, or timed out in the bus) so we never show a stale one.
            prompt.onResolved { queue.remove(prompt) }
        }
    }

    // One dialog at a time. Everything else waits its turn, and tells the bus when its turn comes so
    // its answer window starts from the moment it is visible rather than from the challenge.
    queue.firstOrNull { !it.isResolved }?.let { prompt ->
        LaunchedEffect(prompt) { prompt.markShown() }
        RelayAuthPromptDialog(prompt, accountViewModel) { choice ->
            choice.policyEverywhere?.let { policy ->
                // Applied here, not left to the answer below, so the setting survives an expired
                // prompt — the answer window runs while the user reads the confirmation. See
                // AuthCoordinator.applyPolicyEverywhere.
                coordinator.applyPolicyEverywhere(prompt.askingAccount, policy)

                // "all relays" has to mean the ones already queued behind this dialog too. They were
                // decided before the policy existed, so nothing else resolves them, and asking again
                // about relay B right after being told "always/never, all relays" reads as the answer
                // not having taken. Same account only: the policy is that account's.
                //
                // markShown() first even though these are never shown: a prompt still waiting its
                // turn is parked in the bus's queue-wait window, and an answer dropped into it does
                // not land until that window ends — five minutes of an unauthenticated relay the
                // user already answered for. Marking it shown opens its answer window immediately.
                queue.toList().forEach {
                    if (it.askingAccount == prompt.askingAccount) {
                        it.markShown()
                        it.respond(choice)
                    }
                }
            }
            prompt.respond(choice)
            queue.remove(prompt)
        }
    }
}

/**
 * One question, asked once.
 *
 * The title is constant — the decision is always "do I identify myself here?" — and names the account
 * whose npub would be revealed, which on a multi-account device is the one thing the user cannot
 * infer. Everything that varies is a *single sentence* naming what the relay is refusing to do, with
 * the counterparty's avatar spliced into it inline. That sentence carries what used to be four
 * separate blocks: a purpose-specific title, a purpose label, an avatar row, and a red "if you don't"
 * consequence line, each of which repeated the same name.
 *
 * Two buttons and a switch replace four stacked buttons, and the switch means what it says for
 * *both* of them: it is the answer's scope, not a modifier on "Log in". The four combinations are
 * the four [UserAuthChoice] values for this relay — log in once or always, refuse once or for good —
 * which is why there is no separate "Never allow" button any more: it was "Not now" with the switch
 * on, written twice. Flipping the switch relabels the buttons to the answer they now give
 * ("Always log in" / "Never"), so the standing answer is never given under a one-off label.
 *
 * That frees the row underneath for the two answers this relay's buttons cannot give, one per
 * direction: "Always, all relays" and "Never, all relays" set the account's [RelayAuthPolicy] so
 * nothing is asked again, either way. Both are account-wide, so both confirm before they write —
 * see [PolicyEverywhereConfirmation]. The old "always deliver my messages" is still gone: it
 * flipped the policy to CUSTOM plus two account-wide toggles *silently*, which is the part that was
 * wrong, not the writing itself.
 */
@Composable
private fun RelayAuthPromptDialog(
    prompt: RelayAuthPrompt,
    accountViewModel: AccountViewModel,
    onChoice: (UserAuthChoice) -> Unit,
) {
    var rememberRelay by remember(prompt) { mutableStateOf(false) }
    // The account-wide answer waiting on its confirmation, or null while the prompt itself is up.
    var confirming by remember(prompt) { mutableStateOf<UserAuthChoice?>(null) }
    val accountName = rememberDisplayName(prompt.askingAccount, accountViewModel)

    confirming?.let { choice ->
        PolicyEverywhereConfirmation(
            choice = choice,
            accountName = accountName,
            onDismiss = { confirming = null },
            onConfirm = { onChoice(choice) },
        )
        return
    }

    // The purpose the user is most likely to recognize as "what I was just doing".
    val primary = remember(prompt) { prompt.purposes.primary() }

    // A thread names nobody by itself, so the sentence used to say "the rest of this conversation"
    // about something the reader may not have on screen — these prompts routinely surface over the
    // home feed or the settings screen. Resolving the notes to their authors turns the vague
    // reference into a person the reader recognizes.
    val threadFaces =
        remember(primary) {
            if (primary?.kind == AuthPurposeKind.THREAD) {
                // Participants we can actually name come first. The label renders the *first* face, so
                // taking them in note order let one unloaded author send the whole sentence to the
                // generic fallback even when someone else in the same thread was perfectly nameable.
                primary.notes
                    .mapNotNullTo(LinkedHashSet()) { LocalCache.getNoteIfExists(it)?.author?.pubkeyHex }
                    .sortedByDescending { LocalCache.getUserIfExists(it)?.metadataOrNull()?.bestName() != null }
            } else {
                emptyList()
            }
        }

    val faces =
        primary
            ?.counterparties
            ?.toList()
            .orEmpty()
            .ifEmpty { threadFaces }
    val who =
        when (primary?.kind) {
            AuthPurposeKind.POST_VENUE, AuthPurposeKind.READ_VENUE ->
                primary.venues.firstOrNull()?.let { rememberVenueLabel(it, primary.kind, prompt.relayUrl, accountViewModel) }
            else -> counterpartyLabel(faces, accountViewModel)
        }

    AlertDialog(
        onDismissRequest = { onChoice(UserAuthChoice.DISMISS) },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RelayHeader(prompt, accountViewModel)
                Text(
                    text = stringRes(Res.string.relay_auth_login_as, accountName),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ReasonSentence(
                    reason = reasonFor(primary?.kind, who, prompt.isMyOwnRelay),
                    name = who,
                    face = faces.firstOrNull(),
                    accountViewModel = accountViewModel,
                )

                // Everything else this relay is holding back, as one line instead of stacked sections.
                secondaryLine(prompt.purposes, primary)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                RememberRow(checked = rememberRelay, onCheckedChange = { rememberRelay = it })
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Both buttons read the switch, which is what makes it the scope of the answer
                    // rather than a modifier on one of them. Refusing *and* remembering is the DENY
                    // the red "Never allow" button used to write on its own.
                    //
                    // And both say so: with the switch on they relabel to the standing answer they
                    // now give, so nothing turns a one-off refusal into a permanent one behind a
                    // label that still reads "Not now". The refusal takes the error colour with it,
                    // which is the weight the removed red button carried.
                    OutlinedButton(
                        onClick = { onChoice(if (rememberRelay) UserAuthChoice.BLOCK else UserAuthChoice.DISMISS) },
                        modifier = Modifier.weight(1f),
                        colors =
                            if (rememberRelay) {
                                ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                    ) { Text(if (rememberRelay) stringRes(Res.string.relay_auth_never) else stringRes(Res.string.relay_auth_not_now)) }
                    Button(
                        onClick = { onChoice(if (rememberRelay) UserAuthChoice.ALWAYS_ALLOW else UserAuthChoice.ALLOW_ONCE) },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (rememberRelay) stringRes(Res.string.relay_auth_always_log_in) else stringRes(Res.string.relay_auth_log_in)) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = { confirming = UserAuthChoice.NEVER_ALLOW_EVERYWHERE },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringRes(Res.string.relay_auth_never_allow_everywhere), style = MaterialTheme.typography.labelMedium) }
                    TextButton(
                        onClick = { confirming = UserAuthChoice.ALWAYS_ALLOW_EVERYWHERE },
                    ) { Text(stringRes(Res.string.relay_auth_always_allow_everywhere), style = MaterialTheme.typography.labelMedium) }
                }
            }
        },
    )
}

/**
 * The two actions in this flow that write an account-wide setting, so they ask first.
 *
 * The buttons above are about the one relay in the title, and both of their outcomes are listed and
 * reversible on the settings screen. These two are not about this relay at all: they decide every
 * relay that ever asks — revealing the npub named above to all of them, or cutting it off from all of
 * them — and a link label cannot carry that. The confirmation is where the scope becomes visible,
 * and it names the consequence each direction actually has.
 */
@Composable
private fun PolicyEverywhereConfirmation(
    choice: UserAuthChoice,
    accountName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val always = choice == UserAuthChoice.ALWAYS_ALLOW_EVERYWHERE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringRes(if (always) Res.string.relay_auth_always_everywhere_title else Res.string.relay_auth_never_everywhere_title))
        },
        text = {
            Text(
                stringRes(
                    if (always) Res.string.relay_auth_always_everywhere_body else Res.string.relay_auth_never_everywhere_body,
                    accountName,
                ),
            )
        },
        confirmButton = {
            // The label echoes the link that opened this, not the buttons behind it: with the switch
            // on those now read "Always log in" / "Never" for *this relay*, so confirming an
            // account-wide answer under the same words would make the scope ambiguous exactly where
            // it matters most.
            Button(
                onClick = onConfirm,
                colors =
                    if (always) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    },
            ) {
                Text(stringRes(if (always) Res.string.relay_auth_always_allow_everywhere else Res.string.relay_auth_never_allow_everywhere))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
    )
}

/** The relay leads the dialog, because the relay is the thing being trusted. */
@Composable
private fun RelayHeader(
    prompt: RelayAuthPrompt,
    accountViewModel: AccountViewModel,
) {
    val info = loadRelayInfo(prompt.relayUrl).value
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        RobohashFallbackAsyncImage(
            robot = info.id ?: prompt.relayUrl.displayUrl(),
            model = info.icon,
            contentDescription = null,
            colorFilter = RelayIconFilter,
            modifier = Modifier.size(34.dp).clip(MaterialTheme.shapes.small),
            loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
            loadRobohash = LocalDisplaySettings.current.loadRobohash,
        )
        Column {
            Text(
                text = prompt.relayUrl.displayUrl(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Text(
                text = stringRes(Res.string.relay_auth_relay_asks),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The one variable line, with [face]'s avatar spliced in ahead of [name] so the person reads as part
 * of the sentence rather than a separate block to parse.
 */
@Composable
private fun ReasonSentence(
    reason: String,
    name: String?,
    face: HexKey?,
    accountViewModel: AccountViewModel,
) {
    // The reason is already formatted with the name in it, so we locate that substring to bold it and
    // to splice the avatar in ahead of it. Nothing depends on finding it: a translation that drops or
    // rewrites the placeholder simply renders as plain text with no avatar.
    val subject = name?.takeIf { it.isNotBlank() && reason.contains(it) }
    val annotated: AnnotatedString =
        remember(reason, subject, face) {
            buildAnnotatedString {
                if (subject == null) {
                    append(reason)
                } else {
                    val at = reason.indexOf(subject)
                    append(reason.substring(0, at))
                    if (face != null) appendInlineContent(AVATAR_SLOT, " ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(subject) }
                    append(reason.substring(at + subject.length))
                }
            }
        }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge,
        inlineContent =
            if (face == null) {
                emptyMap()
            } else {
                mapOf(
                    AVATAR_SLOT to
                        InlineTextContent(
                            Placeholder(22.sp, 22.sp, PlaceholderVerticalAlign.TextCenter),
                        ) {
                            LoadRelayAuthUser(face) { user ->
                                if (user != null) ClickableUserPicture(user, 20.dp, accountViewModel)
                            }
                        },
                )
            },
    )
}

/** Remembering becomes a switch, so "allow" and "allow forever" stop competing as two buttons. */
@Composable
private fun RememberRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // No fill: this is the least important control in the dialog and it sits directly above the two
    // that matter. A filled surfaceVariant block reads as a black box in dark theme and gives the
    // switch more weight than the buttons under it.
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringRes(Res.string.relay_auth_remember_relay),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/**
 * The purpose that best describes what the user was doing, most user-facing first. Unlike the old
 * ordering this also considers the purposes that name nobody — reading your own inbox and reading the
 * conversation on screen — which previously had no representation at all.
 */
private fun List<AuthPurpose>.primary(): AuthPurpose? =
    listOf(
        AuthPurposeKind.SEND_DM,
        AuthPurposeKind.NOTIFY_INBOX,
        AuthPurposeKind.POST_VENUE,
        AuthPurposeKind.MY_INBOX,
        AuthPurposeKind.THREAD,
        AuthPurposeKind.READ_OUTBOX,
        AuthPurposeKind.READ_VENUE,
    ).firstNotNullOfOrNull { kind ->
        firstOrNull { it.kind == kind && (it.counterparties.isNotEmpty() || it.venues.isNotEmpty() || it.kind.namesNobody()) }
    }

/** True for the purposes that are fully described without a counterparty or a venue. */
private fun AuthPurposeKind.namesNobody() = this == AuthPurposeKind.MY_INBOX || this == AuthPurposeKind.THREAD

/**
 * What the relay is refusing to do. One sentence — it states the reason *and* the consequence of
 * declining ("won't accept … unless"), which is why there is no separate red consequence line.
 */
@Composable
private fun reasonFor(
    kind: AuthPurposeKind?,
    who: String?,
    isMyOwnRelay: Boolean,
): String =
    when (kind) {
        AuthPurposeKind.SEND_DM -> stringRes(Res.string.relay_auth_why_send_dm, who ?: "")
        AuthPurposeKind.NOTIFY_INBOX -> stringRes(Res.string.relay_auth_why_notify_inbox, who ?: "")
        AuthPurposeKind.READ_OUTBOX -> stringRes(Res.string.relay_auth_why_read_outbox, who ?: "")
        AuthPurposeKind.POST_VENUE -> stringRes(Res.string.relay_auth_why_post_venue, who ?: "")
        AuthPurposeKind.READ_VENUE -> stringRes(Res.string.relay_auth_why_read_venue, who ?: "")
        AuthPurposeKind.MY_INBOX -> stringRes(Res.string.relay_auth_why_my_inbox)
        // Name the conversation by who is in it — but only when that name is a real one. Splicing the
        // unloaded placeholder in gives "your conversation with someone you haven't loaded yet", which
        // is longer than the vague version and no more informative, so it falls back instead.
        AuthPurposeKind.THREAD ->
            if (who.isNullOrBlank() || who.startsWith(stringRes(Res.string.relay_auth_someone_unloaded))) {
                stringRes(Res.string.relay_auth_why_thread)
            } else {
                stringRes(Res.string.relay_auth_why_thread_with, who)
            }
        // No attributable purpose. If it is the user's own relay we can at least say that much,
        // which is the only way MY_OWN_RELAY is ever reachable — the deriver is account-agnostic
        // (one shared socket, many accounts) so it cannot know whose relay this is.
        else ->
            if (isMyOwnRelay) {
                stringRes(Res.string.relay_auth_why_my_own_relay)
            } else {
                stringRes(Res.string.relay_auth_why_other)
            }
    }

/** "Also holding back: …" — every other live purpose, as one line rather than stacked sections. */
@Composable
private fun secondaryLine(
    purposes: List<AuthPurpose>,
    primary: AuthPurpose?,
): String? {
    val others = purposes.filter { it !== primary }
    if (others.isEmpty()) return null
    return pluralStringRes(Res.plurals.relay_auth_also_holding_back, others.size, others.size)
}

/** A short label for a set of counterparties: the first person's name, or "Alice and 4 others". */
@Composable
private fun counterpartyLabel(
    pubkeys: List<HexKey>,
    accountViewModel: AccountViewModel,
): String {
    val first = pubkeys.firstOrNull() ?: return ""
    val name = rememberCounterpartyName(first, accountViewModel)
    if (pubkeys.size == 1) return name
    val others = pubkeys.size - 1
    return pluralStringRes(Res.plurals.relay_auth_name_and_n_others, others, name, others)
}

/**
 * Like [rememberDisplayName], but never renders a bare npub.
 *
 * The reason sentence exists to name *a person* — "it won't serve posts from Alice". A pubkey we
 * have no metadata for has no name to give, and [User.toBestDisplayName] falls back to the shortened
 * npub, so the sentence became "it won't serve posts from npub1j9hlsge8…kqy003h0 unless you log in":
 * a string the reader cannot recognize, dressed up as if it were a name. Falling back to the generic
 * phrase says the same amount and reads as language.
 *
 * The dialog *title* deliberately keeps the npub ([rememberDisplayName]): it names the account whose
 * identity is about to be revealed, where an unrecognizable-but-exact key still beats "someone you
 * haven't loaded yet" — the user can at least match it against the account they are logged in as.
 */
@Composable
private fun rememberCounterpartyName(
    pubkey: HexKey,
    accountViewModel: AccountViewModel,
): String {
    var user by remember(pubkey) { mutableStateOf(LocalCache.getUserIfExists(pubkey)) }
    if (user == null) {
        LaunchedEffect(pubkey) { user = LocalCache.checkGetOrCreateUser(pubkey) }
    }
    val loaded = user ?: return stringRes(Res.string.relay_auth_someone_unloaded)
    val metadata by observeUserInfo(loaded, accountViewModel)
    return metadata?.info?.bestName()
        ?: loaded.metadataOrNull()?.bestName()
        ?: stringRes(Res.string.relay_auth_someone_unloaded)
}

/**
 * A display name for a venue id — a public chat channel (64-hex event id), a NIP-53 live activity, a
 * NIP-72 community, a NIP-29 relay group, or a Concord community.
 *
 * Only a [AuthPurposeKind.POST_VENUE] id is *known* to be a channel (it is the root of a channel
 * message we are sending). A READ id may have come from the tag-shape fallback, where a bare `#e`
 * list is as likely to be note ids on a thread as channel roots — so we only ever *look up* an
 * existing channel there. Get-or-creating on read is what used to mint phantom public chats in
 * [LocalCache] for ordinary notes, complete with a metadata subscription for a room that never was.
 *
 * Both joined-room shapes are resolved **before** any of that, because the rules above would not just
 * mislabel them, they would act on them. A NIP-29 group id is only meaningful together with its host
 * relay ([relayUrl], the relay doing the asking). A Concord community id is a bare 64-hex string, so
 * the public-chat branch would take it: on a `POST_VENUE` — which is exactly what a pending plane wrap
 * now derives — that get-or-create mints the phantom channel this function was rewritten to stop
 * minting, and then names the room after its own nevent.
 */
@Composable
private fun rememberVenueLabel(
    venueId: String,
    kind: AuthPurposeKind,
    relayUrl: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
): String {
    // Resolved off LocalCache first and the active account's list second: a prompt is raised per
    // account, so the account on screen is not necessarily the one being asked about — the cache is
    // shared by all of them, the list is not.
    val concordName = remember(venueId) { concordCommunityLabel(venueId, accountViewModel) }
    if (concordName != null) return concordName

    val channel: Channel? =
        remember(venueId, kind, relayUrl) {
            LocalCache.getRelayGroupChannelIfExists(GroupId(venueId, relayUrl))
                ?: when {
                    venueId.length == 64 ->
                        if (kind == AuthPurposeKind.POST_VENUE) {
                            LocalCache.getOrCreatePublicChatChannel(venueId)
                        } else {
                            LocalCache.getPublicChatChannelIfExists(venueId)
                        }
                    venueId.startsWith("30311:") -> Address.parse(venueId)?.let { LocalCache.getOrCreateLiveChannel(it) }
                    else -> null
                }
        }

    if (channel != null) {
        // Subscribes for the channel's metadata and recomposes when it arrives.
        val state by observeChannel(channel, accountViewModel)
        val name = (state?.channel ?: channel).toBestDisplayName()
        if (name.isNotBlank()) return name
    }

    // Community: the d-identifier is the name in NIP-72. Also the fallback for an unresolved channel.
    return venueId.substringAfterLast(':').ifEmpty { venueId.take(8) }
}

/**
 * The label for a Concord community id, or null when [venueId] is not a community we know of — which
 * is what tells the caller to go on treating the id as a channel root.
 *
 * A community has no metadata event to look up: its name comes from the folded Control Plane, held on
 * the `ConcordChannel`s in [LocalCache] (shared by every logged-in account, so this still resolves a
 * prompt raised for a different one), and failing that from the active account's joined list. A
 * community we can place but cannot name still returns a label, so the caller never falls through to
 * the channel branches with an id it would get-or-create.
 */
private fun concordCommunityLabel(
    venueId: String,
    accountViewModel: AccountViewModel,
): String? {
    val folded = LocalCache.getAnyConcordChannelOfCommunity(venueId)
    val joined =
        accountViewModel.account.concordChannelList.liveCommunities.value
            .firstOrNull { it.id == venueId }
    if (folded == null && joined == null) return null
    return folded?.communityName?.takeIf { it.isNotBlank() }
        ?: joined?.name?.takeIf { it.isNotBlank() }
        ?: venueId.take(8)
}

/**
 * The best display name for [pubkey], reactive to metadata arriving from relays. Falls back to a
 * generic "someone you haven't loaded yet" rather than a hex prefix dressed up as a person's name.
 */
@Composable
private fun rememberDisplayName(
    pubkey: HexKey,
    accountViewModel: AccountViewModel,
): String {
    var user by remember(pubkey) { mutableStateOf(LocalCache.getUserIfExists(pubkey)) }
    if (user == null) {
        LaunchedEffect(pubkey) { user = LocalCache.checkGetOrCreateUser(pubkey) }
    }
    val loaded = user ?: return stringRes(Res.string.relay_auth_someone_unloaded)
    // Reading the observed metadata registers a snapshot read, so the name updates when it arrives.
    val metadata by observeUserInfo(loaded, accountViewModel)
    return metadata?.info?.bestName() ?: loaded.toBestDisplayName()
}
