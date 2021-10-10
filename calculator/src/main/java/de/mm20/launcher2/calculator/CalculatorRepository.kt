package de.mm20.launcher2.calculator

import androidx.lifecycle.MutableLiveData
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.BaseSearchableRepository
import de.mm20.launcher2.search.data.Calculator
import org.mariuszgromada.math.mxparser.Expression

class CalculatorRepository : BaseSearchableRepository() {

    val calculator = MutableLiveData<Calculator?>()

    override suspend fun search(query: String) {
        if (query.isBlank()) {
            calculator.value = null
            return
        }
        if (!LauncherPreferences.instance.searchCalculator) return
        val calc = when {
            query.matches(Regex("0x[0-9a-fA-F]+")) -> {
                val solution = query.substring(2).toIntOrNull(16) ?: run {
                    calculator.value = null
                    return
                }
                Calculator(term = query, solution = solution.toDouble())
            }
            query.matches(Regex("0b[01]+")) -> {
                val solution = query.substring(2).toIntOrNull(2) ?: run {
                    calculator.value = null
                    return
                }
                Calculator(term = query, solution = solution.toDouble())
            }
            query.matches(Regex("0[0-7]+")) -> {
                val solution = query.substring(1).toIntOrNull(8) ?: run {
                    calculator.value = null
                    return
                }
                Calculator(term = query, solution = solution.toDouble())
            }
            else -> {
                val exp = Expression(query)
                if (exp.checkSyntax()) {
                    Calculator(term = query, solution = exp.calculate())
                } else {
                    val exp2 = Expression(query.replace(',', '.').replace(';', ','))
                    if (exp2.checkSyntax()) {
                        Calculator(term = query, solution = exp2.calculate())
                    } else null
                }
            }
        }
        calculator.value = calc
    }
}