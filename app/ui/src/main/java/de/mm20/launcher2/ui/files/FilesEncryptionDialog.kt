package de.mm20.launcher2.ui.files

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import de.mm20.launcher2.helper.PasswordFileEncryption
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/** Single local file only. Remote, archive and vault entries are deliberately not accepted. */
@Composable
fun FilesEncryptionDialog(source: File, decrypt: Boolean, onDismiss: () -> Unit, onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var filename by remember(source, decrypt) { mutableStateOf(if (decrypt) source.name.removeSuffix(".telos-aes").let { if (it == source.name) "$it.decrypted" else it } else "${source.name}.telos-aes") }
    var job by remember { mutableStateOf<Job?>(null) }
    var failure by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    val valid = password.length in (if (decrypt) 1 else 8)..1024 && (decrypt || confirmation == password) &&
        filename.isNotBlank() && filename !in setOf(".", "..") && filename.none { it == '/' || it == '\\' || it == '\u0000' } && filename != source.name
    fun dismiss() {
        if (job?.isActive == true) job?.cancel() else { password = ""; confirmation = ""; onDismiss() }
    }
    AlertDialog(onDismissRequest = { if (job?.isActive != true) dismiss() },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn, dismissOnBackPress = job?.isActive != true, dismissOnClickOutside = job?.isActive != true),
        title = { TelosCyberHeading {  Text(stringResource(if (decrypt) I18nR.string.telos_files_decrypt else I18nR.string.telos_files_encrypt))  } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(source.name, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(I18nR.string.telos_files_encryption_info))
                Text(stringResource(I18nR.string.telos_files_encryption_publish_note), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(filename, { filename = it }, enabled = job?.isActive != true, singleLine = true,
                    label = { Text(stringResource(I18nR.string.telos_files_encryption_output)) })
                OutlinedTextField(password, { if (it.length <= 1024) password = it }, enabled = job?.isActive != true, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(I18nR.string.telos_files_encryption_password)) })
                if (!decrypt) OutlinedTextField(confirmation, { if (it.length <= 1024) confirmation = it }, enabled = job?.isActive != true, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(I18nR.string.telos_files_encryption_confirm)) })
                if (job?.isActive == true) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                if (failure) Text(stringResource(I18nR.string.telos_files_encryption_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = valid && job?.isActive != true, onClick = {
                val secret = password.toCharArray(); password = ""; confirmation = ""; failure = false; progress = 0f
                val output = File(source.parentFile, filename)
                job = scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val staging = File(context.noBackupFilesDir, "file-encryption/${UUID.randomUUID()}")
                            check(staging.mkdirs())
                            val execution = currentCoroutineContext()
                            try {
                                val checkpoint = { execution.ensureActive() }
                                val update: (Long, Long) -> Unit = { done, total -> progress = if (total > 0) (done.toDouble() / total).toFloat() else 1f }
                                if (decrypt) PasswordFileEncryption.decrypt(source, output, secret, staging, checkpoint, update)
                                else PasswordFileEncryption.encrypt(source, output, secret, staging, checkpoint, update)
                            } finally { staging.deleteRecursively() }
                        }
                        onComplete(); onDismiss()
                    } catch (_: CancellationException) { /* Helper cleans up owned work; original stays. */ }
                    catch (_: Exception) { failure = true }
                    finally { secret.fill('\u0000'); job = null }
                }
            }) { Text(stringResource(if (decrypt) I18nR.string.telos_files_decrypt else I18nR.string.telos_files_encrypt)) }
        },
        dismissButton = { TextButton(onClick = ::dismiss) { Text(stringResource(I18nR.string.hc_cancel)) } })
}
