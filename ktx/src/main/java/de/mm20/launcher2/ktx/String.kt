package de.mm20.launcher2.ktx

import com.github.promeg.pinyinhelper.Pinyin
import java.net.URLDecoder
import java.util.*

fun String.decodeUrl(charset: String): String? {
    return URLDecoder.decode(this, charset)
}

/**
 * Normalize a string to lowercase ASCII
 * TODO: Only supports Chinese/Pinyin at the moment
 */
fun String.normalize(): String {
    return this.romanize().lowercase(Locale.getDefault())
}

/**
 * Romanize a string, transliterate non-latin characters into latin
 * TODO: Only supports Chinese/Pinyin at the moment
 */
fun String.romanize(): String {
    return Pinyin.toPinyin(this, "")
}