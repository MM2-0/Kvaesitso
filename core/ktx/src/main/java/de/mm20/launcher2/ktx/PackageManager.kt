package de.mm20.launcher2.ktx

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

fun PackageManager.getApplicationInfoOrNull(
    packageName: String,
    flags: Int = 0,
): ApplicationInfo? {
    return try {
        getApplicationInfo(packageName, flags)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}

fun PackageManager.getApplicationIconOrNull(
    packageName: String,
): Drawable? {
    return try {
        getApplicationIcon(packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}