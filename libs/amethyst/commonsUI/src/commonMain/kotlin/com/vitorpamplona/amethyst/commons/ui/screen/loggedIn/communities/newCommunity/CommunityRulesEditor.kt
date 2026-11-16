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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.communities.newCommunity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_banned_add
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_banned_hint
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_banned_placeholder
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_banned_section
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_hint
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_kind_add
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_kind_comment
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_kind_custom_label
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_kind_long_form
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_kind_picture
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_kind_short_text
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_kind_short_video
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_kind_video
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_kinds_hint
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_kinds_section
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_limits_max_bytes
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_limits_max_per_day
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_limits_save
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_limits_title
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_limits_unlimited
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_max_event_size_hint
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_max_event_size_section
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_wot_add
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_wot_depth
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_wot_hint
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_wot_root
import com.vitorpamplona.amethyst.commons.resources.new_community_rules_wot_section
import com.vitorpamplona.amethyst.commons.resources.remove
import com.vitorpamplona.amethyst.commons.ui.components.Nip05OrPubkeyLine
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightPage
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import org.jetbrains.compose.resources.StringResource

/**
 * NIP-9B structured-rules editor section embedded in the community form.
 *
 * The whole section is opt-in: when no chip is selected and no other field is
 * filled, [NewCommunityModel.publish] won't emit a `kind:34551` event and the
 * community continues to behave like any pre-9A NIP-72 community. The freeform
 * `rules: String` text on `kind:34550` is still rendered above this section by
 * [CommunityFormScreen].
 */
@Composable
internal fun CommunityRulesEditorSection(
    model: NewCommunityModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringRes(Res.string.new_community_rules_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AllowedKindsBlock(model = model)
        BannedUsersBlock(model = model, accountViewModel = accountViewModel, nav = nav)
        WotGateBlock(model = model)
        MaxEventSizeField(model = model)
    }
}

// --- Allowed kinds -----------------------------------------------------------------------------

private data class KnownKind(
    val kind: Int,
    val labelRes: StringResource,
)

private val KNOWN_KINDS =
    listOf(
        KnownKind(1, Res.string.new_community_rules_kind_short_text),
        KnownKind(20, Res.string.new_community_rules_kind_picture),
        KnownKind(21, Res.string.new_community_rules_kind_video),
        KnownKind(22, Res.string.new_community_rules_kind_short_video),
        KnownKind(1111, Res.string.new_community_rules_kind_comment),
        KnownKind(30023, Res.string.new_community_rules_kind_long_form),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllowedKindsBlock(model: NewCommunityModel) {
    SectionLabel(Res.string.new_community_rules_kinds_section)

    Text(
        text = stringRes(Res.string.new_community_rules_kinds_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val current = model.kindRules.toList()
    var editingKind by remember { mutableStateOf<Int?>(null) }

    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KNOWN_KINDS.forEach { known ->
            val selected = current.any { it.kind == known.kind }
            FilterChip(
                selected = selected,
                onClick = {
                    if (selected) {
                        model.removeKindRule(known.kind)
                    } else {
                        model.addKindRule(KindRuleDraft(kind = known.kind))
                    }
                },
                label = { Text(stringRes(known.labelRes), style = MaterialTheme.typography.labelMedium) },
                trailingIcon =
                    if (selected) {
                        {
                            IconButton(onClick = { editingKind = known.kind }) {
                                Icon(
                                    symbol = MaterialSymbols.Edit,
                                    contentDescription =
                                        stringRes(Res.string.new_community_rules_limits_title, known.kind),
                                )
                            }
                        }
                    } else {
                        null
                    },
            )
        }

        // Custom kinds the user has added that aren't in KNOWN_KINDS.
        current
            .filter { rule -> KNOWN_KINDS.none { it.kind == rule.kind } }
            .forEach { rule ->
                AssistChip(
                    onClick = { editingKind = rule.kind },
                    label = {
                        Text(
                            stringRes(Res.string.new_community_rules_kind_custom_label) + " " + rule.kind,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { model.removeKindRule(rule.kind) }) {
                            Icon(
                                symbol = MaterialSymbols.Close,
                                contentDescription = stringRes(Res.string.remove),
                            )
                        }
                    },
                )
            }
    }

    CustomKindAddRow(model = model)

    val toEdit = editingKind
    if (toEdit != null) {
        val rule = current.firstOrNull { it.kind == toEdit }
        if (rule != null) {
            KindRuleLimitsDialog(
                rule = rule,
                onDismiss = { editingKind = null },
                onSave = { updated ->
                    model.updateKindRule(toEdit) { updated }
                    editingKind = null
                },
            )
        } else {
            editingKind = null
        }
    }
}

@Composable
private fun CustomKindAddRow(model: NewCommunityModel) {
    var raw by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = raw,
            onValueChange = { input -> raw = input.filter { it.isDigit() } },
            label = { Text(stringRes(Res.string.new_community_rules_kind_custom_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = {
                val parsed = raw.toIntOrNull()
                if (parsed != null && parsed >= 0) {
                    model.addKindRule(KindRuleDraft(kind = parsed))
                    raw = ""
                }
            },
            enabled = raw.toIntOrNull()?.let { it >= 0 } == true,
        ) {
            Text(stringRes(Res.string.new_community_rules_kind_add))
        }
    }
}

@Composable
private fun KindRuleLimitsDialog(
    rule: KindRuleDraft,
    onDismiss: () -> Unit,
    onSave: (KindRuleDraft) -> Unit,
) {
    var maxBytes by remember(rule) { mutableStateOf(rule.maxBytes?.toString().orEmpty()) }
    var maxPerDay by remember(rule) { mutableStateOf(rule.maxPerAuthorPerDay?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.new_community_rules_limits_title, rule.kind)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = maxBytes,
                    onValueChange = { input -> maxBytes = input.filter { it.isDigit() } },
                    label = { Text(stringRes(Res.string.new_community_rules_limits_max_bytes)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxPerDay,
                    onValueChange = { input -> maxPerDay = input.filter { it.isDigit() } },
                    label = { Text(stringRes(Res.string.new_community_rules_limits_max_per_day)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringRes(Res.string.new_community_rules_limits_unlimited),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        rule.copy(
                            maxBytes = maxBytes.toIntOrNull()?.takeIf { it > 0 },
                            maxPerAuthorPerDay = maxPerDay.toIntOrNull()?.takeIf { it > 0 },
                        ),
                    )
                },
            ) {
                Text(stringRes(Res.string.new_community_rules_limits_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}

// --- Banned users ------------------------------------------------------------------------------

@Composable
private fun BannedUsersBlock(
    model: NewCommunityModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    SectionLabel(Res.string.new_community_rules_banned_section)

    Text(
        text = stringRes(Res.string.new_community_rules_banned_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    var search by remember { mutableStateOf("") }

    val userSuggestions =
        remember {
            UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder())
        }

    DisposableEffect(Unit) {
        onDispose { userSuggestions.reset() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        model.bannedPubkeys.toList().forEach { entry ->
            val cachedUser = remember(entry.pubkey) { accountViewModel.account.cache.getUserIfExists(entry.pubkey) }
            BannedPubkeyRow(
                pubkeyHex = entry.pubkey,
                user = cachedUser,
                accountViewModel = accountViewModel,
                nav = nav,
                onRemove = { model.removeBannedPubkey(entry.pubkey) },
            )
        }

        OutlinedTextField(
            value = search,
            onValueChange = {
                search = it
                if (it.length > 1) {
                    userSuggestions.processCurrentWord(it)
                } else {
                    userSuggestions.reset()
                }
            },
            label = { Text(stringRes(Res.string.new_community_rules_banned_add)) },
            placeholder = { Text(stringRes(Res.string.new_community_rules_banned_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (search.length > 1) {
            ShowUserSuggestionList(
                userSuggestions = userSuggestions,
                onSelect = { user ->
                    model.addBannedPubkey(BannedPubkeyDraft(pubkey = user.pubkeyHex))
                    search = ""
                    userSuggestions.reset()
                },
                accountViewModel = accountViewModel,
                modifier = SuggestionListDefaultHeightPage,
            )
        }
    }
}

@Composable
private fun BannedPubkeyRow(
    pubkeyHex: HexKey,
    user: User?,
    accountViewModel: AccountViewModel,
    nav: INav,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserPicture(
            userHex = pubkeyHex,
            size = 36.dp,
            accountViewModel = accountViewModel,
            nav = nav,
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        ) {
            Text(
                text = user?.toBestDisplayName() ?: shortenHex(pubkeyHex),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (user != null) Nip05OrPubkeyLine(user)
        }
        IconButton(onClick = onRemove) {
            Icon(symbol = MaterialSymbols.Close, contentDescription = stringRes(Res.string.remove))
        }
    }
}

private fun shortenHex(hex: String): String = if (hex.length <= 16) hex else hex.take(8) + "\u2026" + hex.takeLast(8)

// --- WoT gate ----------------------------------------------------------------------------------

@Composable
private fun WotGateBlock(model: NewCommunityModel) {
    SectionLabel(Res.string.new_community_rules_wot_section)

    Text(
        text = stringRes(Res.string.new_community_rules_wot_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        model.wotGates.toList().forEach { gate ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = shortenHex(gate.rootPubkey) + "  \u00b7  " + gate.depth,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { model.removeWotGate(gate) }) {
                    Icon(symbol = MaterialSymbols.Close, contentDescription = stringRes(Res.string.remove))
                }
            }
        }

        var rootRaw by remember { mutableStateOf("") }
        var depthRaw by remember { mutableStateOf("") }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = rootRaw,
                onValueChange = { rootRaw = it },
                label = { Text(stringRes(Res.string.new_community_rules_wot_root)) },
                singleLine = true,
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = depthRaw,
                onValueChange = { input -> depthRaw = input.filter { it.isDigit() } },
                label = { Text(stringRes(Res.string.new_community_rules_wot_depth)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }

        TextButton(
            onClick = {
                val depth = depthRaw.toIntOrNull()?.takeIf { it > 0 } ?: return@TextButton
                val hex = parsePubkeyToHex(rootRaw.trim()) ?: return@TextButton
                model.addWotGate(WotGateDraft(rootPubkey = hex, depth = depth))
                rootRaw = ""
                depthRaw = ""
            },
            enabled =
                rootRaw.trim().isNotEmpty() &&
                    depthRaw.toIntOrNull()?.let { it > 0 } == true &&
                    parsePubkeyToHex(rootRaw.trim()) != null,
        ) {
            Text(stringRes(Res.string.new_community_rules_wot_add))
        }
    }
}

/** Accepts an `npub1...` bech32 or a raw 64-char hex pubkey. Returns canonical hex or null. */
internal fun parsePubkeyToHex(input: String): HexKey? {
    val trimmed = input.trim()
    if (trimmed.length == 64 && trimmed.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
        return trimmed.lowercase()
    }
    if (trimmed.startsWith("npub1")) {
        val parsed = Nip19Parser.uriToRoute(trimmed)?.entity as? NPub ?: return null
        return parsed.hex
    }
    return null
}

// --- Max event size ---------------------------------------------------------------------------

@Composable
private fun MaxEventSizeField(model: NewCommunityModel) {
    SectionLabel(Res.string.new_community_rules_max_event_size_section)

    Text(
        text = stringRes(Res.string.new_community_rules_max_event_size_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val current = model.maxEventSize
    var raw by remember(current) { mutableStateOf(current?.toString().orEmpty()) }

    OutlinedTextField(
        value = raw,
        onValueChange = { input ->
            val cleaned = input.filter { it.isDigit() }
            raw = cleaned
            model.maxEventSize = cleaned.toIntOrNull()?.takeIf { it > 0 }
        },
        label = { Text(stringRes(Res.string.new_community_rules_limits_max_bytes)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

// --- Helpers ----------------------------------------------------------------------------------

@Composable
private fun SectionLabel(resourceId: StringResource) {
    Text(
        text = stringRes(resourceId),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}
