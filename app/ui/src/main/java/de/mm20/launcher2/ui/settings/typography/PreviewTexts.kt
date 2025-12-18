package de.mm20.launcher2.ui.settings.typography

import android.icu.util.ULocale

/**
 * Preview texts to demonstrate typography settings in the users dominant script.
 * Preview texts should be similar in length, and they should be language-neutral, and not use
 * letters that are used only in some languages.
 */
interface PreviewTexts {
    /**
     * Preview for the font. Should be a character that is representative for a font.
     */
    val ExtraShort: String

    /**
     * A short, 3-letter preview.
     */
    val Short1: String

    /**
     * An alternative to [Short1], also 3 letters.
     */
    val Short2: String
    /**
     * A medium-length preview, 5-6 letters.
     */
    val Medium1: String
    /**
     * An alternative to [Medium1], also 5-6 letters.
     */
    val Medium2: String
    /**
     * A longer preview, to demonstrate line height.
     * Should contain letters and numbers, and a newline.
     */
    val TwoLines: String

    companion object {
        operator fun invoke(): PreviewTexts {
            val script = ULocale.addLikelySubtags(ULocale.getDefault()).script
            return forScript(script)
        }

        /**
         * Returns the appropriate [PreviewTexts] implementation based on the script.
         * Defaults to [LatinPreviewTexts] if the script is not recognized.
         * @param script the ISO-15924 script code
         */
        fun forScript(script: String): PreviewTexts {
            return when (script) {
                "Latn" -> LatinPreviewTexts
                "Cyrl" -> CyrillicPreviewTexts
                "Grek" -> GreekPreviewTexts
                else -> LatinPreviewTexts
            }
        }
    }
}

object LatinPreviewTexts: PreviewTexts {
    override val ExtraShort: String = "Aa"
    override val Short1: String = "Abc"
    override val Short2: String = "Deg"
    override val Medium1: String = "Abcdeg"
    override val Medium2: String = "Hilmno"
    override val TwoLines: String = "Abcdeghilm Nop\nRst 123456890"
}

object CyrillicPreviewTexts: PreviewTexts {
    override val ExtraShort: String = "Аа"
    override val Short1: String = "Абв"
    override val Short2: String = "Где"
    override val Medium1: String = "Абвгде"
    override val Medium2: String = "Жзиклм"
    override val TwoLines: String = "Абвгдежзик Лмн\nОпр 1234567890"
}

object GreekPreviewTexts: PreviewTexts {
    override val ExtraShort: String = "Αα"
    override val Short1: String = "Αβγ"
    override val Short2: String = "Δεζ"
    override val Medium1: String = "Αβγδεζ"
    override val Medium2: String = "Ηθικλμ"
    override val TwoLines: String = "Αβγδεζηθ Ικλμ\nΝξο 1234567890"
}


