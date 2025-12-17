package de.mm20.launcher2.ktx

import java.net.URLDecoder

fun String.decodeUrl(charset: String): String? {
    return URLDecoder.decode(this, charset)
}

fun String.stripStartOrNull(s: String): String?
    = if (startsWith(s)) removePrefix(s) else null

fun String.stripEndOrNull(s: String): String?
    = if (endsWith(s)) removeSuffix(s) else null