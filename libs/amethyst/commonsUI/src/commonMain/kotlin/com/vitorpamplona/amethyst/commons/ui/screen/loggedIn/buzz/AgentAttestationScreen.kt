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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.buzz.ui.DropdownOption
import com.vitorpamplona.amethyst.commons.buzz.ui.EditableSuggestDropdown
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzHeldAttestations
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_after_label
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_agent_label
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_agent_paste_error
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_authtag_label
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_before_label
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_change_agent
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_conditions_hint
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_copy_tag
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_error_after_range
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_error_before_range
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_error_invalid_agent_key
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_error_kind_range
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_error_not_auth_tag
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_error_not_for_account
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_error_not_json_tag
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_error_sign_failed
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_expired
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_form_desc
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_generate
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_grants_prefix
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_grants_unrestricted
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_hold_button
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_hold_desc
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_hold_title
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_holding
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_kind_label
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_not_yet_valid
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_readonly_desc
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_readonly_title
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_remove
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_signed_title
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_topbar
import com.vitorpamplona.amethyst.commons.resources.buzz_attest_warning
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.AttestationConditions
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.StringResource

// Common event kinds an agent might be restricted to — suggestions for the "Restrict to kind" field;
// any 0–65535 is still accepted by free numeric entry.
private val KIND_OPTIONS =
    listOf(
        DropdownOption("1", "1 · Text note"),
        DropdownOption("7", "7 · Reaction"),
        DropdownOption("9", "9 · Group chat message"),
        DropdownOption("1111", "1111 · Comment"),
        DropdownOption("30023", "30023 · Long-form article"),
        DropdownOption("40002", "40002 · Buzz stream message (legacy)"),
    )

private val KIND_LABELS = KIND_OPTIONS.associate { it.value to it.label }

/**
 * Owner-side NIP-OA attestation issuance. The owner signs a standalone commitment
 * ([OwnerAttestation]) authorizing an agent pubkey to publish under optional
 * [AttestationConditions]; the agent then attaches the resulting `auth` tag to its
 * events and the relay grants it virtual membership while the owner stays a member.
 *
 * This is entirely offline: the signature covers a hashed commitment string, not a
 * Nostr event, so it needs the owner's **raw private key** — a NIP-46 bunker or NIP-55
 * external signer cannot produce it. Read-only accounts see an explanation instead of
 * the form. Nothing is published; the signed tag is a credential the owner hands to the
 * agent operator out-of-band.
 */
@Composable
fun AgentAttestationScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val keyPair = accountViewModel.account.settings.keyPair
    val myPubkey = accountViewModel.account.userProfile().pubkeyHex

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.buzz_attest_topbar), nav) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Agent side: hold an attestation an owner gave you, so this account
            // authenticates to the owner's Buzz relays as a virtual member. Available to
            // any signer — holding a credential doesn't require the raw key.
            HoldAttestationSection(myPubkey = myPubkey, attestation = accountViewModel.account.buzzAttestation)

            // Owner side: issue an attestation for an agent key. Needs the raw private key.
            val privKey = keyPair.privKey
            if (privKey == null) {
                ReadOnlyKeyNotice()
            } else {
                AttestationForm(ownerKey = keyPair, accountViewModel = accountViewModel, nav = nav)
            }
        }
    }
}

/**
 * Agent-side: paste an `auth` tag an owner issued to this account's key. [parseHeldAttestation]
 * turns it into a typed failure the field can show, and [BuzzHeldAttestations.put] re-checks the
 * signature before storing, so the auth coordinator attaches it when this account AUTHs to a Buzz
 * relay. Persisted across restarts, per account, by `BuzzAttestationStore`.
 */
@Composable
private fun HoldAttestationSection(
    myPubkey: String,
    attestation: BuzzHeldAttestations,
) {
    val mine = attestation.flow.collectAsState().value

    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<FormError?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringRes(Res.string.buzz_attest_hold_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (mine != null) {
                Text(
                    text = stringRes(Res.string.buzz_attest_holding),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringRes(Res.string.buzz_attest_grants_prefix, mine.conditions.ifEmpty { stringRes(Res.string.buzz_attest_grants_unrestricted) }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Buzz checks the time clauses against each AUTH, so a held credential outside them
                // is silently worthless; say so rather than keep showing it as working.
                val now = TimeUtils.now()
                if (!mine.isValidAt(now)) {
                    val expired = mine.validUntil()?.let { it < now } == true
                    Text(
                        text = stringRes(if (expired) Res.string.buzz_attest_expired else Res.string.buzz_attest_not_yet_valid),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedButton(onClick = { attestation.clear() }) {
                    Text(stringRes(Res.string.buzz_attest_remove))
                }
            } else {
                Text(
                    text = stringRes(Res.string.buzz_attest_hold_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        error = null
                    },
                    label = { Text(stringRes(Res.string.buzz_attest_authtag_label)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(text = it.text(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        when (val outcome = parseHeldAttestation(input, myPubkey)) {
                            is HoldOutcome.Failure -> error = outcome.error
                            is HoldOutcome.Success -> {
                                // put() re-checks the signature, so honour its answer instead of
                                // assuming it stored: clearing the field on a rejected paste would
                                // read as success and leave the account holding nothing.
                                if (attestation.put(outcome.attestation)) {
                                    input = ""
                                    error = null
                                } else {
                                    error = NOT_FOR_THIS_ACCOUNT
                                }
                            }
                        }
                    },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringRes(Res.string.buzz_attest_hold_button))
                }
            }
        }
    }
}

private sealed interface HoldOutcome {
    data class Success(
        val attestation: OwnerAttestation,
    ) : HoldOutcome

    data class Failure(
        val error: FormError,
    ) : HoldOutcome
}

/** A form error held as a resource, so it is worded when drawn; [detail] is an exception's own text, shown instead when present. */
private class FormError(
    val resource: StringResource,
    val detail: String? = null,
)

@Composable
private fun FormError.text(): String = detail ?: stringRes(resource)

/** Shown for both rejection paths — the parse-time check and [BuzzHeldAttestations.put]'s. */
private val NOT_FOR_THIS_ACCOUNT = FormError(Res.string.buzz_attest_error_not_for_account)

/**
 * Parses a pasted `["auth", owner, conditions, sig]` JSON array and verifies it
 * authorizes [myPubkey]. Returns a human-readable failure on malformed JSON, a
 * non-`auth` tag, or a signature that doesn't verify for this key.
 */
private fun parseHeldAttestation(
    input: String,
    myPubkey: String,
): HoldOutcome {
    val tag =
        try {
            Json
                .parseToJsonElement(input.trim())
                .jsonArray
                .map { it.jsonPrimitive.content }
                .toTypedArray()
        } catch (e: Exception) {
            return HoldOutcome.Failure(FormError(Res.string.buzz_attest_error_not_json_tag))
        }
    val attestation =
        OwnerAttestation.parse(tag)
            ?: return HoldOutcome.Failure(FormError(Res.string.buzz_attest_error_not_auth_tag))
    if (!attestation.verify(myPubkey)) {
        return HoldOutcome.Failure(NOT_FOR_THIS_ACCOUNT)
    }
    return HoldOutcome.Success(attestation)
}

@Composable
private fun ReadOnlyKeyNotice() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringRes(Res.string.buzz_attest_readonly_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringRes(Res.string.buzz_attest_readonly_desc),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AttestationForm(
    ownerKey: KeyPair,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var selectedAgent by remember { mutableStateOf<HexKey?>(null) }
    var kindInput by remember { mutableStateOf("") }
    var afterInput by remember { mutableStateOf("") }
    var beforeInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<FormError?>(null) }
    var result by remember { mutableStateOf<OwnerAttestation?>(null) }

    Text(
        text = stringRes(Res.string.buzz_attest_form_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // #1: pick the agent by name from the local user cache — or paste an npub/hex for a key that
    // isn't a contact yet (the common case for an external agent operator).
    AgentKeyPicker(
        selected = selectedAgent,
        accountViewModel = accountViewModel,
        nav = nav,
        onSelect = {
            selectedAgent = it
            error = null
            result = null
        },
        onClear = {
            selectedAgent = null
            error = null
            result = null
        },
    )

    Text(
        text = stringRes(Res.string.buzz_attest_conditions_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // #3: kind is any 0–65535, but the common ones have names — offer them, keep free numeric entry.
    EditableSuggestDropdown(
        value = kindInput,
        onValueChange = {
            kindInput = it.filter(Char::isDigit)
            error = null
            result = null
        },
        label = stringRes(Res.string.buzz_attest_kind_label),
        options = KIND_OPTIONS,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = KIND_LABELS[kindInput],
    )

    OutlinedTextField(
        value = afterInput,
        onValueChange = {
            afterInput = it.filter(Char::isDigit)
            error = null
            result = null
        },
        label = { Text(stringRes(Res.string.buzz_attest_after_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        // Echo the entered epoch back as a readable UTC time so nobody has to eyeball unix seconds.
        supportingText = unixEcho(afterInput)?.let { echo -> { Text(echo) } },
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = beforeInput,
        onValueChange = {
            beforeInput = it.filter(Char::isDigit)
            error = null
            result = null
        },
        label = { Text(stringRes(Res.string.buzz_attest_before_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = unixEcho(beforeInput)?.let { echo -> { Text(echo) } },
        modifier = Modifier.fillMaxWidth(),
    )

    error?.let {
        Text(
            text = it.text(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Button(
        onClick = {
            val agent = selectedAgent ?: return@Button
            when (val outcome = buildAttestation(agent, kindInput, afterInput, beforeInput, ownerKey)) {
                is AttestationOutcome.Failure -> {
                    error = outcome.error
                    result = null
                }
                is AttestationOutcome.Success -> {
                    error = null
                    result = outcome.attestation
                }
            }
        },
        enabled = selectedAgent != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringRes(Res.string.buzz_attest_generate))
    }

    result?.let { attestation ->
        AttestationResultCard(
            attestation = attestation,
            onCopy = { scope.launch { clipboard.setText(attestation.toTagJson()) } },
        )
    }
}

/**
 * Single-agent people picker: once an agent is chosen it shows as a removable chip; otherwise a
 * name typeahead over the local user cache with an npub/hex paste escape hatch (Enter accepts it).
 * Mirrors the New-DM recipient picker so authorizing an agent stops being the one raw-key field.
 */
@Composable
private fun AgentKeyPicker(
    selected: HexKey?,
    accountViewModel: AccountViewModel,
    nav: INav,
    onSelect: (HexKey) -> Unit,
    onClear: () -> Unit,
) {
    if (selected != null) {
        AgentChip(selected, accountViewModel, nav, onClear)
        return
    }

    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<HexKey>>(emptyList()) }
    var pasteError by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(150)
        suggestions =
            withContext(Dispatchers.IO) {
                LocalCache.search
                    .findUsersStartingWith(query.trim(), accountViewModel.account)
                    .map { it.pubkeyHex }
                    .take(8)
            }
    }

    OutlinedTextField(
        value = query,
        onValueChange = {
            query = it
            pasteError = false
        },
        label = { Text(stringRes(Res.string.buzz_attest_agent_label)) },
        leadingIcon = { Icon(symbol = MaterialSymbols.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
        singleLine = true,
        isError = pasteError,
        // Keep the invalid-paste error on the field the user just typed in, not far down the form.
        supportingText = stringRes(Res.string.buzz_attest_agent_paste_error).takeIf { pasteError }?.let { msg -> { Text(msg) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions =
            KeyboardActions(
                onDone = {
                    val hex = decodePublicKeyAsHexOrNull(query.trim())?.takeIf { it.isValid() }
                    if (hex != null) onSelect(hex) else pasteError = true
                },
            ),
        modifier = Modifier.fillMaxWidth(),
    )
    // A plain Column (not LazyColumn) — this form lives inside a verticalScroll parent.
    suggestions.forEach { hex ->
        AgentSuggestionRow(hex, accountViewModel, nav) { onSelect(hex) }
    }
}

/** One tappable agent search result — avatar + resolved name. */
@Composable
private fun AgentSuggestionRow(
    hex: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
    onClick: () -> Unit,
) {
    val user: User = remember(hex) { LocalCache.getOrCreateUser(hex) }
    val name by observeUserName(user, accountViewModel)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserPicture(hex, 34.dp, accountViewModel = accountViewModel, nav = nav)
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The chosen agent as a removable chip — tapping the close affordance clears the selection. */
@Composable
private fun AgentChip(
    hex: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
    onRemove: () -> Unit,
) {
    val user: User = remember(hex) { LocalCache.getOrCreateUser(hex) }
    val name by observeUserName(user, accountViewModel)
    InputChip(
        selected = false,
        onClick = onRemove,
        label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        avatar = { UserPicture(hex, 22.dp, accountViewModel = accountViewModel, nav = nav) },
        trailingIcon = { Icon(symbol = MaterialSymbols.Close, contentDescription = stringRes(Res.string.buzz_attest_change_agent), modifier = Modifier.size(16.dp)) },
    )
}

@Composable
private fun AttestationResultCard(
    attestation: OwnerAttestation,
    onCopy: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringRes(Res.string.buzz_attest_signed_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringRes(Res.string.buzz_attest_grants_prefix, attestation.conditions.ifEmpty { stringRes(Res.string.buzz_attest_grants_unrestricted) }),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = attestation.toTagJson(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onCopy) {
                    Text(stringRes(Res.string.buzz_attest_copy_tag))
                }
            }
            Text(
                text = stringRes(Res.string.buzz_attest_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private sealed interface AttestationOutcome {
    data class Success(
        val attestation: OwnerAttestation,
    ) : AttestationOutcome

    data class Failure(
        val error: FormError,
    ) : AttestationOutcome
}

/**
 * Validates the form inputs and signs an [OwnerAttestation], or returns a
 * human-readable failure. Kept out of composition so the signing (raw-key) path is a
 * plain function.
 */
private fun buildAttestation(
    agentInput: String,
    kindInput: String,
    afterInput: String,
    beforeInput: String,
    ownerKey: KeyPair,
): AttestationOutcome {
    val agentHex =
        decodePublicKeyAsHexOrNull(agentInput.trim())
            ?: return AttestationOutcome.Failure(FormError(Res.string.buzz_attest_error_invalid_agent_key))
    if (!agentHex.isValid()) {
        return AttestationOutcome.Failure(FormError(Res.string.buzz_attest_error_invalid_agent_key))
    }

    val kind =
        if (kindInput.isBlank()) {
            null
        } else {
            val parsed = kindInput.toIntOrNull()
            if (parsed == null || parsed !in 0..65535) {
                return AttestationOutcome.Failure(FormError(Res.string.buzz_attest_error_kind_range))
            }
            parsed
        }

    val after = parseOptionalUnix(afterInput) ?: return AttestationOutcome.Failure(FormError(Res.string.buzz_attest_error_after_range))
    val before = parseOptionalUnix(beforeInput) ?: return AttestationOutcome.Failure(FormError(Res.string.buzz_attest_error_before_range))

    val conditions = AttestationConditions(kind = kind, createdAtBefore = before.value, createdAtAfter = after.value)

    return try {
        AttestationOutcome.Success(OwnerAttestation.sign(agentHex, conditions, ownerKey))
    } catch (e: IllegalArgumentException) {
        AttestationOutcome.Failure(FormError(Res.string.buzz_attest_error_sign_failed, e.message))
    }
}

/** Wraps a nullable parse so "absent" and "invalid" are distinguishable from the caller. */
private class OptionalUnix(
    val value: Long?,
)

private fun parseOptionalUnix(input: String): OptionalUnix? {
    if (input.isBlank()) return OptionalUnix(null)
    val parsed = input.toLongOrNull() ?: return null
    if (parsed !in 0..4294967295L) return null
    return OptionalUnix(parsed)
}

/** A readable UTC rendering (`yyyy-MM-dd HH:mm UTC`) of an entered epoch-seconds string, or null when it's blank/out of range. */
private fun unixEcho(input: String): String? {
    if (input.isBlank()) return null
    val secs = input.toLongOrNull()?.takeIf { it in 0..4294967295L } ?: return null
    val day = SearchDate.civilFromDays(secs / SECONDS_PER_DAY)
    val secondOfDay = secs % SECONDS_PER_DAY
    val hours = (secondOfDay / 3600).toString().padStart(2, '0')
    val minutes = (secondOfDay % 3600 / 60).toString().padStart(2, '0')
    return "${day.ymd()} $hours:$minutes UTC"
}

private const val SECONDS_PER_DAY = 86_400L

/** Serializes the `auth` tag to a JSON array string (values are hex / canonical ASCII). */
private fun OwnerAttestation.toTagJson(): String = toTag().joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
