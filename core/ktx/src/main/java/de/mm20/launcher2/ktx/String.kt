package de.mm20.launcher2.ktx

import com.github.promeg.pinyinhelper.Pinyin
import org.apache.commons.lang3.StringUtils
import java.net.URLDecoder
import java.util.*

fun String.decodeUrl(charset: String): String? {
    return URLDecoder.decode(this, charset)
}

/**
 * Normalize a string to lowercase string
 * This is used for substring matching.
 * Characters must be normalized independently so that
 * A.contains(B) -> A.normalize().contains(B.normalize()) is true.
 */
/*fun String.normalize(): String {
    return StringUtils.stripAccents(this.romanize().lowercase(Locale.getDefault()))
        .replace("æ", "ae")
        .replace("œ", "oe")
        .replace("ß", "ss")
}

/**
 * Romanize a string, transliterate non-latin characters into latin
 * This is used for sorting. The resulting string represents the position where the original
 * string should be sorted in the latin alphabet.
 */
fun String.romanize(): String {
    return Pinyin.toPinyin(this, "")
}*/

fun String.stripStartOrNull(s: String): String?
    = if (startsWith(s)) removePrefix(s) else null

fun String.stripEndOrNull(s: String): String?
    = if (endsWith(s)) removeSuffix(s) else null