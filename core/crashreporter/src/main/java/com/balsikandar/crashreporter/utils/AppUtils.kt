package com.balsikandar.crashreporter.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import java.security.MessageDigest

internal fun getAppSignature(context: Context): String {
    val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val pi = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES
        )
        pi.signingInfo?.apkContentsSigners?.firstOrNull()
    } else {
        val pi = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNATURES
        )
        pi.signatures?.firstOrNull()
    }
    return if (signature != null) {
        val digest = MessageDigest.getInstance("SHA")
        digest.update(signature.toByteArray())
        digest.digest().toHexString(
            HexFormat {
                upperCase = true
                bytes {
                    byteSeparator = ":"
                }
            }
        )
    } else "null"
}