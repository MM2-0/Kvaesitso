package de.mm20.launcher2.ui.legacy.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.graphics.alpha
import androidx.lifecycle.Observer
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.legacy.helper.ActivityStarter
import de.mm20.launcher2.music.MusicViewModel
import de.mm20.launcher2.music.PlaybackState
import de.mm20.launcher2.ui.LegacyLauncherTheme
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.databinding.CompactMusicBinding
import de.mm20.launcher2.ui.widget.MusicWidget
import org.koin.androidx.viewmodel.ext.android.viewModel

class MusicWidget : LauncherWidget {

    override val canResize: Boolean
        get() = false
    override val name: String
        get() = context.getString(R.string.widget_name_music)

    private val viewModel: MusicViewModel by (context as AppCompatActivity).viewModel()

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)


    init {
        val composeView = ComposeView(context)
        composeView.id = FrameLayout.generateViewId()
        composeView.setContent {
            LegacyLauncherTheme {
                // TODO: Temporary solution until parent widget card is rewritten in Compose
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    Column {
                        MusicWidget()
                    }
                }
            }
        }
        addView(composeView)
    }

    companion object {
        const val ID = "music"
    }
}

