package de.mm20.launcher2.ui.legacy.component

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.ui.MdcLauncherTheme
import de.mm20.launcher2.ui.databinding.ViewUnitconverterBinding
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.search.UnitConverterItem

class UnitConverterView : FrameLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleRes
    )

    private val unitConverter: LiveData<UnitConverter?>

    private val binding = ViewUnitconverterBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        val viewModel: SearchVM by (context as AppCompatActivity).viewModels()
        unitConverter = viewModel.unitConverterResult
        unitConverter.observe(context as AppCompatActivity, Observer {
            if (it == null) visibility = View.GONE
            else {
                visibility = View.VISIBLE
            }
        })

        binding.composeView.setContent {
            val converter by unitConverter.observeAsState()
            MdcLauncherTheme {
                // TODO: Temporary solution until parent widget card is rewritten in Compose
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    Column {
                        converter?.let {
                            UnitConverterItem(
                                unitConverter = it,
                            )
                        }
                    }
                }
            }
        }
    }

}