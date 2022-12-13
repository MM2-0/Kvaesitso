package de.mm20.launcher2.search.data

import de.mm20.launcher2.search.Searchable
import java.text.DecimalFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

data class Calculator(
    val term: String,
    val solution: Double
): Searchable {

    val formattedString: String
    val formattedBinaryString: String
    val formattedHexString: String
    val formattedOctString: String

    init {
        if (solution.isNaN()) {
            formattedString = "NaN"
            formattedOctString = "NaN"
            formattedBinaryString = "NaN"
            formattedHexString = "NaN"
        } else {
            val nf =
                if ((abs(solution) > 1e12 || abs(solution) < 1e-5) && solution != 0.0) DecimalFormat(
                    "#.######E0"
                )
                else DecimalFormat("#,###.######")
            formattedString = nf.format(solution)
            var s = StringBuffer(solution.roundToInt().toString(2))
            while (s.length % 4 != 0) {
                s = s.insert(0, '0')
            }

            for (i in s.length - 4 downTo 4 step 4) {
                s.insert(i, ' ')
            }
            formattedBinaryString = s.toString()

            s = StringBuffer(solution.roundToInt().toString(8))
            while (s.length % 3 != 0) {
                s = s.insert(0, '0')
            }

            for (i in s.length - 3 downTo 3 step 3) {
                s.insert(i, ' ')
            }
            formattedOctString = s.toString()

            s = StringBuffer(solution.roundToInt().toString(16).uppercase(Locale.getDefault()))
            while (s.length % 2 != 0) {
                s = s.insert(0, '0')
            }

            for (i in s.length - 2 downTo 2 step 2) {
                s.insert(i, ' ')
            }
            formattedHexString = s.toString()
        }
    }

    fun getBeatifiedTerm(): String {
        if(term.matches(Regex("0x[0-9a-fA-F]+"))) {
            return term.substring(2).uppercase(Locale.ROOT) + "₁₆"
        }
        if(term.matches(Regex("0b[01]+"))) {
            return term.substring(2) + "₂"
        }
        if(term.matches(Regex("0[0-7]+"))) {
            return term.substring(1) + "₈"
        }
        return term.replace(Regex("\\s+"), "")
            .replace("pi", " \u03C0 ", ignoreCase = true)
            .replace("*", " \u00D7 ")
            .replace("-", " \u2212 ")
            .replace("/", " \u2215 ")
            .replace("+", " + ")
            .replace(Regex("&{1,2}"), " \u2227 ")
            .replace(Regex("\\|{1,2}"), " \u2228 ")
            .replace("!=", " \u2260 ")
            .replace("<>", " \u2260 ")
            .replace(">=", " \u2265 ")
            .replace("<=", " \u2264 ")
            .replace("=", " = ")
            .replace("<", " < ")
            .replace(">", " > ")
    }
}