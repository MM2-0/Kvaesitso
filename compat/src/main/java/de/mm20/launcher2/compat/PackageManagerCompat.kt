package de.mm20.launcher2.compat

import android.content.pm.PackageManager
import android.os.Build

object PackageManagerCompat {
    fun getInstallSource(
        packageManager: PackageManager,
        packageName: String
    ): InstallSourceInfoCompat {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val installSourceInfo = packageManager.getInstallSourceInfo(packageName)
            return InstallSourceInfoCompat(
                originatingPackageName = installSourceInfo.originatingPackageName,
                initiatingPackageName = installSourceInfo.initiatingPackageName,
                installingPackageName = installSourceInfo.installingPackageName,
            )
        } else {
            val installerPackageName = packageManager.getInstallerPackageName(packageName)
            return InstallSourceInfoCompat(
                originatingPackageName = installerPackageName,
                initiatingPackageName = installerPackageName,
                installingPackageName = installerPackageName
            )
        }
    }
}

data class InstallSourceInfoCompat(
    val originatingPackageName: String?,
    val initiatingPackageName: String?,
    val installingPackageName: String?
)