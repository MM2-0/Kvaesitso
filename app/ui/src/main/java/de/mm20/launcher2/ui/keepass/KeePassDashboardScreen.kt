package de.mm20.launcher2.ui.keepass

import de.mm20.launcher2.ui.component.TelosCyberHeading

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryValue
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosCyberAppBarTitle
import de.mm20.launcher2.ui.component.TelosCyberSectionLabel
import de.mm20.launcher2.ui.component.VirtualAppWindow
import de.mm20.launcher2.ui.component.telosTechSurface
import de.mm20.launcher2.ui.component.virtualAppPopup
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.serialization.Serializable

@Serializable
data object KeePassDashboardRoute : NavKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeePassDashboardScreen() {
    val context = LocalContext.current
    val viewModel: KeePassViewModel = viewModel()

    val currentGroup = viewModel.currentGroup.value
    val selectedEntry = viewModel.selectedEntry.value
    val isUnlocked = viewModel.isUnlocked.value
    val unlockError = viewModel.unlockError.value

    var password by remember { mutableStateOf("") }

    val kdbxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && password.isNotEmpty()) {
            viewModel.loadFromUri(context, uri, password)
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && password.isNotEmpty()) {
            viewModel.loadFromSteganography(context, uri, password)
        }
    }

    BackHandler(enabled = isUnlocked && (selectedEntry != null || viewModel.canNavigateUp)) {
        viewModel.navigateUp()
    }

    VirtualAppWindow {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        TelosCyberAppBarTitle(
                            title = if (!isUnlocked) stringResource(I18nR.string.keepass_title)
                            else selectedEntry?.let { entryPlain(it, "Title") }
                                ?: currentGroup?.name
                                ?: stringResource(I18nR.string.keepass_title),
                            status = stringResource(if (isUnlocked) I18nR.string.net_hub_state_on else I18nR.string.keepass_lock),
                            statusColor = if (isUnlocked) TelosCyber.NeonGreen else TelosCyber.NeonMagenta,
                        )
                    },
                    navigationIcon = {
                        if (isUnlocked && (selectedEntry != null || viewModel.canNavigateUp)) {
                            IconButton(onClick = { viewModel.navigateUp() }) {
                                Icon(
                                    painterResource(BaseR.drawable.arrow_back_24px),
                                    contentDescription = stringResource(I18nR.string.hc_back),
                                )
                            }
                        }
                    },
                    actions = {
                        if (isUnlocked) {
                            IconButton(onClick = { viewModel.lock() }) {
                                Icon(
                                    painterResource(BaseR.drawable.lock_24px),
                                    contentDescription = stringResource(I18nR.string.keepass_lock),
                                )
                            }
                        }
                    },
                )
            },
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                when {
                    !isUnlocked -> UnlockPane(
                        password = password,
                        unlockError = unlockError,
                        onPasswordChange = { password = it },
                        onOpenKdbx = { kdbxLauncher.launch("*/*") },
                        onOpenStego = { imageLauncher.launch("image/png") },
                    )
                    selectedEntry != null -> EntryDetailPane(
                        entry = selectedEntry,
                        onCopy = { text -> copyText(context, text) },
                        onClose = { viewModel.closeEntry() },
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        currentGroup?.groups?.let { groups ->
                            items(items = groups, key = { it.uuid.toString() }) { group ->
                                ListItem(
                                    headlineContent = { Text(group.name) },
                                    leadingContent = {
                                        Icon(painterResource(BaseR.drawable.folder_24px), contentDescription = null)
                                    },
                                    modifier = Modifier.clickable { viewModel.openGroup(group) },
                                )
                                HorizontalDivider()
                            }
                        }
                        currentGroup?.entries?.let { entries ->
                            items(items = entries, key = { it.uuid.toString() }) { entry ->
                                val title = entryPlain(entry, "Title")
                                    ?: stringResource(I18nR.string.keepass_unnamed)
                                val username = entryPlain(entry, "UserName").orEmpty()
                                ListItem(
                                    headlineContent = { Text(title) },
                                    supportingContent = {
                                        if (username.isNotEmpty()) Text(username)
                                    },
                                    leadingContent = {
                                        Icon(painterResource(BaseR.drawable.lock_24px), contentDescription = null)
                                    },
                                    modifier = Modifier.clickable { viewModel.openEntry(entry) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnlockPane(
    password: String,
    unlockError: Boolean,
    onPasswordChange: (String) -> Unit,
    onOpenKdbx: () -> Unit,
    onOpenStego: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .telosTechSurface(borderColor = TelosCyber.NeonMagenta, shape = RoundedCornerShape(18.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TelosCyberSectionLabel(stringResource(I18nR.string.keepass_title), accent = TelosCyber.NeonMagenta)
            Icon(
                painter = painterResource(BaseR.drawable.lock_24px),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = TelosCyber.NeonMagenta,
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(I18nR.string.keepass_db_password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (unlockError) {
                Text(
                    stringResource(I18nR.string.keepass_wrong_password),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenKdbx, enabled = password.isNotEmpty()) {
                    Text(stringResource(I18nR.string.keepass_open_kdbx))
                }
                FilledTonalButton(onClick = onOpenStego, enabled = password.isNotEmpty()) {
                    Text(stringResource(I18nR.string.keepass_open_stego))
                }
            }
        }
    }
}

@Composable
private fun EntryDetailPane(
    entry: Entry,
    onCopy: (String) -> Unit,
    onClose: () -> Unit,
) {
    val title = entryPlain(entry, "Title") ?: stringResource(I18nR.string.keepass_unnamed)
    val username = entryPlain(entry, "UserName")
    val password = entryPlain(entry, "Password")
    val url = entryPlain(entry, "URL")
    val notes = entryPlain(entry, "Notes")
    var revealPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = virtualAppPopup(),
        title = { TelosCyberHeading {  Text(title)  } },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EntryFieldRow(
                    label = stringResource(I18nR.string.keepass_entry_username),
                    value = username,
                    onCopy = onCopy,
                )
                EntryFieldRow(
                    label = stringResource(I18nR.string.keepass_entry_password),
                    value = password,
                    masked = !revealPassword,
                    onCopy = onCopy,
                    trailing = {
                        TextButton(onClick = { revealPassword = !revealPassword }) {
                            Text(
                                if (revealPassword) stringResource(I18nR.string.keepass_entry_hide)
                                else stringResource(I18nR.string.keepass_entry_reveal),
                            )
                        }
                    },
                )
                EntryFieldRow(
                    label = stringResource(I18nR.string.keepass_entry_url),
                    value = url,
                    onCopy = onCopy,
                )
                EntryFieldRow(
                    label = stringResource(I18nR.string.keepass_entry_notes),
                    value = notes,
                    onCopy = onCopy,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text(stringResource(I18nR.string.hc_close))
            }
        },
    )
}

@Composable
private fun EntryFieldRow(
    label: String,
    value: String?,
    onCopy: (String) -> Unit,
    masked: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    if (value.isNullOrEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (masked) "••••••••" else value,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
            IconButton(onClick = { onCopy(value) }) {
                Icon(
                    painterResource(BaseR.drawable.content_copy_24px),
                    contentDescription = stringResource(I18nR.string.hc_copy),
                )
            }
        }
    }
}

private fun entryPlain(entry: Entry, key: String): String? {
    val value = entry.fields.entries.firstOrNull { it.key == key }?.value ?: return null
    return if (value is EntryValue.Plain) value.content.takeIf { it.isNotBlank() } else null
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("keepass", text))
}
