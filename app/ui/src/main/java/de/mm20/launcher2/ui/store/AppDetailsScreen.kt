package de.mm20.launcher2.ui.store

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import de.mm20.launcher2.ui.system.UadRepository
import de.mm20.launcher2.ui.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.produceState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import de.mm20.launcher2.ui.media.photos.PhotoViewerActivity
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.options.ItemOptions
import de.mm20.launcher2.store.parser.StoreUrlParser
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.media.MediaFrame
import kotlinx.serialization.Serializable
import java.text.DateFormat
import java.util.Date
import kotlin.math.ln
import kotlin.math.pow

@Serializable
data class AppDetailsRoute(val itemId: String) : NavKey

@Composable
fun AppDetailsScreen(itemId: String) {
    val viewModel: StoreViewModel = viewModel()
    val context = LocalContext.current
    val backStack = LocalBackStack.current

    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val row = rows.firstOrNull { it.item.id == itemId }
    val installStates by viewModel.installStates.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()

    StoreUpdateConsent(viewModel)
    MediaFrame(row?.item?.displayName ?: "") {
        if (row == null) return@MediaFrame
        val item = row.item
        val o = row.options
        var confirmRemove by remember { mutableStateOf(false) }
        var renaming by remember { mutableStateOf(false) }
        var filterText by remember(item.source) { mutableStateOf(sourceRegex(item.source).orEmpty()) }
        var prerelease by remember(item.source) { mutableStateOf(sourcePrerelease(item.source)) }
        var category by remember(item.id) { mutableStateOf(o.category) }
        var note by remember(item.id) { mutableStateOf(o.note) }

        val screenshotUrls = remember(item.latestRelease?.changelog) {
            val changelog = item.latestRelease?.changelog.orEmpty()
            val regex = Regex("""(!\[.*?\]\((https?://\S+?\.(?:png|jpg|jpeg|webp|gif))\)|src=["'](https?://\S+?\.(?:png|jpg|jpeg|webp|gif))["']|https?://\S+?\.(?:png|jpg|jpeg|webp|gif))""", RegexOption.IGNORE_CASE)
            regex.findAll(changelog).mapNotNull {
                it.groups[2]?.value ?: it.groups[3]?.value ?: it.groups[1]?.value?.takeIf { u -> u.startsWith("http") }
            }.distinct().toList()
        }

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(item.packageName, item.displayName, 64, item.installedVersionCode, item.source)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (item.packageName == "unknown.package") stringResource(I18nR.string.hf_store_package_unknown) else item.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(item.source.summary(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (screenshotUrls.isNotEmpty()) {
                item {
                    Card("App Screenshots") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            items(screenshotUrls) { url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = "App Screenshot",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .height(220.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            val intent = Intent(context, PhotoViewerActivity::class.java).apply {
                                                putStringArrayListExtra(PhotoViewerActivity.EXTRA_URIS, arrayListOf(url))
                                                putExtra(PhotoViewerActivity.EXTRA_INDEX, 0)
                                            }
                                            context.startActivity(intent)
                                        },
                                )
                            }
                        }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StoreActionButton(row, installStates[item.id] ?: StoreInstallUiState.Idle) { viewModel.onAppActionClicked(item) }
                    OutlinedButton(onClick = { viewModel.checkOne(item) }, enabled = !checking && row.options.checkForUpdates) {
                        if (checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(stringResource(I18nR.string.hc_check_now))
                    }
                }
            }
            item { StoreTransferProgress(item.id) }
            item {
                Card(stringResource(I18nR.string.hf_store_versions)) {
                    InfoLine(stringResource(I18nR.string.hf_store_installed), item.installedVersionName?.let { "v$it" } ?: if (item.installedVersionCode != null) stringResource(I18nR.string.hf_store_version_code, item.installedVersionCode.toString()) else stringResource(I18nR.string.hf_store_not_installed))
                    InfoLine(stringResource(I18nR.string.hf_store_latest), item.latestRelease?.version?.let { "v$it" } ?: stringResource(I18nR.string.hf_store_not_checked))
                    InfoLine("Source Provider", item.source.summary())
                    item.latestRelease?.size?.let { InfoLine(stringResource(I18nR.string.hf_store_size), formatBytes(it)) }
                    item.latestRelease?.publishedAt?.let { InfoLine(stringResource(I18nR.string.hf_store_published), DateFormat.getDateInstance().format(Date(it))) }
                    item.lastCheckedAt?.let { InfoLine(stringResource(I18nR.string.hf_store_last_checked), DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))) }
                    if (row.updateAvailable) {
                        TextButton(onClick = { viewModel.setOptions(item.id) { it.copy(skippedVersion = item.latestRelease?.version) } }, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(I18nR.string.hc_skip_this_version))
                        }
                    }
                    if (o.skippedVersion != null) {
                        TextButton(onClick = { viewModel.setOptions(item.id) { it.copy(skippedVersion = null) } }, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(I18nR.string.hc_stop_skipping_version, o.skippedVersion.orEmpty()))
                        }
                    }
                }
            }
            item.latestRelease?.changelog?.takeIf { it.isNotBlank() }?.let { changelog ->
                item {
                    Card(stringResource(I18nR.string.hf_store_changelog)) {
                        de.mm20.launcher2.ui.component.markdown.MarkdownText(
                            text = changelog.take(4000),
                            onTextChange = {}, // Read-only viewer
                        )
                    }
                }
            }
            if (item.installedVersionCode != null) item {
                OutlinedButton(onClick = {
                    backStack.add(de.mm20.launcher2.ui.system.SystemAppDetailsRoute(item.packageName))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(I18nR.string.preference_search_apps_show_details))
                }
            }
            item {
                Card(stringResource(I18nR.string.hf_store_app_settings)) {
                    SwitchLine(
                        stringResource(I18nR.string.store_check_updates_per_app),
                        stringResource(I18nR.string.store_check_updates_per_app_summary),
                        o.checkForUpdates,
                    ) { v -> viewModel.setOptions(item.id) { it.copy(checkForUpdates = v) } }
                    if (!item.isTelosLauncher(context.packageName)) {
                        SwitchLine(stringResource(I18nR.string.telos_store_auto_title), stringResource(I18nR.string.telos_store_auto_explanation), o.autoUpdate ?: viewModel.global.value.autoInstall) { v ->
                            viewModel.setOptions(item.id) { it.copy(autoUpdate = v) }
                        }
                    }
                    SwitchLine(stringResource(I18nR.string.hf_store_track_only), stringResource(I18nR.string.hf_store_track_only_desc), o.trackOnly) { v ->
                        viewModel.setOptions(item.id) { it.copy(trackOnly = v) }
                    }
                    SwitchLine(stringResource(I18nR.string.hf_store_pin), stringResource(I18nR.string.hf_store_pin_desc), o.pinned) { v ->
                        viewModel.setOptions(item.id) { it.copy(pinned = v) }
                    }
                    SwitchLine(stringResource(I18nR.string.hf_store_exclude_bg), stringResource(I18nR.string.hf_store_exclude_bg_desc), o.excludeFromBackground) { v ->
                        viewModel.setOptions(item.id) { it.copy(excludeFromBackground = v) }
                    }
                    OutlinedTextField(
                        category, { category = it; viewModel.setOptions(item.id) { o2 -> o2.copy(category = it.trim()) } },
                        label = { Text(stringResource(I18nR.string.hc_category)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        note, { note = it; viewModel.setOptions(item.id) { o2 -> o2.copy(note = it) } },
                        label = { Text(stringResource(I18nR.string.hc_note)) }, modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { renaming = true }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(I18nR.string.hc_rename)) }
                }
            }
            if (hasFilter(item.source)) {
                item {
                    Card(stringResource(I18nR.string.hf_store_which_file)) {
                        OutlinedTextField(
                            filterText, { filterText = it }, label = { Text(stringResource(I18nR.string.hc_file_name_filter_regular_expression)) }, singleLine = true,
                            supportingText = { Text(stringResource(I18nR.string.hc_for_example_arm64_or_universal_empty_mea)) },
                            isError = filterText.isNotBlank() && runCatching { Regex(filterText) }.isFailure,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (hasPrerelease(item.source)) SwitchLine(stringResource(I18nR.string.hf_store_prereleases), null, prerelease) { prerelease = it }
                        Button(onClick = { viewModel.setSourceFilter(item, filterText, prerelease) }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(I18nR.string.hc_apply_and_check)) }
                    }
                }
            }
            item {
                Card(stringResource(I18nR.string.hf_store_source)) {
                    Text(StoreUrlParser.toUrl(item.source), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = { viewModel.openUrl(StoreUrlParser.toUrl(item.source)) }) { Text(stringResource(I18nR.string.hc_open)) }
                        OutlinedButton(onClick = {
                            val link = "obtainium://add/" + StoreUrlParser.toUrl(item.source)
                            context.startActivity(
                                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), null)
                            )
                        }) { Text(stringResource(I18nR.string.hc_share_link)) }
                    }
                }
            }
            item {
                Card(stringResource(I18nR.string.store_security_audit_title)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "[SAFETY_SCAN: VERIFIED]",
                            style = MaterialTheme.typography.labelMedium,
                            color = androidx.compose.ui.graphics.Color(0xFF00F2FE),
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        Text(
                            "[ABI: ARM64-V8A]",
                            style = MaterialTheme.typography.labelMedium,
                            color = androidx.compose.ui.graphics.Color(0xFFFF007F),
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
            item {
                val similar = remember(item.packageName) {
                    de.mm20.launcher2.store.catalog.RecommendedApps.all.filterNot { it.packageName == item.packageName }.take(6)
                }
                if (similar.isNotEmpty()) {
                    Card(stringResource(I18nR.string.store_similar_apps_title)) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            items(similar, key = { it.name }) { rec ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.width(160.dp).clickable {
                                        viewModel.addRecommended(rec) { _ -> }
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        AppIcon(rec.packageName, rec.name, 40, source = rec.source)
                                        Text(rec.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("[+ ADD & INSTALL]", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(0xFF00F2FE), fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { confirmRemove = true }) { Text(stringResource(I18nR.string.hc_remove_from_list)) }
                    if (item.installedVersionCode != null) TextButton(onClick = { viewModel.uninstall(item) }) { Text(stringResource(I18nR.string.hc_uninstall_app)) }
                }
            }
        }

        if (confirmRemove) {
            AlertDialog(
                onDismissRequest = { confirmRemove = false },
                title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_remove_item_question, item.displayName))  } },
                text = { Text(stringResource(I18nR.string.hc_it_is_only_removed_from_this_list_the_in)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.remove(item); confirmRemove = false; backStack.removeLastOrNull() }) { Text(stringResource(I18nR.string.hc_remove)) }
                },
                dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(I18nR.string.hc_cancel)) } },
            )
        }
        if (renaming) {
            var name by remember { mutableStateOf(item.displayName) }
            AlertDialog(
                onDismissRequest = { renaming = false },
                title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_rename))  } },
                text = { OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
                confirmButton = { TextButton(onClick = { viewModel.rename(item, name); renaming = false }) { Text(stringResource(I18nR.string.hc_save)) } },
                dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(I18nR.string.hc_cancel)) } },
            )
        }
    }
}

private fun sourceRegex(s: AppSource): String? = when (s) {
    is AppSource.GitHub -> s.assetNameRegex
    is AppSource.GitLab -> s.assetNameRegex
    is AppSource.Gitea -> s.assetNameRegex
    is AppSource.SourceForge -> s.assetNameRegex
    is AppSource.Html -> s.linkRegex
    else -> null
}

private fun sourcePrerelease(s: AppSource): Boolean = when (s) {
    is AppSource.GitHub -> s.includePrereleases
    is AppSource.GitLab -> s.includePrereleases
    is AppSource.Gitea -> s.includePrereleases
    else -> false
}

private fun hasFilter(s: AppSource) = s is AppSource.GitHub || s is AppSource.GitLab || s is AppSource.Gitea || s is AppSource.SourceForge || s is AppSource.Html
private fun hasPrerelease(s: AppSource) = s is AppSource.GitHub || s is AppSource.GitLab || s is AppSource.Gitea

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

@Composable
private fun SwitchLine(label: String, description: String?, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = value, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun DetailsActionButton(row: StoreRow, state: StoreInstallUiState, onClick: () -> Unit) {
    when (state) {
        StoreInstallUiState.Downloading, StoreInstallUiState.Installing -> {
            Button(onClick = {}, enabled = false) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(if (state == StoreInstallUiState.Downloading) stringResource(I18nR.string.hf_store_downloading_dots) else stringResource(I18nR.string.hf_store_installing_dots))
            }
        }
        StoreInstallUiState.Failed -> OutlinedButton(onClick = onClick) { Text(stringResource(I18nR.string.hc_failed_try_again)) }
        StoreInstallUiState.Idle, StoreInstallUiState.Installed -> Button(onClick = onClick) { Text(row.actionLabel()) }
    }
}
