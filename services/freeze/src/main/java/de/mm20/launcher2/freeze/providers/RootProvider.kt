package de.mm20.launcher2.freeze.providers

import de.mm20.launcher2.freeze.PrivilegedAccessProvider
import de.mm20.launcher2.freeze.enabledAction
import de.mm20.launcher2.ktx.TelosShellEngine

/**
 * Runs `pm suspend`/`pm unsuspend` through a root shell. Requesting permission and checking
 * availability are the same operation here: invoking `su` is what triggers the device's
 * superuser-grant prompt (Magisk, KernelSU, ...) on first use.
 */
internal class RootProvider : PrivilegedAccessProvider {

    private var hasRoot: Boolean? = null

    private val userId: Int
        get() = android.os.Process.myUid() / 100000

    override suspend fun isAvailable(): Boolean {
        hasRoot?.let { return it }
        val granted = TelosShellEngine.isRootAvailable()
        // Only a positive result is cached: a denied/cancelled su prompt must not lock root out until
        // the process restarts, the user may grant it later.
        if (granted) hasRoot = true
        return granted
    }

    override suspend fun hasPermission(): Boolean = isAvailable()

    override suspend fun requestPermission(): Boolean = isAvailable()

    // === TELOS_PENDING_REVIEW_START: multi_user_freeze ===
    override suspend fun setPackagesSuspended(packageNames: List<String>, suspended: Boolean, userId: Int): Set<String> {
        val action = if (suspended) "suspend" else "unsuspend"
        return packageNames.filterTo(mutableSetOf()) { pkg ->
            TelosShellEngine.isValidPackageName(pkg) && runAsRoot("pm $action --user $userId $pkg")
        }
    }

    override suspend fun setPackagesEnabled(packageNames: List<String>, enabled: Boolean, userId: Int): Set<String> {
        val action = if (enabled) "enable" else "disable-user"
        return packageNames.filterTo(mutableSetOf()) { pkg ->
            TelosShellEngine.isValidPackageName(pkg) && runAsRoot("pm $action --user $userId $pkg")
        }
    }

    override suspend fun setEnabledSetting(packageName: String, state: Int, userId: Int): Boolean =
        TelosShellEngine.isValidPackageName(packageName) && enabledAction(state)?.let { runAsRoot("pm $it --user $userId '$packageName'") } ?: false

    override suspend fun setComponentSetting(component: String, state: Int, userId: Int): Boolean =
        component.matches(Regex("[A-Za-z0-9_.$/]+")) && enabledAction(state)?.let { runAsRoot("pm $it --user $userId '$component'", 10) } ?: false

    override suspend fun setHidden(packageName: String, hidden: Boolean, userId: Int): Boolean =
        TelosShellEngine.isValidPackageName(packageName) && runAsRoot("pm ${if (hidden) "hide" else "unhide"} --user $userId $packageName")

    override suspend fun forceStopPackage(packageName: String, userId: Int): Boolean {
        if (!TelosShellEngine.isValidPackageName(packageName)) return false
        return runAsRoot("am force-stop --user $userId $packageName")
    }

    override suspend fun clearCache(packageName: String, userId: Int): Boolean {
        if (!TelosShellEngine.isValidPackageName(packageName)) return false
        // Clearing only cache to avoid data loss
        return runAsRoot("rm -rf /data/user/$userId/$packageName/cache/*")
    }
    // === TELOS_PENDING_REVIEW_END: multi_user_freeze ===

    private suspend fun runAsRoot(command: String, timeoutSeconds: Long? = null): Boolean {
        val timeoutMs = (timeoutSeconds ?: 30L) * 1_000L
        return TelosShellEngine.execRootResult(
            command = command,
            timeoutMs = timeoutMs,
        ).isSuccess
    }
}
