package de.mm20.launcher2.ui.launcher.search.webappshortcut

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.preferences.WebAppGroup
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.component.ToolbarAction
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.settings.webapps.EditWebAppShortcutSheet
import de.mm20.launcher2.ui.webappspanel.WebAppsPanelManager
import de.mm20.launcher2.webappshortcuts.WebAppShortcutRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun WebAppShortcutItem(
    modifier: Modifier = Modifier,
    shortcut: WebAppShortcut,
    inDock: Boolean = false,
    onRemoveFromDock: (() -> Unit)? = null,
    onReplaceInDock: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    val viewModel: SearchableItemVM = listItemViewModel(key = "search-${shortcut.key}")
    val iconSize = LocalGridSettings.current.iconSize.dp.toPixels()

    LaunchedEffect(shortcut, iconSize) {
        viewModel.init(shortcut, iconSize.toInt())
    }

    Column(
        modifier = modifier
            .padding(16.dp),
    ) {
        Text(
            text = shortcut.labelOverride ?: shortcut.label,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            text = shortcut.url,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        val toolbarActions = mutableListOf<ToolbarAction>()

        if (LocalFavoritesEnabled.current) {
            val isPinned by viewModel.isPinned.collectAsState(false)
            val favAction = if (isPinned) {
                DefaultToolbarAction(
                    label = stringResource(I18nR.string.menu_favorites_unpin),
                    icon = BaseR.drawable.star_24px_filled,
                    action = {
                        viewModel.unpin()
                        onBack?.invoke()
                    }
                )
            } else {
                DefaultToolbarAction(
                    label = stringResource(I18nR.string.menu_favorites_pin),
                    icon = BaseR.drawable.star_24px,
                    action = {
                        viewModel.pin()
                        onBack?.invoke()
                    })
            }
            toolbarActions.add(favAction)
        }

        var showEditSheet by remember { mutableStateOf(false) }
        toolbarActions.add(
            DefaultToolbarAction(
                label = stringResource(I18nR.string.web_app_shortcut_edit),
                icon = BaseR.drawable.tune_24px,
                action = { showEditSheet = true }
            )
        )

        var showDeleteDialog by remember { mutableStateOf(false) }
        toolbarActions.add(
            DefaultToolbarAction(
                label = stringResource(I18nR.string.menu_delete),
                icon = BaseR.drawable.delete_24px,
                action = { showDeleteDialog = true }
            )
        )

        if (inDock) {
            toolbarActions.add(
                DefaultToolbarAction(
                    label = stringResource(I18nR.string.dock_menu_remove),
                    icon = BaseR.drawable.delete_24px,
                    action = {
                        onRemoveFromDock?.invoke()
                        onBack?.invoke()
                    }
                )
            )
            toolbarActions.add(
                DefaultToolbarAction(
                    label = stringResource(I18nR.string.dock_menu_replace),
                    icon = BaseR.drawable.autorenew_24px,
                    action = {
                        onReplaceInDock?.invoke()
                        onBack?.invoke()
                    }
                )
            )
        }

        Toolbar(
            leftActions = if (onBack != null) listOf(
                DefaultToolbarAction(
                    stringResource(id = I18nR.string.menu_back),
                    icon = BaseR.drawable.arrow_back_24px,
                    action = onBack
                )
            ) else emptyList(),
            rightActions = toolbarActions
        )

        val webAppShortcutRepository: WebAppShortcutRepository = koinInject()
        val panelManager: WebAppsPanelManager = koinInject()
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { TelosCyberHeading {  Text(stringResource(I18nR.string.web_app_delete_title))  } },
                text = { Text(stringResource(I18nR.string.web_app_delete_message, shortcut.labelOverride ?: shortcut.label)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        panelManager.delete(shortcut)
                        onBack?.invoke()
                    }) { Text(stringResource(I18nR.string.menu_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
        EditWebAppShortcutSheet(
            expanded = showEditSheet,
            existing = shortcut,
            onSave = { label, url, iconUri, faviconUrl, rendererPackage, showInGrid, showInPanel, iconSource, customCss, notificationsEnabled, groupId, adBlockMode, cookieOptions ->
                webAppShortcutRepository.update(
                    shortcut, label, url, iconUri, faviconUrl, rendererPackage,
                    showInGrid, showInPanel, shortcut.order, iconSource, customCss,
                    notificationsEnabled, adBlockMode, cookieOptions,
                )
                val browsingSettings: de.mm20.launcher2.preferences.ui.WebAppBrowsingSettings = org.koin.java.KoinJavaComponent.getKoin().get()
                kotlinx.coroutines.MainScope().launch {
                    val groups: List<WebAppGroup> = browsingSettings.groups.first()
                    val currentGroups = groups.map { g ->
                        if (g.id == groupId) {
                            if (!g.appKeys.contains(shortcut.key)) g.copy(appKeys = g.appKeys + shortcut.key) else g
                        } else {
                            g.copy(appKeys = g.appKeys - shortcut.key)
                        }
                    }
                    browsingSettings.setGroups(currentGroups)
                }
                showEditSheet = false
            },
            onDismiss = { showEditSheet = false },
            onImportIcon = { uri, sizePx -> panelManager.importIcon(uri, sizePx) },
            onFindFavicon = { url -> panelManager.findFavicon(url) },
            onExportIconPackIcon = { customIcon, sizePx -> panelManager.exportIconPackIcon(customIcon, sizePx) },
        )
    }
}

@Composable
fun WebAppShortcutItemGridPopup(
    shortcut: WebAppShortcut,
    show: MutableTransitionState<Boolean>,
    origin: IntRect,
    inDock: Boolean = false,
    onRemoveFromDock: (() -> Unit)? = null,
    onReplaceInDock: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        show,
        enter = expandIn(
            animationSpec = tween(300),
            expandFrom = Alignment.Center,
        ) { origin.size },
        exit = shrinkOut(
            animationSpec = tween(300),
            shrinkTowards = Alignment.Center,
        ) { origin.size },
    ) {
        WebAppShortcutItem(
            modifier = Modifier.fillMaxWidth(),
            shortcut = shortcut,
            inDock = inDock,
            onRemoveFromDock = onRemoveFromDock,
            onReplaceInDock = onReplaceInDock,
            onBack = onDismiss,
        )
    }
}
