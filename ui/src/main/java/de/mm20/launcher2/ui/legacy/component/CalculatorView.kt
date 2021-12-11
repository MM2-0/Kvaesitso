package de.mm20.launcher2.ui.legacy.component

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.calculator.CalculatorViewModel
import de.mm20.launcher2.search.data.Calculator
import de.mm20.launcher2.ui.databinding.ViewCalculatorBinding
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.math.round

class CalculatorView : FrameLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    private val calculator: LiveData<Calculator?>

    private val binding = ViewCalculatorBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        val viewModel: CalculatorViewModel by (context as AppCompatActivity).viewModel()
        calculator = viewModel.calculator
        calculator.observe(context as AppCompatActivity, Observer {
            if (it == null) visibility = View.GONE
            else {
                visibility = View.VISIBLE
                bind(it)
            }
        })
    }


    private fun bind(calc: Calculator) {

        binding.calculatorTerm.text = beautifyTerm(calc.term)
        binding.calculatorSolution.text = context.getString(R.string.calculator_solution, calc.formattedString)
        if (calc.solution == round(calc.solution) && calc.term.matches(Regex("[0-9]+"))) {
            val binHexOct = StringBuilder()
            binHexOct.append(calc.formattedBinaryString).append("\n")
                    .append(calc.formattedOctString).append("\n")
                    .append(calc.formattedHexString)
            binding.calculatorSolutionHexBinOct.text = binHexOct.toString()
            binding.calculatorSolutionHexBinOct.visibility = View.VISIBLE
            binding.calculatorLabelHexBinOct.visibility = View.VISIBLE
        } else {
            binding.calculatorSolutionHexBinOct.visibility = GONE
            binding.calculatorLabelHexBinOct.visibility = GONE
        }
    }

    private fun beautifyTerm(term: String): String {
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