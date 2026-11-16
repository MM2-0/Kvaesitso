package de.mm20.launcher2.data.store.installer

import java.io.File

internal data class InstallCommandResult(val exitCode: Int, val output: String)

internal fun parseInstallSessionId(output: String): String? =
    Regex("""\[(\d+)]""").find(output)?.groupValues?.get(1)
        ?: Regex("""(?i)session(?:\s+id)?[:\s]+(\d+)""").find(output)?.groupValues?.get(1)

internal fun installCommandSucceeded(exitCode: Int, output: String): Boolean =
    exitCode == 0 && (
        output.isBlank() ||
            output.lineSequence().any { line ->
                val text = line.trim()
                text.equals("Success", true) || text.startsWith("Success", true)
            }
        )

/** Explicit sessions keep command failures separate from APK streaming and final commit. */
internal suspend fun installPrivilegedSession(
    apk: File,
    userId: Int,
    preserveEnabled: Boolean = false,
    allowDowngrade: Boolean = false,
    run: suspend (List<String>, File?) -> InstallCommandResult,
): BackendInstallResult {
    require(userId >= 0 && apk.length() > 0)
    val args = mutableListOf("pm", "install-create", "-r", "--user", userId.toString(), "-S", apk.length().toString())
    if (allowDowngrade) args.add("-d")
    var created = run(args + if (preserveEnabled) listOf("--skip-enable") else emptyList(), null)
    if (created.exitCode != 0 && preserveEnabled) created = run(args, null)
    val session = parseInstallSessionId(created.output).takeIf { created.exitCode == 0 }
    var sessionError: String? = created.output
    if (session != null) {
        var committed = false
        try {
            val written = run(listOf("pm", "install-write", "-S", apk.length().toString(), session, "base.apk", "-"), apk)
            if (!installCommandSucceeded(written.exitCode, written.output)) {
                sessionError = "install-write: ${written.output}"
            } else {
                val result = run(listOf("pm", "install-commit", session), null)
                committed = installCommandSucceeded(result.exitCode, result.output)
                if (committed) return BackendInstallResult.Success
                sessionError = "install-commit: ${result.output}"
            }
        } finally {
            if (!committed) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                runCatching { run(listOf("pm", "install-abandon", session), null) }
            }
        }
    } else {
        sessionError = "install-create: ${created.output}"
    }
    val streamed = stdinInstall(apk, userId, preserveEnabled, allowDowngrade, run)
    if (streamed is BackendInstallResult.Success) return streamed
    return BackendInstallResult.Failed(sessionError ?: "install failed")
}

private suspend fun stdinInstall(
    apk: File,
    userId: Int,
    preserveEnabled: Boolean,
    allowDowngrade: Boolean,
    run: suspend (List<String>, File?) -> InstallCommandResult,
): BackendInstallResult {
    var args = apkInstallArguments(apk.length(), userId, preserveEnabled, allowDowngrade)
    var result = run(args, apk)
    if (!installCommandSucceeded(result.exitCode, result.output) && preserveEnabled &&
        unsupportedPreserveFlag(result.output)
    ) {
        args = apkInstallArguments(apk.length(), userId, false, allowDowngrade)
        result = run(args, apk)
    }
    return if (installCommandSucceeded(result.exitCode, result.output)) BackendInstallResult.Success
    else BackendInstallResult.Failed("install: ${result.output}")
}
