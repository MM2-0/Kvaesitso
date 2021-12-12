package de.mm20.launcher2.ui.legacy.component

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.calculator.CalculatorViewModel
import de.mm20.launcher2.search.data.Calculator
import de.mm20.launcher2.ui.LegacyLauncherTheme
import de.mm20.launcher2.ui.databinding.ViewCalculatorBinding
import de.mm20.launcher2.ui.search.CalculatorItem
import de.mm20.launcher2.ui.search.UnitConverterItem
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
            }
        })

        binding.composeView.setContent {
            val converter by calculator.observeAsState()
            LegacyLauncherTheme {
                // TODO: Temporary solution until parent widget card is rewritten in Compose
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    Column {
                        converter?.let {
                            CalculatorItem(
                                calculator = it,
                            )
                        }
                    }
                }
            }
        }
    }
}