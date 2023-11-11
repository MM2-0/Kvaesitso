package de.mm20.launcher2.ui.launcher.search.location

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.i18n.R
import de.mm20.launcher2.search.OpeningTime
import de.mm20.launcher2.ui.animation.animateTextStyleAsState
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.ktx.metersToLocalizedString
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.locals.LocalGridSettings
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.asinh
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

@Composable
fun LocationItem(
    modifier: Modifier = Modifier,
    location: Location,
    showDetails: Boolean,
    onBack: () -> Unit,
    priorityCallback: ((key: String, priority: Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val viewModel: SearchableItemVM = listItemViewModel(key = "search-${location.key}")
    val iconSize = LocalGridSettings.current.iconSize.dp.toPixels()

    val userLocation by remember(context) { viewModel.getUserLocation(context) }.collectAsStateWithLifecycle(null)
    val insaneUnits by viewModel.useInsaneUnits.collectAsState()

    val distance = userLocation?.distanceTo(location.toAndroidLocation())
    if (distance != null)
        priorityCallback?.invoke(location.key, distance.roundToInt())

    val openingHours = remember { mutableStateOf<List<OpeningTime>?>(null) }
    val websiteUrl = remember { mutableStateOf<String?>(null) }
    val street = remember { mutableStateOf<String?>(null) }
    val houseNumber = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(location) {
        viewModel.init(location, iconSize.toInt())
        openingHours.value = location.getOpeningHours()
        websiteUrl.value = location.getWebsiteUrl()
        street.value = location.getStreet()
        houseNumber.value = location.getHouseNumber()
    }

    val closedColor = MaterialTheme.colorScheme.secondary
    val openColor = MaterialTheme.colorScheme.tertiary

    Row(modifier = modifier) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row {
                val padding by animateDpAsState(if (showDetails) 16.dp else 12.dp)
                Column(
                    modifier = Modifier.padding(
                        top = padding,
                        start = padding,
                        bottom = 12.dp,
                        end = padding
                    )
                ) {
                    val textStyle by animateTextStyleAsState(
                        if (showDetails) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.titleSmall
                    )
                    val icon by viewModel.icon.collectAsStateWithLifecycle()
                    val badge by viewModel.badge.collectAsState(null)
                    Row {
                        ShapedLauncherIcon(
                            modifier = Modifier
                                .padding(end = 16.dp),
                            size = 48.dp,
                            icon = { icon },
                            badge = { badge },
                        )
                        Text(
                            modifier = Modifier.fillMaxHeight(),
                            text = location.labelOverride ?: location.label,
                            style = textStyle
                        )
                    }
                    AnimatedVisibility(!showDetails) {
                        Row(modifier = Modifier.padding(top = 2.dp)) {
                            if (!openingHours.value.isNullOrEmpty()) {
                                val isOpen = openingHours.value!!.any { it.isOpen }
                                Text(
                                    modifier = Modifier.padding(end = 8.dp),
                                    text = context.getString(if (isOpen) R.string.location_open else R.string.location_closed),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOpen) openColor else closedColor
                                )
                            }
                            Text(
                                text = getLocationSummary(
                                    context,
                                    distance,
                                    street.value,
                                    houseNumber.value,
                                    insaneUnits
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    AnimatedVisibility(showDetails) {
                        val tags by viewModel.tags.collectAsState(emptyList())
                        if (tags.isNotEmpty()) {
                            Text(
                                modifier = Modifier.padding(top = 1.dp, bottom = 4.dp),
                                text = tags.joinToString(separator = " #", prefix = "#"),
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(showDetails) {
                Column {
                    Row(horizontalArrangement = Arrangement.SpaceEvenly) {

                        if (!openingHours.value.isNullOrEmpty()) {
                            val oh = openingHours.value!!
                            val today = LocalDateTime.now().dayOfWeek
                            val daysOfWeek = enumValues<DayOfWeek>()

                            val nextOpeningTime =
                                (0 until DayOfWeek.SUNDAY.ordinal)
                                    .firstNotNullOfOrNull {
                                        val dow =
                                            daysOfWeek[(today.ordinal + it) % DayOfWeek.SUNDAY.ordinal]
                                        oh.filter { it.dayOfWeek == dow }
                                            .firstOrNull {
                                                it.dayOfWeek != today || it.startTime.isAfter(
                                                    LocalTime.now()
                                                )
                                            }
                                    } ?: oh.first()

                            val openIndex = oh.indexOfFirst { it.isOpen }
                            val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                            if (openIndex != -1) {
                                val todaySchedule = oh[openIndex]
                                Text(
                                    text = stringResource(
                                        R.string.location_open_until,
                                        (todaySchedule.startTime + todaySchedule.duration).format(
                                            timeFormatter
                                        )
                                    )
                                )
                            }
                            Text(
                                text = stringResource(
                                    if (nextOpeningTime.dayOfWeek == today) R.string.location_open_next
                                    else R.string.location_open_next_day,
                                    if (nextOpeningTime.dayOfWeek == today) Duration.between(
                                        nextOpeningTime.startTime,
                                        LocalTime.now()
                                    )
                                    else "${
                                        nextOpeningTime.dayOfWeek.getDisplayName(
                                            TextStyle.SHORT,
                                            Locale.getDefault()
                                        )
                                    } ${nextOpeningTime.startTime.format(timeFormatter)}"
                                )
                            )
                        }

                        val userHeading by remember(context) { viewModel.getUserHeading(context) }.collectAsStateWithLifecycle(null)
                        if (userLocation != null && userHeading != null) {
                            val directionArrowAngle by animateFloatAsState(
                                targetValue = userLocation!!.bearingTo(location.toAndroidLocation()) - userHeading!!
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    modifier = Modifier
                                        .padding(top = 8.dp, bottom = 8.dp, start = 16.dp)
                                        .rotate(directionArrowAngle),
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = null
                                )
                                if (distance != null) {
                                    Text(
                                        text = distance.metersToLocalizedString(
                                            context,
                                            insaneUnits
                                        )
                                    )
                                }
                            }
                        }
                    }

                    val showMap by viewModel.showMap.collectAsState()
                    if (showMap) {

                        val zoomLevel = 19
                        val nTiles = 4

                        val (yStart, yStop, xStart, xStop) = getRowColTileCoordinatesAround(
                            location.latitude,
                            location.longitude,
                            zoomLevel,
                            nTiles
                        )

                        val tileServerUrl by viewModel.mapTileServerUrl.collectAsState()

                        for (y in yStart..yStop) {
                            Row(horizontalArrangement = Arrangement.Center) {
                                for (x in xStart..xStop) {
                                    AsyncImage(
                                        modifier = Modifier.width(256.dp).height(256.dp),
                                        imageLoader = SearchableItemVM.mapTileLoader,
                                        model = ImageRequest.Builder(context)
                                            .data("$tileServerUrl/$zoomLevel/$x/$y.png")
                                            .addHeader("User-Agent", SearchableItemVM.mapTileLoaderUserAgent)
                                            .build(),
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    }

                    Toolbar(
                        leftActions = listOf(
                            DefaultToolbarAction(
                                label = stringResource(id = R.string.menu_back),
                                icon = Icons.Rounded.ArrowBack
                            ) {
                                onBack()
                            }
                        ),
                        rightActions = listOfNotNull(
                            DefaultToolbarAction(
                                label = stringResource(id = R.string.menu_map),
                                icon = Icons.Rounded.Map
                            ) {
                                viewModel.viewModelScope.launch {
                                    location.launch(context, null)
                                }
                            },
                            websiteUrl.value.runCatching {
                                val uri = Uri.parse(this)
                                DefaultToolbarAction(
                                    label = stringResource(id = R.string.menu_website),
                                    icon = Icons.Rounded.TravelExplore
                                ) {
                                    viewModel.viewModelScope.launch {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    }
                                }
                            }.getOrNull(),
                        )
                    )

                }
            }
        }
    }
}

private fun getLocationSummary(
    context: Context,
    distance: Float?,
    street: String?,
    houseNumber: String?,
    imperialUnits: Boolean
): String {
    val summary = StringBuilder()
    if (distance != null) {
        summary.append(distance.metersToLocalizedString(context, imperialUnits), ' ')
    }
    if (street != null) {
        summary.append(street, ' ')
        if (houseNumber != null) {
            summary.append(houseNumber, ' ')
        }
    }
    return summary.toString()
}

private fun getDoubleTileCoordinates(
    latitude: Double,
    longitude: Double,
    zoomLevel: Int
): Pair<Double, Double> {
    // https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames#Mathematics
    val latRadians = Math.toRadians(latitude)
    val n = 1 shl zoomLevel
    val xCoordinate = (longitude + 180.0) / 360.0 * n
    val yCoordinate = (1.0 - asinh(tan(latRadians)) / Math.PI) / 2.0 * n

    return yCoordinate to xCoordinate
}

data class TileCoordinateRange(val yStart: Int, val yStop: Int, val xStart: Int, val xStop: Int)

private fun getRowColTileCoordinatesAround(
    latitude: Double,
    longitude: Double,
    zoomLevel: Int,
    nTiles: Int
): TileCoordinateRange {
    if (sqrt(nTiles.toDouble()) % 1.0 != 0.0)
        throw IllegalArgumentException("nTiles must be a square number")

    val sideLen = sqrt(nTiles.toDouble()).toInt()
    val sideLenHalf = sideLen / 2

    val (yCoordinate, xCoordinate) = getDoubleTileCoordinates(latitude, longitude, zoomLevel)
    val xTile = xCoordinate.toInt()
    val yTile = yCoordinate.toInt()

    val yStart: Int
    val yStop: Int
    val xStart: Int
    val xStop: Int

    if (sideLen % 2 == 1) {
        // center tile is defined
        yStart = yTile - sideLenHalf
        yStop = yTile + sideLenHalf
        xStart = xTile - sideLenHalf
        xStop = xTile + sideLenHalf
    } else {
        // center tile is not defined; take adjacent tiles closest to coordinate of interest
        val leftOfCenter = (xCoordinate % 1.0) < 0.5
        val topOfCenter = (yCoordinate % 1.0) < 0.5

        yStart = if (topOfCenter) yTile - sideLen / 2 else yTile - sideLen / 2 + 1
        yStop = if (topOfCenter) yTile + sideLen / 2 - 1 else yTile + sideLen / 2
        xStart = if (leftOfCenter) xTile - sideLen / 2 else xTile - sideLen / 2 + 1
        xStop = if (leftOfCenter) xTile + sideLen / 2 - 1 else xTile + sideLen / 2
    }

    return TileCoordinateRange(yStart, yStop, xStart, xStop)
}
