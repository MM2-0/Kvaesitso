package de.mm20.launcher2.ui.legacy.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.alpha
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.legacy.helper.ActivityStarter
import de.mm20.launcher2.music.MusicViewModel
import de.mm20.launcher2.music.PlaybackState
import de.mm20.launcher2.search.SearchViewModel
import de.mm20.launcher2.ui.LegacyLauncherTheme
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.widget.MusicWidget
import de.mm20.launcher2.ui.widget.WeatherWidget
import kotlinx.android.synthetic.main.compact_music.view.*
import org.koin.androidx.viewmodel.ext.android.viewModel

class MusicWidget : LauncherWidget {

    override val compactViewRanking: Int
        get() = if (viewModel.hasActiveSession) 1 else -1

    override val compactView: CompactView?
        get() {
            return MusicCompactView(context)
        }
    override val settingsFragment: String?
        get() = null
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


    override fun update() {
    }

    companion object {
        const val ID = "music"
    }
}

class MusicCompactView : FrameLayout, CompactView {


    private val viewModel: MusicViewModel by (context as AppCompatActivity).viewModel()

    override fun setTranslucent(translucent: Boolean) {
        if (translucent) {
            musicCompactTitle.setTextColor(Color.WHITE)
            musicCompactArtist.setTextColor(Color.WHITE)
            musicCompactNext.elevation = 2 * dp
            musicCompactPlay.elevation = 2 * dp
            musicCompactNext.alpha = 1f
            musicCompactPlay.alpha = 1f
            musicCompactNext.imageTintList = ColorStateList.valueOf(Color.WHITE)
            musicCompactPlay.imageTintList = ColorStateList.valueOf(Color.WHITE)
            val shadowY = resources.getDimension(R.dimen.elevation_shadow_1dp_y)
            val shadowR = resources.getDimension(R.dimen.elevation_shadow_1dp_radius)
            val shadowC = Color.argb(66, 0, 0, 0)
            musicCompactTitle.setShadowLayer(shadowR, 0f, shadowY, shadowC)
            musicCompactArtist.setShadowLayer(shadowR, 0f, shadowY, shadowC)
        } else {
            val primaryColor = ContextCompat.getColorStateList(context, R.color.text_color_primary)!!
            musicCompactTitle.setTextColor(primaryColor)
            musicCompactArtist.setTextColor(ContextCompat.getColorStateList(context, R.color.text_color_secondary))
            musicCompactNext.elevation = 0f
            musicCompactPlay.elevation = 0f
            musicCompactNext.alpha = primaryColor.defaultColor.alpha / 255f
            musicCompactPlay.alpha = primaryColor.defaultColor.alpha / 255f
            musicCompactNext.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.icon_color))
            musicCompactPlay.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.icon_color))
            musicCompactTitle.setShadowLayer(0f, 0f, 0f, 0)
            musicCompactArtist.setShadowLayer(0f, 0f, 0f, 0)
        }
    }

    private var playPauseIcon = if (viewModel.playbackState.value == PlaybackState.Playing) R.drawable.ic_pause else R.drawable.ic_play
        set(value) {
            if (value != field) {
                val icon = context.getDrawable(value)
                musicCompactPlay.setImageDrawable(icon)
                (icon as? AnimatedVectorDrawable)?.start()
                field = value
            }
        }


    override var goToParent: (() -> Unit)? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    init {
        View.inflate(context, R.layout.compact_music, this)
        clipChildren = false
        musicCompactNext.setOnClickListener {
            viewModel.next()
            (musicCompactNext.drawable as AnimatedVectorDrawable).start()
        }
        musicCompactPlay.setOnClickListener { _ ->
            viewModel.togglePause()
        }
        musicCompactMeta.setOnClickListener {
            ActivityStarter.start(context, this, pendingIntent = viewModel.getLaunchIntent(context))
        }
        viewModel.title.observe(context as AppCompatActivity, Observer {
            musicCompactTitle.text = it
        })

        viewModel.artist.observe(context as AppCompatActivity, Observer {
            musicCompactArtist.text = it
        })

        viewModel.playbackState.observe(context as AppCompatActivity, Observer {
            if (it == PlaybackState.Playing) {
                playPauseIcon = R.drawable.ic_play_to_pause
                musicCompactPlay.setOnClickListener {
                    viewModel.pause()
                }
                musicCompactTitle.isSelected = true
                musicCompactArtist.isSelected = true
            } else {
                playPauseIcon = R.drawable.ic_pause_to_play
                musicCompactPlay.setOnClickListener {
                    viewModel.play()
                }
                musicCompactTitle.isSelected = false
                musicCompactArtist.isSelected = false
            }
        })
    }

    override fun update() {
        musicCompactTitle.text = viewModel.title.value
        musicCompactArtist.text = viewModel.artist.value
        playPauseIcon = if (viewModel.playbackState.value == PlaybackState.Playing) R.drawable.ic_pause else R.drawable.ic_play
    }
}
