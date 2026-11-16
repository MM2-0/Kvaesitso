package de.mm20.launcher2.ui.comms.radio
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import de.mm20.launcher2.comms.model.RadioStation
import de.mm20.launcher2.preferences.ui.PerformanceSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.comms.RadioPlayButton
import de.mm20.launcher2.ui.comms.RadioViewModel
import de.mm20.launcher2.ui.comms.radioStationSubtitle
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.telosTechSurface
import org.koin.compose.koinInject

enum class RadioChip { All, Recent, Mine, Browser }

/** Average colour of a station logo, used to tint the card of the playing station. Null when unavailable. */
@Composable
internal fun rememberLogoColor(url: String, enabled: Boolean): Color? {
    val context = LocalContext.current
    var color by remember(url) { mutableStateOf<Color?>(null) }
    LaunchedEffect(url, enabled) {
        if (!enabled || url.isBlank()) return@LaunchedEffect
        color = runCatching {
            val request = ImageRequest.Builder(context).data(url).size(32).allowHardware(false).build()
            val result = context.imageLoader.execute(request) as? SuccessResult ?: return@runCatching null
            val bmp = (result.drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
            averageColor(bmp)
        }.getOrNull()
    }
    return color
}

private fun averageColor(bmp: Bitmap): Color? {
    var r = 0L; var g = 0L; var b = 0L; var n = 0L
    val w = bmp.width; val h = bmp.height
    if (w == 0 || h == 0) return null
    val step = maxOf(1, minOf(w, h) / 16)
    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            val p = bmp.getPixel(x, y)
            if ((p ushr 24) > 128) {
                r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
            }
            x += step
        }
        y += step
    }
    if (n == 0L) return null
    return Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
}

/** Three bars that move while the station plays. Static when animations are reduced. */
@Composable
fun RadioEqualizer(animate: Boolean, color: Color, modifier: Modifier = Modifier) {
    val phases = if (animate) {
        val t = rememberInfiniteTransition(label = "eq")
        listOf(420, 560, 490).mapIndexed { i, ms ->
            t.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Reverse),
                label = "bar$i",
            ).value
        }
    } else listOf(0.6f, 1f, 0.45f)
    Row(modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        phases.forEach { f ->
            Box(Modifier.width(3.dp).height((16 * f).dp).clip(RoundedCornerShape(1.dp)).background(color))
        }
    }
}

@Composable
internal fun RadioLogo(station: RadioStation, modifier: Modifier, corner: Dp = 20.dp, pad: Dp = 12.dp) {
    Box(
        modifier.clip(RoundedCornerShape(corner)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (station.faviconUrl.isNotBlank()) {
            AsyncImage(
                model = station.faviconUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(pad),
            )
        } else {
            Icon(painterResource(BaseR.drawable.music_note_24px), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        }
    }
}

/** Station card: soft logo-tinted gradient, large logo, play button, equalizer while playing */
@Composable
fun RadioStationCard(
    station: RadioStation,
    player: RadioViewModel,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiSettings = LocalRadioUiSettings.current
    val reduce by koinInject<PerformanceSettings>().reduceAnimations.collectAsState(false)
    val playingId by player.stationId.collectAsStateWithLifecycle()
    val playWhenReady by player.playWhenReady.collectAsStateWithLifecycle()
    val playing = playingId == station.id && playWhenReady
    val logoColor = rememberLogoColor(station.faviconUrl, true)
    val tint by animateColorAsState(
        (logoColor ?: MaterialTheme.colorScheme.primary).copy(alpha = if (playing) 0.42f else 0.24f),
        animationSpec = if (reduce) snap() else tween(300),
        label = "cardTint",
    )
    val low = MaterialTheme.colorScheme.surfaceContainerLow
    val cardShape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(reduce, onClick = { if (uiSettings.tapPlays) player.toggleStation(station) else onLongClick() }, onLongClick = onLongClick)
            .telosTechSurface(
                borderColor = if (playing) TelosCyber.NeonGreen else TelosCyber.NeonCyan.copy(alpha = 0.65f),
                shape = cardShape,
            )
            .clip(cardShape)
            .background(Brush.verticalGradient(listOf(tint.compositeOver(low), low)))
            .padding(12.dp),
    ) {
        RadioLogo(station, Modifier.size(64.dp), corner = 8.dp, pad = 6.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (playing) RadioEqualizer(!reduce, MaterialTheme.colorScheme.primary)
            Text(station.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            radioStationSubtitle(station),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        }
        if (playingId == station.id) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton(onClick = { player.togglePlayPause() }) {
                    Icon(
                        painterResource(if (playWhenReady) BaseR.drawable.pause_24px else BaseR.drawable.play_arrow_24px),
                        contentDescription = null,
                    )
                }
                androidx.compose.material3.IconButton(onClick = { player.stop() }) {
                    Icon(
                        painterResource(BaseR.drawable.close_24px),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

/** Home of the collection: a two column grid showing favorites alphabetically, with the last played station first */
@Composable
fun RadioHome(
    favorites: List<RadioStation>,
    player: RadioViewModel,
    onLongClick: (RadioStation) -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce by koinInject<PerformanceSettings>().reduceAnimations.collectAsState(false)
    val shown = remember(favorites) {
        val lastPlayed = favorites.maxByOrNull { it.lastPlayedAt }
        val rest = favorites.filter { it.id != lastPlayed?.id }.sortedBy { it.name }
        if (lastPlayed != null && lastPlayed.lastPlayedAt > 0) listOf(lastPlayed) + rest else rest
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(260.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        items(shown, key = { it.id }) { s ->
            RadioStationCard(s, player, { onLongClick(s) }, if (reduce) Modifier else Modifier.animateItem())
        }
    }
}
