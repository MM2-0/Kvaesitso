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
package com.vitorpamplona.amethyst.commons.browser.ui.pill

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission.Decision
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.browser_pill_cancel
import com.vitorpamplona.amethyst.commons.resources.browser_pill_clear
import com.vitorpamplona.amethyst.commons.resources.browser_pill_decision_allow
import com.vitorpamplona.amethyst.commons.resources.browser_pill_decision_ask
import com.vitorpamplona.amethyst.commons.resources.browser_pill_decision_block
import com.vitorpamplona.amethyst.commons.resources.browser_pill_dialog_answer
import com.vitorpamplona.amethyst.commons.resources.browser_pill_dialog_block
import com.vitorpamplona.amethyst.commons.resources.browser_pill_dialog_generic
import com.vitorpamplona.amethyst.commons.resources.browser_pill_dialog_leave
import com.vitorpamplona.amethyst.commons.resources.browser_pill_dialog_leave_message
import com.vitorpamplona.amethyst.commons.resources.browser_pill_dialog_leave_title
import com.vitorpamplona.amethyst.commons.resources.browser_pill_dialog_says
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_certificate
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_certificate_desc
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_clear
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_clear_all
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_clear_all_confirm
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_clear_confirm
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_connection
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_http
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_http_desc
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_https
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_https_desc
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_open
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_permissions
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_site_data
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_site_data_desc
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_tor
import com.vitorpamplona.amethyst.commons.resources.browser_pill_ok
import com.vitorpamplona.amethyst.commons.resources.browser_pill_perm_allow
import com.vitorpamplona.amethyst.commons.resources.browser_pill_perm_camera_desc
import com.vitorpamplona.amethyst.commons.resources.browser_pill_perm_deny
import com.vitorpamplona.amethyst.commons.resources.browser_pill_perm_location_desc
import com.vitorpamplona.amethyst.commons.resources.browser_pill_perm_microphone_desc
import com.vitorpamplona.amethyst.commons.resources.browser_pill_perm_once
import com.vitorpamplona.amethyst.commons.resources.browser_pill_perm_title
import com.vitorpamplona.amethyst.commons.resources.browser_pill_perm_tor_note
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_forced
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_off
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_on
import com.vitorpamplona.amethyst.commons.ui.stringRes

/** A compact, read-only origin badge for card headers: the page can't fake it, so every card starts with it. */
@Composable
fun OriginBadge(
    host: String,
    security: BrowserChrome.Security,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecurityIcon(security, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(host, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The card frame every page-initiated prompt shares. */
@Composable
internal fun PageCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        border = PillDefaults.hairline(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp)) { content() }
    }
}

/**
 * A site asking for camera / microphone / location: the origin badge, a tinted circle per thing asked with
 * what it means, a note when calls over Tor could still expose the IP, and three plain-worded answers —
 * remembered allow, one-time allow, remembered refusal.
 */
@Composable
fun PermissionPromptCard(
    host: String,
    security: BrowserChrome.Security,
    permissions: Set<BrowserSitePermission>,
    onAllow: () -> Unit,
    onAllowOnce: () -> Unit,
    onDeny: () -> Unit,
) {
    PageCard {
        OriginBadge(host, security)
        Spacer(Modifier.height(20.dp))
        Text(
            stringRes(Res.string.browser_pill_perm_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            permissions.sortedBy { it.ordinal }.forEach { permission ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(permissionSymbol(permission), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, filled = true)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(stringRes(permissionLabel(permission)), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringRes(
                                when (permission) {
                                    BrowserSitePermission.CAMERA -> Res.string.browser_pill_perm_camera_desc
                                    BrowserSitePermission.MICROPHONE -> Res.string.browser_pill_perm_microphone_desc
                                    BrowserSitePermission.LOCATION -> Res.string.browser_pill_perm_location_desc
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // WebRTC can reach out around the SOCKS proxy; say so where it matters, without blocking the call.
        if (security == BrowserChrome.Security.TOR && (BrowserSitePermission.CAMERA in permissions || BrowserSitePermission.MICROPHONE in permissions)) {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillActionIcon(BrowserChrome.Action.TOR, tint = MaterialTheme.colorScheme.onTertiaryContainer, size = 18.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringRes(Res.string.browser_pill_perm_tor_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringRes(Res.string.browser_pill_perm_allow)) }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = onAllowOnce, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringRes(Res.string.browser_pill_perm_once)) }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onDeny, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringRes(Res.string.browser_pill_perm_deny)) }
    }
}

/** The kinds of page dialog. */
enum class PageDialogType { ALERT, CONFIRM, PROMPT, BEFORE_UNLOAD }

/**
 * A page's alert / confirm / prompt / beforeunload. The origin badge heads it (so it can't pass for
 * Amethyst UI); prompt gets a labelled field with clear and IME Done; repeat dialogs offer a checkbox to
 * block the rest, as Chrome does.
 */
@Composable
fun PageDialogCard(
    type: PageDialogType,
    host: String?,
    security: BrowserChrome.Security,
    message: String,
    defaultValue: String = "",
    offerBlock: Boolean = false,
    onResult: (confirmed: Boolean, text: String?, block: Boolean) -> Unit,
    autoFocus: Boolean = true,
) {
    var text by remember { mutableStateOf(defaultValue) }
    var block by remember { mutableStateOf(false) }
    val leave = type == PageDialogType.BEFORE_UNLOAD
    val focus = remember { FocusRequester() }
    if (type == PageDialogType.PROMPT && autoFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    PageCard {
        if (host != null) {
            OriginBadge(host, security)
            Spacer(Modifier.height(16.dp))
        }
        Text(
            when {
                leave -> stringRes(Res.string.browser_pill_dialog_leave_title)
                host != null -> stringRes(Res.string.browser_pill_dialog_says, host)
                else -> stringRes(Res.string.browser_pill_dialog_generic)
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
            Text(
                if (leave) stringRes(Res.string.browser_pill_dialog_leave_message) else message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (type == PageDialogType.PROMPT) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringRes(Res.string.browser_pill_dialog_answer)) },
                singleLine = true,
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(onClick = { text = "" }) { Icon(MaterialSymbols.Cancel, contentDescription = stringRes(Res.string.browser_pill_clear)) }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onResult(true, text, block) }),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (offerBlock) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { block = !block }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = block, onCheckedChange = { block = it })
                Text(stringRes(Res.string.browser_pill_dialog_block), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (type != PageDialogType.ALERT) {
                TextButton(onClick = { onResult(false, null, block) }) { Text(stringRes(Res.string.browser_pill_cancel)) }
                Spacer(Modifier.width(8.dp))
            }
            Button(
                onClick = { onResult(true, if (type == PageDialogType.PROMPT) text else null, block) },
                colors = if (leave) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors(),
            ) {
                Text(stringRes(if (leave) Res.string.browser_pill_dialog_leave else Res.string.browser_pill_ok))
            }
        }
    }
}

/** Certificate facts for [PageInfoSheet]. */
data class CertificateInfo(
    val issuedTo: String,
    val issuedBy: String,
    val validUntil: String,
)

/**
 * Page info as a sheet: who the site is, how you're connected (encryption, route, certificate), what it
 * may use (camera / mic / location as Ask · Allow · Block, editable in place), and its stored data with a
 * clear that asks first — for this site, or for every site this account's browser has visited.
 */
@Composable
fun PageInfoSheet(
    ui: BrowserPillUi,
    certificate: CertificateInfo?,
    onPermissionChange: (BrowserSitePermission, Decision) -> Unit,
    onClearSiteData: () -> Unit,
    onClearAllSiteData: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyConfirmingClear: ClearScope = ClearScope.NONE,
) {
    var confirmingClear by remember { mutableStateOf(initiallyConfirmingClear) }
    val https = ui.chrome.url.startsWith("https://", ignoreCase = true)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        border = PillDefaults.hairline(),
    ) {
        // Scrolls: connection, three permissions and the data card outgrow a phone, and the clear
        // confirmations grow the sheet further, so its end would be cut off below the screen.
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(PillDefaults.SheetPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SiteMonogram(ui.title.ifBlank { ui.host }, size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(ui.host, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(BrowserChrome.originOf(ui.chrome.url) ?: ui.chrome.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            GroupCard(heading = stringRes(Res.string.browser_pill_info_connection)) {
                GroupRow(
                    icon = { Icon(if (https) MaterialSymbols.Lock else MaterialSymbols.NoEncryption, contentDescription = null, tint = if (https) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer, filled = true) },
                    iconContainer = if (https) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.errorContainer,
                    title = stringRes(if (https) Res.string.browser_pill_info_https else Res.string.browser_pill_info_http),
                    supporting = stringRes(if (https) Res.string.browser_pill_info_https_desc else Res.string.browser_pill_info_http_desc),
                    supportingColor = if (https) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    onClick = null,
                )
                ui.chrome.torOn?.let { tor ->
                    GroupDivider()
                    GroupRow(
                        icon = { PillActionIcon(BrowserChrome.Action.TOR, tint = if (tor) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, size = 22.dp) },
                        iconContainer = if (tor) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        title = stringRes(if (tor) Res.string.browser_pill_info_tor else Res.string.browser_pill_info_open),
                        supporting =
                            stringRes(
                                when {
                                    tor -> Res.string.browser_pill_tor_on
                                    ui.chrome.torForced -> Res.string.browser_pill_tor_forced
                                    else -> Res.string.browser_pill_tor_off
                                },
                            ),
                        onClick = null,
                    )
                }
                if (certificate != null) {
                    GroupDivider()
                    GroupRow(
                        icon = { Icon(MaterialSymbols.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        iconContainer = MaterialTheme.colorScheme.surfaceContainerHighest,
                        title = stringRes(Res.string.browser_pill_info_certificate),
                        supporting = stringRes(Res.string.browser_pill_info_certificate_desc, certificate.issuedTo, certificate.issuedBy, certificate.validUntil),
                        onClick = null,
                    )
                }
            }

            GroupCard(heading = stringRes(Res.string.browser_pill_info_permissions)) {
                BrowserSitePermission.entries.forEachIndexed { index, permission ->
                    if (index > 0) GroupDivider()
                    PermissionDecisionRow(permission, ui.sitePermissions[permission] ?: Decision.ASK) { onPermissionChange(permission, it) }
                }
            }

            GroupCard(heading = stringRes(Res.string.browser_pill_info_site_data)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(MaterialSymbols.Cookie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(stringRes(Res.string.browser_pill_info_site_data_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    AnimatedContent(targetState = confirmingClear, label = "clear-site-data") { confirming ->
                        when (confirming) {
                            ClearScope.NONE ->
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { confirmingClear = ClearScope.SITE },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(MaterialSymbols.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringRes(Res.string.browser_pill_info_clear))
                                    }
                                    TextButton(
                                        onClick = { confirmingClear = ClearScope.ALL },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringRes(Res.string.browser_pill_info_clear_all))
                                    }
                                }
                            ClearScope.SITE ->
                                ClearConfirmation(
                                    message = stringRes(Res.string.browser_pill_info_clear_confirm),
                                    confirmLabel = stringRes(Res.string.browser_pill_info_clear),
                                    onCancel = { confirmingClear = ClearScope.NONE },
                                    onConfirm = {
                                        confirmingClear = ClearScope.NONE
                                        onClearSiteData()
                                    },
                                )
                            ClearScope.ALL ->
                                ClearConfirmation(
                                    message = stringRes(Res.string.browser_pill_info_clear_all_confirm),
                                    confirmLabel = stringRes(Res.string.browser_pill_info_clear_all),
                                    onCancel = { confirmingClear = ClearScope.NONE },
                                    onConfirm = {
                                        confirmingClear = ClearScope.NONE
                                        onClearAllSiteData()
                                    },
                                )
                        }
                    }
                }
            }
        }
    }
}

/** Which clear [PageInfoSheet] is asking to confirm, if any. */
enum class ClearScope { NONE, SITE, ALL }

/** The red "are you sure" box a clear asks through before it deletes anything. */
@Composable
private fun ClearConfirmation(
    message: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringRes(Res.string.browser_pill_cancel), color = MaterialTheme.colorScheme.onErrorContainer) }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            ) { Text(confirmLabel) }
        }
    }
}

/** One permission with a first-class Ask · Allow · Block choice. */
@Composable
private fun PermissionDecisionRow(
    permission: BrowserSitePermission,
    decision: Decision,
    onChange: (Decision) -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(permissionSymbol(permission), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Text(stringRes(permissionLabel(permission)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        val options = listOf(Decision.ASK to Res.string.browser_pill_decision_ask, Decision.ALLOW to Res.string.browser_pill_decision_allow, Decision.BLOCK to Res.string.browser_pill_decision_block)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 52.dp)) {
            options.forEachIndexed { index, (option, label) ->
                SegmentedButton(
                    selected = decision == option,
                    onClick = { onChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon = {},
                    label = { Text(stringRes(label), textAlign = TextAlign.Center, maxLines = 1) },
                )
            }
        }
    }
}
