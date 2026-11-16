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
package com.vitorpamplona.amethyst.commons.ui.components.namecoin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinResolveState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.namecoin_resolution_badge
import com.vitorpamplona.amethyst.commons.resources.namecoin_resolution_invalid_identifier
import com.vitorpamplona.amethyst.commons.resources.namecoin_resolution_malformed_pubkey
import com.vitorpamplona.amethyst.commons.resources.namecoin_resolution_malformed_record
import com.vitorpamplona.amethyst.commons.resources.namecoin_resolution_no_nostr_field
import com.vitorpamplona.amethyst.commons.resources.namecoin_resolution_no_record
import com.vitorpamplona.amethyst.commons.resources.namecoin_resolution_resolving
import com.vitorpamplona.amethyst.commons.resources.namecoin_resolution_servers_unreachable
import com.vitorpamplona.amethyst.commons.resources.namecoin_resolution_timeout
import com.vitorpamplona.amethyst.commons.resources.namecoin_resolution_unknown_error
import com.vitorpamplona.amethyst.commons.search.SearchSegment
import com.vitorpamplona.amethyst.commons.search.SearchTokenizer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinResolveOutcome
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Translate a [NamecoinResolveOutcome] from the quartz resolver into the
 * shared [NamecoinResolveState] used elsewhere in the app (e.g. the
 * desktop `SearchScreen`'s inline Namecoin lookup row).
 *
 * Mirrors the wording desktop already ships so the same identifier
 * produces the same diagnostic string regardless of which surface
 * triggered the lookup. Callers are expected to handle
 * [NamecoinResolveOutcome.Success] separately (it needs a [User]
 * lookup through [com.vitorpamplona.amethyst.commons.model.cache.LocalCache], which
 * this helper has no access to).
 */
fun mapOutcomeToResolveState(outcome: NamecoinResolveOutcome): NamecoinResolveState =
    when (outcome) {
        is NamecoinResolveOutcome.Success ->
            // Success is intentionally NOT handled here — callers must
            // resolve the pubkey through LocalCache first.
            error("mapOutcomeToResolveState called with Success outcome; resolve via LocalCache instead")

        is NamecoinResolveOutcome.NameNotFound -> NamecoinResolveState.NotFound

        is NamecoinResolveOutcome.NoNostrField ->
            NamecoinResolveState.Error(NamecoinResolveState.Reason.NoNostrField(outcome.name))

        is NamecoinResolveOutcome.MalformedRecord ->
            // Surface the parser detail verbatim so the publisher of the
            // broken record can locate the bad byte
            // (kotlinx.serialization includes a column number).
            NamecoinResolveState.Error(NamecoinResolveState.Reason.MalformedRecord(outcome.name, outcome.error))

        is NamecoinResolveOutcome.ServersUnreachable ->
            NamecoinResolveState.Error(NamecoinResolveState.Reason.ServersUnreachable)

        is NamecoinResolveOutcome.InvalidIdentifier ->
            NamecoinResolveState.Error(NamecoinResolveState.Reason.InvalidIdentifier)

        // The record sent the lookup on to another name (an import or a delegate) and that one
        // failed: say why it did, the reason the user can act on.
        is NamecoinResolveOutcome.ImportFailed -> causeOf(outcome.cause, outcome.name)

        is NamecoinResolveOutcome.DelegationFailed -> causeOf(outcome.cause, outcome.name)

        // How the record is built (references that loop, or nest too deep or too wide), not the
        // network: the publisher has to fix it, so it reads as a malformed record.
        is NamecoinResolveOutcome.CyclicReference ->
            malformed(outcome.name, "references loop: ${outcome.path.joinToString(" → ")}")

        is NamecoinResolveOutcome.LookupBudgetExhausted ->
            malformed(outcome.name, "references need more than ${outcome.limit} lookups")

        is NamecoinResolveOutcome.DepthLimitExceeded ->
            malformed(outcome.name, "references nest deeper than ${outcome.limit}")

        NamecoinResolveOutcome.Timeout ->
            NamecoinResolveState.Error(NamecoinResolveState.Reason.Timeout)
    }

private fun malformed(
    name: String,
    detail: String,
) = NamecoinResolveState.Error(NamecoinResolveState.Reason.MalformedRecord(name, detail))

/** A failed import's or delegation's [cause]; a cause that reads as success is still a failure of [name]. */
private fun causeOf(
    cause: NamecoinResolveOutcome,
    name: String,
): NamecoinResolveState = if (cause is NamecoinResolveOutcome.Success) malformed(name, "a reference failed") else mapOutcomeToResolveState(cause)

@Composable
private fun NamecoinResolveState.Reason.text(): String =
    when (this) {
        is NamecoinResolveState.Reason.NoNostrField -> stringRes(Res.string.namecoin_resolution_no_nostr_field, name)
        is NamecoinResolveState.Reason.MalformedRecord -> stringRes(Res.string.namecoin_resolution_malformed_record, name, detail)
        NamecoinResolveState.Reason.ServersUnreachable -> stringRes(Res.string.namecoin_resolution_servers_unreachable)
        NamecoinResolveState.Reason.InvalidIdentifier -> stringRes(Res.string.namecoin_resolution_invalid_identifier)
        NamecoinResolveState.Reason.Timeout -> stringRes(Res.string.namecoin_resolution_timeout)
        is NamecoinResolveState.Reason.Other -> message ?: stringRes(Res.string.namecoin_resolution_unknown_error)
    }

/**
 * Lightweight syntactic check: does this look like something we should
 * route to Namecoin? Mirrors [com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver.isNamecoinIdentifier]
 * but tolerates a leading `@` (matches the dropdown's [com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState.userSearchTermOrNull]).
 *
 * Accepted shapes:
 *  - `host.bit` and `user@host.bit` (domain namespace)
 *  - `d/<name>` (domain namespace, direct reference)
 *  - `id/<name>` (identity namespace)
 *
 * The length floor matches the dropdown's `>2 chars` rule while still
 * requiring at least one character after a `d/`/`id/` prefix, so single-
 * character Namecoin labels (which are valid, if expensive, on chain)
 * still trigger the on-chain lookup.
 */
fun looksLikeNamecoinIdentifier(raw: String): Boolean {
    val trimmed = raw.trim().removePrefix("@").lowercase()
    if (trimmed.length < 3) return false
    if (trimmed.startsWith("d/") && trimmed.length > 2) return true
    if (trimmed.startsWith("id/") && trimmed.length > 3) return true
    if (trimmed.length < 5) return false
    return trimmed.endsWith(".bit") ||
        trimmed.contains("@") && trimmed.substringAfter('@').endsWith(".bit")
}

/**
 * The single Namecoin term [searchInput] asks to resolve, or null when
 * the field holds anything but one bare identifier.
 *
 * The search field speaks the search token language
 * ([com.vitorpamplona.amethyst.commons.search.SearchTokenizer]): a seeded
 * chip — the `kind:git ` the Git Repositories search icon pre-populates,
 * an author filter, a date bound — is a segment of its own, separate
 * from the word typed after it. Gating on the raw string glued the
 * segments together, so `kind:git mstrofnone.bit` was looked up as
 * `d/kind:git mstrofnone` and the row reported "No record for
 * kind:git mstrofnone.bit", a record nobody asked for, on top of the
 * actual search results. Only a lone plain word ([SearchSegment.Text])
 * that itself passes [looksLikeNamecoinIdentifier] is resolved; a
 * compound query is a search, not a lookup, and the row stays out of
 * its way.
 */
fun namecoinLookupTerm(searchInput: String): String? {
    val trimmed = searchInput.trim().removePrefix("@")
    if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null
    if (SearchTokenizer.tokenize(trimmed).singleOrNull() !is SearchSegment.Text) return null
    return trimmed.takeIf(::looksLikeNamecoinIdentifier)
}

/**
 * Inline Namecoin resolution indicator + result row. Designed to be
 * mounted alongside any text input whose local-cache prefix search can
 * race ahead of an on-chain `.bit` lookup (the onchain-zap recipient
 * field and the global search bar both have this race).
 *
 * Behaviour:
 *  - Renders nothing when [searchInput] is not one bare Namecoin-shaped
 *    identifier (`.bit`, `d/<name>`, or `id/<name>`) — see
 *    [namecoinLookupTerm], which keeps seeded search chips like
 *    `kind:git ` out of the lookup.
 *  - Shows a small spinner row ("Resolving on Namecoin…") while the
 *    ElectrumX lookup is in flight (after a 300 ms debounce to match
 *    typical input-field debounce intervals).
 *  - On success, shows the resolved user as a tappable row with a
 *    `MaterialSymbols.Link` badge labelled "Namecoin"; tapping calls
 *    [onUserResolved].
 *  - On failure, shows a single explanatory line in the error colour.
 *
 * State is held in [NamecoinResolveState] (the same sealed class the
 * desktop `SearchScreen` and `NamecoinNameService` already use) so this
 * row stays in lockstep with the rest of the app's Namecoin UI.
 *
 * The composable is intentionally self-contained: it owns its own
 * [LaunchedEffect] keyed on [searchInput], so it cancels in-flight
 * lookups whenever the user keeps typing.
 *
 * @param modifier applied to the outer `Column` so callers can position
 *   or pad the row (e.g. the search bar pads horizontally).
 */
@Composable
fun NamecoinResolutionRow(
    searchInput: String,
    accountViewModel: AccountViewModel,
    onUserResolved: (User) -> Unit,
    modifier: Modifier = Modifier,
) {
    val term = remember(searchInput) { namecoinLookupTerm(searchInput) } ?: return

    var state by remember { mutableStateOf<NamecoinResolveState?>(null) }
    val resolver = LocalAppServices.current.namecoinResolver

    LaunchedEffect(term) {
        // Match UserSuggestionState's 300 ms debounce so we don't fire a
        // lookup on every keystroke.
        delay(300)
        state = NamecoinResolveState.Loading
        val outcome =
            withContext(Dispatchers.IO) {
                runCatchingCancellable {
                    resolver.resolveDetailed(term)
                }.getOrElse {
                    NamecoinResolveOutcome.ServersUnreachable(
                        it.message ?: it::class.simpleName ?: "Lookup error",
                    )
                }
            }
        state =
            when (outcome) {
                is NamecoinResolveOutcome.Success -> NamecoinResolveState.Resolved(outcome.result)
                else -> mapOutcomeToResolveState(outcome)
            }
    }

    Column(modifier = modifier) {
        Spacer(Modifier.size(8.dp))
        when (val s = state) {
            null, NamecoinResolveState.Loading -> ResolvingChip(term)
            is NamecoinResolveState.Resolved -> ResolvedRow(term, s, accountViewModel, onUserResolved)
            NamecoinResolveState.NotFound -> FailedRow(stringRes(Res.string.namecoin_resolution_no_record, term))
            is NamecoinResolveState.Error -> FailedRow(s.reason.text())
        }
    }
}

@Composable
private fun ResolvingChip(query: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringRes(Res.string.namecoin_resolution_resolving, query),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ResolvedRow(
    query: String,
    state: NamecoinResolveState.Resolved,
    accountViewModel: AccountViewModel,
    onUserResolved: (User) -> Unit,
) {
    // Look up the User in the same cache the rest of the app uses, exactly
    // the way desktop's SearchScreen does. Falls back to a malformed-record
    // error row if the pubkey somehow fails the hex shape check.
    val user =
        remember(state.result.pubkey) {
            accountViewModel.account.cache.checkGetOrCreateUser(state.result.pubkey)
        }
    if (user == null) {
        FailedRow(
            stringRes(Res.string.namecoin_resolution_malformed_pubkey, state.result.namecoinName, state.result.pubkey),
        )
        return
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onUserResolved(user) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            UserPicture(
                userHex = user.pubkeyHex,
                size = 32.dp,
                accountViewModel = accountViewModel,
                nav = EmptyNav(),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.toBestDisplayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            NamecoinBadge()
        }
    }
}

@Composable
private fun NamecoinBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringRes(Res.string.namecoin_resolution_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FailedRow(message: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            // Selectable so the diagnostic (server detail, parser column, name)
            // can be copied into a bug report or checked against another client.
            SelectionContainer {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer),
                )
            }
        }
    }
}
