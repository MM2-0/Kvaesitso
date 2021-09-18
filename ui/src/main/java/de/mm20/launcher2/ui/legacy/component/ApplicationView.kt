package de.mm20.launcher2.ui.legacy.component

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import de.mm20.launcher2.applications.AppViewModel
import de.mm20.launcher2.search.data.Application
import de.mm20.launcher2.ui.R
import kotlinx.android.synthetic.main.view_application.view.*

class ApplicationView : FrameLayout {

    private val applications: LiveData<List<Application>>

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    init {
        View.inflate(context, R.layout.view_application, this)
        layoutTransition = LayoutTransition()
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        applicationCard.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        applications = ViewModelProvider(context as AppCompatActivity).get(AppViewModel::class.java).applications
        applications.observe(context as AppCompatActivity, Observer<List<Application>> {
            visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
            applicationGrid.submitItems(it)
        })
    }
}
