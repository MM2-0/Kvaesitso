package de.mm20.launcher2.ui.legacy.view

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.ktx.lifecycleOwner
import de.mm20.launcher2.ktx.lifecycleScope
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

open class InnerCardView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr), KoinComponent {
    init {

        radius = LauncherCardView.currentCardStyle.radius * dp
        strokeColor = ContextCompat.getColor(context, R.color.color_divider)
        strokeWidth = (1 * dp).toInt()
        cardElevation = 2 * dp
        outlineProvider = null
    }

    private val dataStore: LauncherDataStore by inject()
    private var job: Job? = null
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        job?.cancel()
        job = lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                dataStore.data.map { it.cards.radius }.distinctUntilChanged().collectLatest {
                    radius = it * dp
                }
            }
        }
    }
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        job?.cancel()
    }

}
