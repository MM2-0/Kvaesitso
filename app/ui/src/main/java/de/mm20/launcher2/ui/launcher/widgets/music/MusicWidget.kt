package de.mm20.launcher2.ui.launcher.widgets.music

import android.content.pm.PackageManager
import android.content.res.Resources
import android.media.session.PlaybackState.CustomAction
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import de.mm20.launcher2.music.PlaybackState
import de.mm20.launcher2.music.SupportedActions
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.Tooltip
import de.mm20.launcher2.ui.ktx.conditional
import de.mm20.launcher2.ui.launcher.transitions.EnterHomeTransitionParams
import de.mm20.launcher2.ui.launcher.transitions.HandleEnterHomeTransition
import de.mm20.launcher2.ui.locals.LocalWindowSize
import de.mm20.launcher2.ui.theme.transparency.transparency
import de.mm20.launcher2.widgets.MusicWidget
import kotlin.math.min

@Composable
fun MusicWidget(widget: MusicWidget) {

    val viewModel: MusicWidgetVM = viewModel(key = "music-widget-${widget.id}")

    val albumArt by viewModel.albumArt.collectAsStateWithLifecycle(null)
    val title by viewModel.title.collectAsStateWithLifecycle(null)
    val artist by viewModel.artist.collectAsStateWithLifecycle(null)
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle(PlaybackState.Stopped)
    val position by viewModel.position.collectAsStateWithLifecycle(null)
    val duration by viewModel.duration.collectAsStateWithLifecycle(null)

    val supportedActions by viewModel.supportedActions.collectAsStateWithLifecycle(SupportedActions())

    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle(true)
        AnimatedVisibility(!hasPermission) {
            MissingPermissionBanner(
                modifier = Modifier.padding(16.dp),
                text = stringResource(R.string.missing_permission_music_widget),
                onClick = {
                    viewModel.requestPermission(context as AppCompatActivity)
                }
            )
        }
        if (title == null && artist == null) {
            NoData()
        } else {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min)
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxHeight()
                        .weight(2f)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = title ?: "",
                            modifier = if (playbackState != PlaybackState.Playing) Modifier
                            else Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artist ?: "",
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .then(
                                    if (playbackState != PlaybackState.Playing) Modifier
                                    else Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column {
                        val dur = duration
                        var pos by remember(position) { mutableStateOf(position) }

                        val interactionSource = remember { MutableInteractionSource() }
                        val isDragged by interactionSource.collectIsDraggedAsState()

                        var seekPosition by remember { mutableStateOf<Float?>(null) }

                        if (pos != null && dur != null && dur > 0) {
                            if (playbackState != PlaybackState.Stopped && supportedActions.seekTo && widget.config.interactiveProgressBar) {
                                Slider(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp)
                                        .requiredHeightIn(max = 20.dp),
                                    value = (if (isDragged) seekPosition else pos?.toFloat()) ?: 0f,
                                    valueRange = 0f..dur.toFloat(),
                                    interactionSource = interactionSource,
                                    onValueChange = {
                                        seekPosition = it
                                    },
                                    onValueChangeFinished = {
                                        seekPosition?.let {
                                            viewModel.seekTo(it.toLong())
                                            pos = it.toLong()
                                        }
                                    },
                                    track = {
                                        SliderDefaults.Track(
                                            sliderState = it,
                                            modifier = Modifier.requiredHeight(4.dp)
                                        )
                                    }
                                )
                            } else {
                                LinearProgressIndicator(
                                    progress = { (pos?.toFloat() ?: 0f) / dur.toFloat() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 16.dp),
                                    strokeCap = StrokeCap.Round,
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 16.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, start = 16.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatTimestamp(
                                    if (isDragged) seekPosition?.toLong() else pos
                                ),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                text = formatTimestamp(duration),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                AnimatedContent(albumArt) { art ->
                    Box(
                        modifier = Modifier
                            .padding(top = 16.dp, end = 16.dp)
                            .size(96.dp)
                            .clip(MaterialTheme.shapes.small)
                            .combinedClickable(
                                onClick = {
                                    viewModel.openPlayer(context)
                                },
                                onLongClick = {
                                    viewModel.openPlayerSelector(context)
                                },
                            )
                            .semantics {
                                contentDescription =
                                    context.getString(R.string.music_widget_open_player)
                            }
                            .conditional(
                                art == null,
                                Modifier.background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (art != null) {
                            val windowSize = LocalWindowSize.current
                            var bounds by remember { mutableStateOf(IntRect.Zero) }
                            Image(
                                bitmap = art.asImageBitmap(),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onGloballyPositioned {
                                        bounds = it.boundsInWindow().roundToIntRect()
                                    },
                                contentDescription = null,
                                contentScale = ContentScale.Crop
                            )
                            HandleEnterHomeTransition {
                                if (
                                    it.componentName.packageName == viewModel.currentPlayerPackage &&
                                    bounds.right > 0f && bounds.left < windowSize.width &&
                                    bounds.bottom > 0f && bounds.top < windowSize.height
                                ) {
                                    return@HandleEnterHomeTransition EnterHomeTransitionParams(
                                        bounds
                                    ) { _, _ ->
                                        val shape = MaterialTheme.shapes.small
                                        Image(
                                            bitmap = art.asImageBitmap(),
                                            modifier = Modifier
                                                .size(96.dp)
                                                .clip(shape),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                                return@HandleEnterHomeTransition null
                            }
                        } else {
                            Icon(
                                painterResource(R.drawable.music_note_48px),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,

            ) {
            if (supportedActions.skipToPrevious) {
                Tooltip(
                    tooltipText = stringResource(R.string.music_widget_previous_track)
                ) {
                    IconButton(
                        onClick = {
                            viewModel.skipPrevious()
                        }) {
                        Icon(
                            painter = painterResource(R.drawable.skip_previous_24px),
                            stringResource(R.string.music_widget_previous_track)
                        )
                    }
                }
            }
            val playPauseIcon =
                AnimatedImageVector.animatedVectorResource(R.drawable.anim_ic_play_pause)
            Tooltip(
                tooltipText = stringResource(
                    if (playbackState == PlaybackState.Playing) R.string.music_widget_pause
                    else R.string.music_widget_play
                )
            ) {
                FilledTonalIconButton(
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = MaterialTheme.transparency.surface),
                    ),
                    onClick = { viewModel.togglePause() },
                ) {
                    AnimatedContent(
                        playbackState == PlaybackState.Playing,
                        transitionSpec = {
                            fadeIn().togetherWith(fadeOut())
                        },
                        modifier = Modifier.rotate(
                            animateFloatAsState(
                                if (playbackState == PlaybackState.Playing) 90f else 0f
                            ).value
                        )
                    ) {
                        if (it) {
                            Icon(
                                painterResource(R.drawable.pause_24px),
                                stringResource(R.string.music_widget_pause),
                                modifier = Modifier.rotate(-90f)
                            )
                        } else {
                            Icon(
                                painterResource(R.drawable.play_arrow_24px),
                                stringResource(R.string.music_widget_play),
                            )
                        }
                    }
                }
            }
            if (supportedActions.skipToNext) {
                Tooltip(
                    tooltipText = stringResource(R.string.music_widget_next_track)
                ) {
                    IconButton(onClick = {
                        viewModel.skipNext()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.skip_next_24px),
                            stringResource(R.string.music_widget_next_track)
                        )
                    }
                }
            }
            CustomActions(
                actions = supportedActions,
                onActionSelected = {
                    viewModel.performCustomAction(it)
                },
                playerPackage = viewModel.currentPlayerPackage,
            )
        }
    }
}

@Composable
fun CustomActions(
    actions: SupportedActions,
    onActionSelected: (CustomAction) -> Unit,
    playerPackage: String?
) {
    val usedSlots = 1 + (if (actions.skipToPrevious) 1 else 0) + (if (actions.skipToNext) 1 else 0)
    val slots = 5 - usedSlots

    for (i in 0 until min(actions.customActions.size, slots - 1)) {
        val action = actions.customActions[i]
        Tooltip(
            tooltipText = action.name.toString()
        ) {
            IconButton(
                onClick = {
                    onActionSelected(action)
                }
            ) {
                CustomActionIcon(action, playerPackage)
            }
        }
    }
    if (slots < actions.customActions.size) {
        var showOverflowMenu by remember { mutableStateOf(false) }
        Box {
            Tooltip(
                tooltipText = stringResource(R.string.action_more_actions)
            ) {
                IconButton(onClick = { showOverflowMenu = true }) {
                    Icon(
                        painterResource(R.drawable.more_vert_24px),
                        contentDescription = null
                    )
                }
            }
            DropdownMenu(
                expanded = showOverflowMenu,
                onDismissRequest = { showOverflowMenu = false },
            ) {
                for (i in slots - 1 until actions.customActions.size) {
                    val action = actions.customActions[i]
                    DropdownMenuItem(
                        leadingIcon = {
                            CustomActionIcon(action, playerPackage)
                        },
                        text = {
                            Text(
                                text = action.name.toString(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = {
                            onActionSelected(action)
                        }
                    )
                }
            }
        }
    } else if (slots == actions.customActions.size) {
        val action = actions.customActions.last()
        Tooltip(
            tooltipText = action.name.toString()
        ) {
            IconButton(
                onClick = {
                    onActionSelected(action)
                }
            ) {
                CustomActionIcon(action, playerPackage)
            }
        }
    }
}

@Composable
fun CustomActionIcon(action: CustomAction, playerPackage: String?) {
    val context = LocalContext.current
    val resources = remember(playerPackage) {
        playerPackage?.let {
            try {
                context.packageManager.getResourcesForApplication(it)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    val drawable = remember(action, resources) {
        if (resources != null) {
            try {
                ResourcesCompat.getDrawable(
                    resources, action.icon, null
                )
            } catch (e: Resources.NotFoundException) {
                null
            }
        } else {
            null
        }
    }

    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(context)
            .data(drawable)
            .crossfade(false)
            .placeholder(drawable)
            .build(),
    )
    Icon(
        modifier = Modifier.size(24.dp),
        painter = painter,
        contentDescription = null,
    )
}

@Composable
fun NoData() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.music_note_24px),
            contentDescription = "",
            modifier = Modifier
                .padding(24.dp)
                .size(32.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = stringResource(id = R.string.music_widget_no_data),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Suppress("DefaultLocale")
private fun formatTimestamp(timestamp: Long?): String {
    if (timestamp == null) return "--:--"

    val totalSeconds = timestamp / 1000
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        days > 0 -> String.format("%d:%02d:%02d:%02d", days, hours, minutes, seconds)
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
        else -> String.format("%02d:%02d", minutes, seconds)
    }
}