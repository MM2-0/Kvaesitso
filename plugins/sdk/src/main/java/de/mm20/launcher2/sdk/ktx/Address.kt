package de.mm20.launcher2.sdk.ktx

import android.location.Address

internal fun Address.formatToString(
): String {
    val sb = StringBuilder()
    if (locality != null) sb.append(locality).append(", ")
    if (countryCode != null) sb.append(countryCode)
    return sb.toString()
}