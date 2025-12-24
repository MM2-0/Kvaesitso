package de.mm20.launcher2.data

import de.mm20.launcher2.search.StringNormalizer
import org.apache.commons.lang3.StringUtils
import java.util.Locale

/**
 * Pre Android 10 StringNormalizer. Only strips accents from latin characters
 */
internal class CompatStringNormalizer: StringNormalizer  {

    override val id: String = "null"

    override fun normalize(input: String): String {
        return StringUtils.stripAccents(input.lowercase(Locale.getDefault()))
            .replace("æ", "ae")
            .replace("œ", "oe")
            .replace("ß", "ss")
    }
}