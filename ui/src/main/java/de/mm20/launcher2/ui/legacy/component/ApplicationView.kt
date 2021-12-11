package de.mm20.launcher2.ui.legacy.component

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import de.mm20.launcher2.applications.AppViewModel
import de.mm20.launcher2.search.data.Application
import de.mm20.launcher2.ui.databinding.ViewApplicationBinding
import org.koin.androidx.viewmodel.ext.android.viewModel

class ApplicationView : FrameLayout {

    private val applications: LiveData<List<Application>>

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    private val binding = ViewApplicationBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        layoutTransition = LayoutTransition()
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        binding.applicationCard.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        val viewModel: AppViewModel by (context as AppCompatActivity).viewModel()
        applications = viewModel.applications
        applications.observe(context as AppCompatActivity, Observer<List<Application>> {
            visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
            binding.applicationGrid.submitItems(it)
        })
    }
}
