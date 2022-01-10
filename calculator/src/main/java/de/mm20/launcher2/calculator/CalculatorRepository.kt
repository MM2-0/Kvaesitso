package de.mm20.launcher2.calculator

import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.data.Calculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mariuszgromada.math.mxparser.Expression

interface CalculatorRepository {
    fun search(query: String): Flow<Calculator?>
}

class CalculatorRepositoryImpl : CalculatorRepository, KoinComponent {

    private val dataStore: LauncherDataStore by inject()

    override fun search(query: String): Flow<Calculator?> = channelFlow {
        if (query.isBlank()) {
            send(null)
            return@channelFlow
        }
        val searchCalculator = dataStore.data.map { it.calculatorSearch.enabled }
        searchCalculator.collectLatest {
            if (it) {
                send(queryCalculator(query))
            } else {
                send(null)
            }
        }
    }

    private suspend fun queryCalculator(query: String): Calculator? {
        return when {
            query.matches(Regex("0x[0-9a-fA-F]+")) -> {
                val solution = query.substring(2).toIntOrNull(16) ?: run {
                    return null
                }
                Calculator(term = query, solution = solution.toDouble())
            }
            query.matches(Regex("0b[01]+")) -> {
                val solution = query.substring(2).toIntOrNull(2) ?: run {
                    return null
                }
                Calculator(term = query, solution = solution.toDouble())
            }
            query.matches(Regex("0[0-7]+")) -> {
                val solution = query.substring(1).toIntOrNull(8) ?: run {
                    return null
                }
                Calculator(term = query, solution = solution.toDouble())
            }
            else -> {
                withContext(Dispatchers.IO) {
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
        }
    }
}