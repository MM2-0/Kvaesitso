package de.mm20.launcher2.ui.widget

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.music.PlaybackState
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.ktx.conditional
import de.mm20.launcher2.ui.launcher.widgets.music.MusicWidgetVM

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalAnimationGraphicsApi::class
)
@Composable
fun MusicWidget() {

    val viewModel: MusicWidgetVM = viewModel()

    val albumArt by viewModel.albumArt.observeAsState()
    val title by viewModel.title.observeAsState()
    val artist by viewModel.artist.observeAsState()
    val album by viewModel.album.observeAsState()
    val playbackState by viewModel.playbackState.observeAsState()

    val context = LocalContext.current

    Row(
        modifier = Modifier.height(IntrinsicSize.Min)
    ) {
        Column(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxHeight()
                .weight(2f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = title ?: "---",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist ?: "---",
                    modifier = Modifier.padding(vertical = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album ?: "---",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        viewModel.onPreviousClick()
                    }) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        null
                    )
                }
                val playPauseIcon = AnimatedImageVector.animatedVectorResource(R.drawable.anim_ic_play_pause)
                IconButton(onClick = { viewModel.onPlayClick() }) {
                    Icon(
                        painter = rememberAnimatedVectorPainter(playPauseIcon, atEnd = playbackState == PlaybackState.Playing),
                        contentDescription = ""
                    )
                }
                IconButton(onClick = {
                    viewModel.onNextClick()
                }) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        null
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(144.dp)
                .combinedClickable(
                    onClick = {
                        viewModel.onAlbumArtClick()
                    },
                    onLongClick = {
                        viewModel.onAlbumArtLongClick(context)
                    }
                )
                .conditional(
                    albumArt == null,
                    Modifier.background(
                        MaterialTheme.colorScheme.primaryContainer,
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (albumArt != null) {
                albumArt?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        modifier = Modifier
                            .fillMaxSize(),
                        contentDescription = null
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
    }
}