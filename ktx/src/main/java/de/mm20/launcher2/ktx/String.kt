package de.mm20.launcher2.ktx

import java.net.URLDecoder

fun String.decodeUrl(charset: String): String? {
    return URLDecoder.decode(this, charset)
}