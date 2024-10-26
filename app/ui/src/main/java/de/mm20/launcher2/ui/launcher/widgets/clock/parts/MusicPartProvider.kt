package de.mm20.launcher2.ui.launcher.widgets.clock.parts

import android.app.PendingIntent
import android.content.Context
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.sendWithBackgroundPermission
import de.mm20.launcher2.music.MusicService
import de.mm20.launcher2.music.PlaybackState
import de.mm20.launcher2.music.SupportedActions
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MusicPartProvider : PartProvider, KoinComponent {

    private val musicService: MusicService by inject()

    override fun getRanking(context: Context): Flow<Int> = channelFlow {
        musicService.playbackState.collectLatest {
            if (it == PlaybackState.Stopped) send(0)
            else send(50)
        }
    }

    @Composable
    override fun Component(compactLayout: Boolean) {
        val context = LocalContext.current

        val title by musicService.title.collectAsState(null)
        val artist by musicService.artist.collectAsState(null)
        val state by musicService.playbackState.collectAsState(PlaybackState.Stopped)
        val supportedActions by musicService.supportedActions.collectAsState(SupportedActions())

        val playIcon = AnimatedImageVector.animatedVectorResource(R.drawable.anim_ic_play_pause)

        if (compactLayout) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp)
                        .combinedClickable(
                            onClick = {
                                try {
                                    musicService
                                        .openPlayer()
                                        ?.sendWithBackgroundPermission(context)
                                } catch (e: PendingIntent.CanceledException) {
                                    CrashReporter.logException(e)
                                }
                            },
                            onLongClick = {
                                musicService.openPlayerChooser(context)
                            }
                        )
                ) {
                    title?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    artist?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = { musicService.togglePause() }) {
                    Icon(
                        painter = rememberAnimatedVectorPainter(
                            animatedImageVector = playIcon,
                            atEnd = state == PlaybackState.Playing
                        ), contentDescription = stringResource(
                            if (state == PlaybackState.Playing) R.string.music_widget_pause
                            else R.string.music_widget_play
                        )
                    )
                }
                if (supportedActions.skipToNext) {
                    IconButton(onClick = { musicService.next() }) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = stringResource(R.string.music_widget_next_track)
                        )
                    }
                }
            }
        }
        if (!compactLayout) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                try {
                                    musicService.openPlayer()?.sendWithBackgroundPermission(context)
                                } catch (e: PendingIntent.CanceledException) {
                                }
                            },
                            onLongClick = {
                                musicService.openPlayerChooser(context)
                            }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    title?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                    artist?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Row {
                    if (supportedActions.skipToPrevious) {
                        IconButton(onClick = { musicService.previous() }) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = stringResource(R.string.music_widget_previous_track)
                            )
                        }
                    }
                    IconButton(onClick = { musicService.togglePause() }) {
                        Icon(
                            painter = rememberAnimatedVectorPainter(
                                animatedImageVector = playIcon,
                                atEnd = state == PlaybackState.Playing
                            ), contentDescription = stringResource(
                                if (state == PlaybackState.Playing) R.string.music_widget_pause
                                else R.string.music_widget_play
                            )
                        )
                    }
                    if (supportedActions.skipToPrevious) {
                        IconButton(onClick = { musicService.next() }) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = stringResource(R.string.music_widget_next_track)
                            )
                        }
                    }
                }
            }
        }
    }

}