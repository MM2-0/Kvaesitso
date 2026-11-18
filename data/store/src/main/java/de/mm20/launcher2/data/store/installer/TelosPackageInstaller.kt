package de.mm20.launcher2.data.store.installer

import android.content.Context
import de.mm20.launcher2.i18n.R
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.store.installer.AppInstaller
import de.mm20.launcher2.store.installer.InstallResult
import java.io.File
import java.io.InputStream

/** User-selected installation path, with journaled freeze restoration. */
class TelosPackageInstaller(
    private val context: Context,
    private val freezeManager: de.mm20.launcher2.freeze.FreezeManager,
    private val options: de.mm20.launcher2.store.options.StoreOptions,
) : AppInstaller {

    private val shizuku = ShizukuInstallBackend(context)
    private val root = RootInstallBackend()
    private val sessionApi = SessionApiInstallBackend(context)

    /** Ordered, privileged backends tried before falling back to [sessionApi]. */
    private val privilegedBackends: List<InstallBackend> = listOf(shizuku, root)

    override suspend fun isAvailable(): Boolean {
        // sessionApi.isAvailable() is always true - the standard API works unconditionally.
        return true
    }

    override suspend fun canInstallSilently(): Boolean = when (options.global.value.installer) {
        "shizuku" -> shizuku.isAvailable() && shizuku.hasPermission()
        "root" -> root.isAvailable() && root.hasPermission()
        "session" -> isDeviceOwner()
        else -> false
    }

    override fun externalInstallers(): List<de.mm20.launcher2.store.installer.InstallerChoice> =
        externalInstallerActivities(context).map { info ->
            de.mm20.launcher2.store.installer.InstallerChoice(
                "external:" + android.content.ComponentName(info.activityInfo.packageName, info.activityInfo.name).flattenToString(),
                info.loadLabel(context.packageManager).toString())
        }

    private suspend fun freezeBackend(): String? {
        freezeManager.refreshBackendState()
        return when {
            shizuku.isAvailable() && shizuku.hasPermission() -> "Shizuku"
            root.isAvailable() && root.hasPermission() -> "Root"
            isDeviceOwner() -> "DeviceOwner"
            else -> null
        }
    }

    private fun isDeviceOwner() = context.getSystemService(android.app.admin.DevicePolicyManager::class.java)
        ?.isDeviceOwnerApp(context.packageName) == true

    private fun failed(cause: Throwable? = null, details: String? = cause?.message) = InstallResult.Failed(
        context.getString(de.mm20.launcher2.i18n.R.string.au_store_msg_install_failed) +
            details?.takeIf { it.isNotBlank() }?.take(8192)?.let { "\n$it" }.orEmpty(), cause)

    override suspend fun install(apk: File, packageName: String): InstallResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { installOnIo(apk, packageName) }

    private suspend fun installOnIo(apk: File, packageName: String): InstallResult {
        if (freezeManager.hasPendingFrozenUpdate(packageName)) return InstallResult.Failed(context.getString(de.mm20.launcher2.i18n.R.string.hf_store_installing))
        
        StoreTerminalLogs.log("SECURITY", "Scanning APK $packageName...")
        val securityReport = de.mm20.launcher2.data.store.security.ApkSecurityScanner.scanApk(context, apk)
        if (securityReport != null) {
            StoreTerminalLogs.log("SECURITY", "Risk Level: ${securityReport.riskLevel.name}")
        }

        StoreTerminalLogs.log("SIGNATURE", "Verifying certificate signature match for $packageName...")
        val signatureMatch = de.mm20.launcher2.data.store.security.ApkSecurityScanner.verifyInstalledSignatureMatch(context, apk)
        if (!signatureMatch) {
            StoreTerminalLogs.log("ERROR", "Signature mismatch for $packageName! Install blocked.")
            return failed(IllegalStateException("APK signature does not match installed application! Update blocked for security."))
        }

        // Automatic Pre-Update Rollback Backup
        runCatching {
            val pm = context.packageManager
            val flags = android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS or android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
            val info = pm.getApplicationInfo(packageName, flags)
            val installedApk = File(info.sourceDir)
            if (installedApk.exists()) {
                val backupDir = File(context.cacheDir, "apk-backups").apply { mkdirs() }
                val backupFile = File(backupDir, "$packageName-backup.apk")
                installedApk.copyTo(backupFile, overwrite = true)
                StoreTerminalLogs.log("BACKUP", "Created pre-update rollback backup for $packageName")
            }
        }

        val archive = context.packageManager.getPackageArchiveInfo(apk.path, 0)
            ?: return failed(IllegalArgumentException("Invalid APK"))
        if (archive?.packageName != packageName) return failed(IllegalArgumentException("APK package does not match the selected app"))
        if (freezeManager.isFrozen(packageName) && !options.global.value.updateFrozenApps)
            return InstallResult.Failed(context.getString(de.mm20.launcher2.i18n.R.string.store_update_frozen))
        val selected = options.global.value.installer
        val selectedBackend = when (selected) { "shizuku" -> shizuku; "root" -> root; else -> null }
        for (backend in listOfNotNull(selectedBackend)) {
            if (!backend.isAvailable() || !backend.requestPermission())
                return failed(IllegalStateException("$selected is not available or authorized"))
            var result: BackendInstallResult = BackendInstallResult.Failed("Install did not start")
            var restored = true
            var lease: String? = null
            try {
                lease = freezeManager.beginFrozenUpdate(packageName,
                    if (backend === shizuku) "Shizuku" else "Root", thawFirst = true)
                result = backend.install(apk, packageName, allowDowngrade = options.global.value.allowDowngrade)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                result = BackendInstallResult.Failed(error.message ?: "Frozen update failed", error)
            } finally {
                restored = lease?.let { freezeManager.finishFrozenUpdate(packageName, it) } ?: true
            }
            if (!restored) return InstallResult.Failed(
                context.getString(de.mm20.launcher2.i18n.R.string.freeze_restore_all) + ": " +
                    context.getString(de.mm20.launcher2.i18n.R.string.pdft_error_title))
            return when (val installed = result) {
                is BackendInstallResult.Success -> {
                    // Shizuku/root installs finish synchronously (unlike the session API path,
                    // whose result arrives later via InstallResultReceiver) - persist the new
                    // installedVersionCode to Room right here so the UI unsticks immediately.
                    val versionCode = resolveInstalledVersionCode(context, packageName)
                    AppDatabase.getInstance(context).storeItemDao()
                        .updateInstalledVersion(packageName, versionCode)
                    InstallResult.Success
                }
                is BackendInstallResult.Failed -> {
                    CrashReporter.logException(Exception("${backend::class.simpleName}: ${installed.reason}", installed.cause))
                    // Fall back to the default package installer prompt if privileged install fails
                    android.util.Log.w("TelosPackageInstaller", "Privileged install failed, falling back to session installer")
                    break // breaks the 'for (backend in ...)' loop to fall through to sessionApi
                }
            }
        }

        var pending = false
        var lease: String? = null
        try {
            val frozen = freezeManager.isFrozen(packageName)
            val restorationBackend = if (frozen) freezeBackend()
                ?: return InstallResult.Failed(context.getString(de.mm20.launcher2.i18n.R.string.freeze_no_backend_available))
                else "DeviceOwner"
            lease = freezeManager.beginFrozenUpdate(packageName, restorationBackend)
            if (selected.startsWith("external:")) {
                val component = android.content.ComponentName.unflattenFromString(selected.removePrefix("external:"))
                    ?: return failed(IllegalStateException("Installer is no longer available"))
                val available = externalInstallerActivities(context).any {
                    it.activityInfo.packageName == component.packageName && it.activityInfo.name == component.className
                }
                if (!available) return failed(IllegalStateException("Installer is no longer available"))
                // Copy into our provider's cache; external apps cannot read a private download path.
                val shared = File(context.cacheDir, "store-handoff").apply { mkdirs() }
                shared.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
                val copy = File(shared, java.util.UUID.randomUUID().toString() + ".apk")
                apk.inputStream().use { input -> copy.outputStream().use { input.copyTo(it, 64 * 1024) } }
                context.startActivity(android.content.Intent(context, ExternalInstallActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("component", component.flattenToString())
                    putExtra("file", copy.path)
                    putExtra(InstallResultReceiver.EXTRA_PACKAGE_NAME, packageName)
                    putExtra(InstallResultReceiver.EXTRA_FREEZE_TOKEN, lease)
                    putExtra("version", if (android.os.Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong())
                })
                pending = true
                return InstallResult.Pending
            }
            if (selected != "session" && selected != "shizuku" && selected != "root") return failed(IllegalStateException("Unknown installer: $selected"))
            return when (val result = sessionApi.install(apk, packageName, lease)) {
                is BackendInstallResult.Success -> { pending = true; InstallResult.Pending }
                is BackendInstallResult.Failed -> failed(result.cause, result.reason)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            CrashReporter.logException(error)
            return failed(error)
        } finally {
            if (!pending) lease?.let { freezeManager.finishFrozenUpdate(packageName, it) }
        }
    }

    /** Spool in bounded chunks so streams use the same package validation and freeze policy. */
    suspend fun installFromStream(apkStream: InputStream, packageName: String, sizeBytes: Long = -1): InstallResult {
        val temporary = File.createTempFile("store-install-", ".apk", context.cacheDir)
        return try {
            temporary.outputStream().use { apkStream.copyTo(it, 64 * 1024) }
            install(temporary, packageName)
        } finally { temporary.delete() }
    }
}
