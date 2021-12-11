package de.mm20.launcher2.ui.legacy.component

import android.content.Context
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.xwray.groupie.ExpandableGroup
import com.xwray.groupie.ExpandableItem
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.Section
import com.xwray.groupie.kotlinandroidextensions.GroupieViewHolder
import com.xwray.groupie.kotlinandroidextensions.Item
import de.mm20.launcher2.ktx.sp
import de.mm20.launcher2.search.data.CurrencyUnitConverter
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.databinding.ViewUnitconverterBinding
import de.mm20.launcher2.unitconverter.UnitConverterViewModel
import de.mm20.launcher2.unitconverter.UnitValue
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.DateFormat
import java.util.*
import kotlin.math.min

class UnitConverterView : FrameLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    private val unitConverter: LiveData<UnitConverter?>
    private val adapter: GroupAdapter<GroupieViewHolder>

    private val binding = ViewUnitconverterBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        val unitConverterViewModel by (context as AppCompatActivity).viewModel<UnitConverterViewModel>()
        unitConverter = unitConverterViewModel.unitConverter
        unitConverter.observe(context as AppCompatActivity, Observer {
            if (it == null) visibility = View.GONE
            else {
                visibility = View.VISIBLE
                bind(it)
            }
        })
        adapter = GroupAdapter()
        binding.unitConverterValues.also {
            it.adapter = adapter
            it.addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))
        }
    }


    private fun bind(converter: UnitConverter) {
        val title = converter.inputValue.formattedValue + " " + converter.inputValue.formattedName
        binding.unitConverterInput.text = title

        /*val sb = StringBuilder()
        for (unit in converter.values) {

            sb.append("${unit.formatted}\n")
        }
        sb.removeSuffix("\n")
        unitConverterValues.text = sb.toString()
        unitConverterIcon.setImageResource(when (converter.dimension) {
            Dimension.LENGTH -> R.drawable.ic_unit_length
            Dimension.MASS -> R.drawable.ic_unit_mass
            Dimension.TIME -> R.drawable.ic_unit_time
            Dimension.DATA -> R.drawable.ic_unit_datasize
            Dimension.VELOCITY -> R.drawable.ic_unit_velocity
            else -> 0
        })*/

        adapter.clear()

        val maxValueLength = converter.values.maxByOrNull { it.formattedValue.length }?.formattedValue?.length
                ?: 0

        val section = Section().apply {
            addAll(converter.values.subList(0, min(converter.values.size, 5)).map {
                ValueItem(it, maxValueLength * 8f * sp)
            })
        }.also {
            adapter.add(it)
        }

        if (converter.values.size > 5) {
            binding.showAllButton.visibility = View.VISIBLE
            binding.showAllButton.setOnClickListener {
                section.addAll(converter.values.subList( 5, converter.values.size).map {
                    ValueItem(it, maxValueLength * 8f * sp)
                })
                binding.showAllButton.visibility = View.GONE
                binding.showAllButton.setOnClickListener(null)
            }
        } else {
            binding.showAllButton.visibility = View.GONE
        }


        if (converter is CurrencyUnitConverter) {
            val df = DateFormat.getDateInstance(DateFormat.SHORT)
            val date = Date().apply {
                time = converter.updateTimestamp
            }
            val infoText = SpannableStringBuilder()
                    .append("European Central Bank (${df.format(date)})", object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            CustomTabsIntent
                                    .Builder()
                                    .setToolbarColor(0xFF003299.toInt())
                                    .build()
                                    .launchUrl(context,
                                            Uri.parse("https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html"))
                        }
                    }, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    .append(" • ")
                    .append(context.getString(R.string.disclaimer), object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            MaterialDialog(context).show {
                                title(res = R.string.disclaimer)
                                message(res = R.string.disclaimer_currency_converter)
                                positiveButton(res = R.string.close) {
                                    dismiss()
                                }
                            }
                        }
                    }, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            binding.unitConverterInfo.apply {
                text = infoText
                visibility = View.VISIBLE
                movementMethod = LinkMovementMethod.getInstance()
            }
        } else {
            binding.unitConverterInfo.visibility = View.GONE
        }

    }
}

class ValueItem(private val value: UnitValue, val valueWidth: Float) : Item() {
    override fun getLayout(): Int {
        return R.layout.unit_converter_row
    }

    override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        viewHolder.itemView.findViewById<TextView>(R.id.value).let {
            it.text = value.formattedValue
            it.layoutParams = it.layoutParams.apply {
                width = valueWidth.toInt()
            }
        }
        viewHolder.itemView.findViewById<TextView>(R.id.name).text = value.formattedName
        viewHolder.itemView.findViewById<TextView>(R.id.symbol).text = value.symbol
    }

}

class ExpandItem : Item(), ExpandableItem {

    private lateinit var expandableGroup: ExpandableGroup

    override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        viewHolder.itemView.visibility = if (expandableGroup.isExpanded) View.GONE else View.VISIBLE
        viewHolder.itemView.setOnClickListener {
            expandableGroup.onToggleExpanded()
            viewHolder.itemView.visibility = View.GONE
        }
    }

    override fun getLayout(): Int {
        return R.layout.unit_converter_show_all
    }

    override fun setExpandableGroup(onToggleListener: ExpandableGroup) {
        expandableGroup = onToggleListener
    }

}