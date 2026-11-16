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
package com.vitorpamplona.amethyst.commons.ui.actions.mediaServers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.mediaServers.ui.MediaServerEditField
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_a_blossom_server
import com.vitorpamplona.amethyst.commons.resources.blossom_import_add_url_label
import com.vitorpamplona.amethyst.commons.resources.blossom_import_busy
import com.vitorpamplona.amethyst.commons.resources.blossom_import_disable_all
import com.vitorpamplona.amethyst.commons.resources.blossom_import_enable_all
import com.vitorpamplona.amethyst.commons.resources.blossom_import_files_found
import com.vitorpamplona.amethyst.commons.resources.blossom_import_found_files
import com.vitorpamplona.amethyst.commons.resources.blossom_import_intro
import com.vitorpamplona.amethyst.commons.resources.blossom_import_manage_servers
import com.vitorpamplona.amethyst.commons.resources.blossom_import_no_targets
import com.vitorpamplona.amethyst.commons.resources.blossom_import_none_found
import com.vitorpamplona.amethyst.commons.resources.blossom_import_scan
import com.vitorpamplona.amethyst.commons.resources.blossom_import_scanning
import com.vitorpamplona.amethyst.commons.resources.blossom_import_source_failed
import com.vitorpamplona.amethyst.commons.resources.blossom_import_sources_section
import com.vitorpamplona.amethyst.commons.resources.blossom_import_start_button
import com.vitorpamplona.amethyst.commons.resources.blossom_import_title
import com.vitorpamplona.amethyst.commons.resources.delete_media_server
import com.vitorpamplona.amethyst.commons.ui.components.rememberLongNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/** Vibrant palette for server monograms; picked deterministically from the host name. */
private val ImportMonogramColors =
    listOf(
        Color(0xFF8B5CF6),
        Color(0xFF0EA5A0),
        Color(0xFFE07B00),
        Color(0xFF4169E1),
        Color(0xFFD16D8F),
        Color(0xFF4F9D4F),
        Color(0xFFB66605),
        Color(0xFF7C6FE0),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlossomImportScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val blossomImportBusyStr = stringRes(Res.string.blossom_import_busy)
    val vm: BlossomImportViewModel = rememberViewModel { BlossomImportViewModel() }
    vm.init(accountViewModel, LocalAppServices.current)

    val sources by vm.sources.collectAsStateWithLifecycle()
    val candidates by vm.candidates.collectAsStateWithLifecycle()
    val scanning by vm.isScanning.collectAsStateWithLifecycle()
    val scanned by vm.scanned.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    // Collect the user's own server list so the empty-state ↔ picker switch recomposes if the
    // kind-10063 list arrives from a relay just after the screen opens (common on cold start).
    val targetServers by accountViewModel.account.blossomServers.flow
        .collectAsStateWithLifecycle()
    val longNotice = rememberLongNotice()

    Scaffold(
        topBar = {
            TopBarWithBackButton(
                caption = stringRes(Res.string.blossom_import_title),
                nav = nav,
            )
        },
    ) { padding ->
        if (targetServers.isEmpty()) {
            NoTargetsState(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding(),
                        ),
                onManageServers = { nav.nav(Route.EditMediaServers) },
            )
            return@Scaffold
        }

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding(),
                    ),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringRes(Res.string.blossom_import_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringRes(Res.string.blossom_import_sources_section),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    val anyDisabled = sources.any { !it.enabled }
                    TextButton(onClick = { vm.setAll(anyDisabled) }, enabled = sources.isNotEmpty()) {
                        Text(
                            stringRes(
                                if (anyDisabled) Res.string.blossom_import_enable_all else Res.string.blossom_import_disable_all,
                            ),
                        )
                    }
                }
            }

            items(sources, key = { it.baseUrl }) { source ->
                ImportSourceRow(
                    source = source,
                    onToggle = { vm.toggle(source.baseUrl) },
                    onRemove = { vm.remove(source.baseUrl) },
                )
            }

            item {
                Text(
                    text = stringRes(Res.string.blossom_import_add_url_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.grayText,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                MediaServerEditField(Res.string.add_a_blossom_server) { vm.addCustom(it) }
            }

            item {
                val enabledCount = sources.count { it.enabled }
                FilledTonalButton(
                    onClick = { vm.scan() },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    enabled = !scanning && enabledCount > 0,
                ) {
                    if (scanning) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(stringRes(Res.string.blossom_import_scanning))
                    } else {
                        Icon(symbol = MaterialSymbols.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringRes(Res.string.blossom_import_scan))
                    }
                }
            }

            error?.let {
                item {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (scanned && !scanning) {
                if (candidates.isEmpty()) {
                    item {
                        Text(
                            text = stringRes(Res.string.blossom_import_none_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.grayText,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                } else {
                    item {
                        ImportResultCard(
                            count = candidates.size,
                            onImport = {
                                when (vm.importSelected()) {
                                    is ImportStart.Started -> nav.popBack()
                                    ImportStart.Busy ->
                                        longNotice.show(blossomImportBusyStr)
                                    ImportStart.Empty -> {}
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoTargetsState(
    modifier: Modifier,
    onManageServers: () -> Unit,
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                symbol = MaterialSymbols.Storage,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.grayText,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringRes(Res.string.blossom_import_no_targets),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.grayText,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onManageServers) {
            Text(stringRes(Res.string.blossom_import_manage_servers))
        }
    }
}

@Composable
private fun ImportSourceRow(
    source: ImportSource,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onToggle)
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ServerMonogramTile(name = source.name, size = 36.dp)

        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = source.name.replaceFirstChar(Char::titlecase),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ScanStatusLabel(source.scan, source.host)
        }

        if (source.custom) {
            IconButton(onClick = onRemove) {
                Icon(
                    symbol = MaterialSymbols.Delete,
                    contentDescription = stringRes(Res.string.delete_media_server),
                    tint = MaterialTheme.colorScheme.grayText,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Switch(checked = source.enabled, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun ScanStatusLabel(
    scan: SourceScanState,
    host: String,
) {
    when (scan) {
        is SourceScanState.Idle ->
            Text(
                text = host,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        is SourceScanState.Scanning ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 1.5.dp)
                Text(
                    text = stringRes(Res.string.blossom_import_scanning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
        is SourceScanState.Found -> {
            val count = scan.count
            Text(
                text = pluralStringRes(Res.plurals.blossom_import_files_found, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = if (count > 0) MaterialTheme.colorScheme.allGoodColor else MaterialTheme.colorScheme.grayText,
            )
        }
        is SourceScanState.Failed ->
            Text(
                text = stringRes(Res.string.blossom_import_source_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
    }
}

@Composable
private fun ImportResultCard(
    count: Int,
    onImport: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = pluralStringRes(Res.plurals.blossom_import_found_files, count, count),
            style = MaterialTheme.typography.bodyMedium,
        )
        FilledTonalButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(symbol = MaterialSymbols.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(pluralStringRes(Res.plurals.blossom_import_start_button, count, count))
        }
    }
}

/** A colored letter tile identifying a server, derived from its host name. */
@Composable
private fun ServerMonogramTile(
    name: String,
    size: Dp,
) {
    val letter = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    val color = ImportMonogramColors[((name.hashCode() % ImportMonogramColors.size) + ImportMonogramColors.size) % ImportMonogramColors.size]
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size / 3))
                .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}
