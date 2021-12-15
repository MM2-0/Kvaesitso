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

class MusicCompactView : FrameLayout, CompactView {


    private val viewModel: MusicViewModel by (context as AppCompatActivity).viewModel()

    private val binding = CompactMusicBinding.inflate(LayoutInflater.from(context), this)

    override fun setTranslucent(translucent: Boolean) {
        if (translucent) {
            binding.musicCompactTitle.setTextColor(Color.WHITE)
            binding.musicCompactArtist.setTextColor(Color.WHITE)
            binding.musicCompactNext.elevation = 2 * dp
            binding.musicCompactPlay.elevation = 2 * dp
            binding.musicCompactNext.alpha = 1f
            binding.musicCompactPlay.alpha = 1f
            binding.musicCompactNext.imageTintList = ColorStateList.valueOf(Color.WHITE)
            binding.musicCompactPlay.imageTintList = ColorStateList.valueOf(Color.WHITE)
            val shadowY = resources.getDimension(R.dimen.elevation_shadow_1dp_y)
            val shadowR = resources.getDimension(R.dimen.elevation_shadow_1dp_radius)
            val shadowC = Color.argb(66, 0, 0, 0)
            binding.musicCompactTitle.setShadowLayer(shadowR, 0f, shadowY, shadowC)
            binding.musicCompactArtist.setShadowLayer(shadowR, 0f, shadowY, shadowC)
        } else {
            val primaryColor = ContextCompat.getColorStateList(context, R.color.text_color_primary)!!
            binding.musicCompactTitle.setTextColor(primaryColor)
            binding.musicCompactArtist.setTextColor(ContextCompat.getColorStateList(context, R.color.text_color_secondary))
            binding.musicCompactNext.elevation = 0f
            binding.musicCompactPlay.elevation = 0f
            binding.musicCompactNext.alpha = primaryColor.defaultColor.alpha / 255f
            binding.musicCompactPlay.alpha = primaryColor.defaultColor.alpha / 255f
            binding.musicCompactNext.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.icon_color))
            binding.musicCompactPlay.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.icon_color))
            binding.musicCompactTitle.setShadowLayer(0f, 0f, 0f, 0)
            binding.musicCompactArtist.setShadowLayer(0f, 0f, 0f, 0)
        }
    }

    private var playPauseIcon = if (viewModel.playbackState.value == PlaybackState.Playing) R.drawable.ic_pause else R.drawable.ic_play
        set(value) {
            if (value != field) {
                val icon = context.getDrawable(value)
                binding.musicCompactPlay.setImageDrawable(icon)
                (icon as? AnimatedVectorDrawable)?.start()
                field = value
            }
        }


    override var goToParent: (() -> Unit)? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    init {
        clipChildren = false
        binding.musicCompactNext.setOnClickListener {
            viewModel.next()
            (binding.musicCompactNext.drawable as AnimatedVectorDrawable).start()
        }
        binding.musicCompactPlay.setOnClickListener { _ ->
            viewModel.togglePause()
        }
        binding.musicCompactMeta.setOnClickListener {
            ActivityStarter.start(context, this, pendingIntent = viewModel.getLaunchIntent(context))
        }
        viewModel.title.observe(context as AppCompatActivity, Observer {
            binding.musicCompactTitle.text = it
        })

        viewModel.artist.observe(context as AppCompatActivity, Observer {
            binding.musicCompactArtist.text = it
        })

        viewModel.playbackState.observe(context as AppCompatActivity, Observer {
            if (it == PlaybackState.Playing) {
                playPauseIcon = R.drawable.ic_play_to_pause
                binding.musicCompactPlay.setOnClickListener {
                    viewModel.pause()
                }
                binding.musicCompactTitle.isSelected = true
                binding.musicCompactArtist.isSelected = true
            } else {
                playPauseIcon = R.drawable.ic_pause_to_play
                binding.musicCompactPlay.setOnClickListener {
                    viewModel.play()
                }
                binding.musicCompactTitle.isSelected = false
                binding.musicCompactArtist.isSelected = false
            }
        })
    }

    override fun update() {
        binding.musicCompactTitle.text = viewModel.title.value
        binding.musicCompactArtist.text = viewModel.artist.value
        playPauseIcon = if (viewModel.playbackState.value == PlaybackState.Playing) R.drawable.ic_pause else R.drawable.ic_play
    }
}
