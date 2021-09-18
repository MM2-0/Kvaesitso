package de.mm20.launcher2.ui.legacy.component

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.chip.Chip
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.legacy.helper.ActivityStarter
import de.mm20.launcher2.search.WebsearchViewModel
import de.mm20.launcher2.search.data.Websearch
import de.mm20.launcher2.ui.R
import kotlinx.android.synthetic.main.view_websearch.view.*

class WebSearchView : FrameLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    private val websearches: LiveData<List<Websearch>>

    init {
        View.inflate(context, R.layout.view_websearch, this)
        val viewModel = ViewModelProvider(context as AppCompatActivity)[WebsearchViewModel::class.java]
        websearches = viewModel.websearches
        websearches.observe(context as AppCompatActivity, Observer {
            updateWebsearches(it)
        })
    }

    private fun updateWebsearches(websearches: List<Websearch>) {
        visibility = if (websearches.isEmpty()) View.GONE else View.VISIBLE
        webSearchList.removeAllViews()
        for (search in websearches) {
            val chip = Chip(context)
            chip.text = search.label
            if (search.icon != null) {
                Glide.with(context)
                        .load(search.icon)
                        .into(object : SimpleTarget<Drawable>() {
                            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                                chip.chipIcon = resource
                            }

                        })
            } else {
                chip.chipIcon = ContextCompat.getDrawable(context, R.drawable.ic_search)
                chip.chipIconTint = ColorStateList.valueOf(search.color)

            }
            chip.chipStrokeWidth = 1 * dp
            chip.chipStrokeColor = ContextCompat.getColorStateList(context, R.color.chip_stroke)
            chip.chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.chip_background)
            chip.setTextAppearanceResource(R.style.ChipTextAppearance)
            chip.setOnClickListener {
                ActivityStarter.start(context, chip, intent = search.getLaunchIntent())
            }

            webSearchList.addView(chip)
        }
    }

}