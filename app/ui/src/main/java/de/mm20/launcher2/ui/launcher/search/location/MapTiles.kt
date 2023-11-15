package de.mm20.launcher2.ui.launcher.search.location

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.compose.animation.core.EaseInOutBounce
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.minus
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.LocationCategory
import de.mm20.launcher2.search.OpeningTime
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

typealias UserLocation = Pair<Double, Double>

@Composable
fun MapTiles(
    tileServerUrl: String,
    location: Location,
    zoomLevel: Int,
    numberOfTiles: Int,
    userLocation: UserLocation?,
    applyTheming: Boolean,
    modifier: Modifier = Modifier,
    // https://wiki.openstreetmap.org/wiki/Attribution_guidelines/2021-06-04_draft#Attribution_text
    osmAttribution: String? = "© OpenStreetMap",
) {
    val context = LocalContext.current
    val tintColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val (start, stop) = getRowColTileCoordinatesAround(
        location.latitude,
        location.longitude,
        zoomLevel,
        numberOfTiles
    )

    val sideLength = sqrt(numberOfTiles.toFloat())
    val drawnTiles = remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.matchParentSize()) {
            for (y in start.y..stop.y) {
                Row(
                    modifier = Modifier
                        .weight(1f / sideLength)
                        .fillMaxSize()
                ) {
                    for (x in start.x..stop.x) {
                        AsyncImage(
                            modifier = Modifier
                                .weight(1f / sideLength)
                                .fillMaxSize(),
                            imageLoader = TileMapRepository.loader,
                            model = ImageRequest.Builder(context)
                                .data("$tileServerUrl/$zoomLevel/$x/$y.png")
                                .addHeader(
                                    "User-Agent",
                                    TileMapRepository.userAgent
                                )
                                .build(),
                            contentDescription = null,
                            colorFilter = if (applyTheming) ColorFilter.tint(
                                tintColor,
                                BlendMode.Saturation
                            ) else null,
                            onState = {
                                if (it is AsyncImagePainter.State.Success)
                                    drawnTiles.intValue++
                                if (it is AsyncImagePainter.State.Error)
                                    Log.e(
                                        "MapTiles",
                                        "Error loading tile: $x, $y @$zoomLevel",
                                        it.result.throwable
                                    )
                            }
                        )
                    }
                }
            }
        }

        if (numberOfTiles == drawnTiles.intValue) {
            val locationBorderColor = MaterialTheme.colorScheme.inversePrimary
            val userLocationColor = MaterialTheme.colorScheme.primary
            val userLocationBorderColor = MaterialTheme.colorScheme.outline

            val infiniteTransition = rememberInfiniteTransition("infiniteTransition")
            val locationIndicatorAnimation by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = EaseInOutBounce),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "locationIndicatorAnimation"
            )

            val textMeasurer = rememberTextMeasurer()
            val osmAttributionTextStyle = MaterialTheme.typography.labelSmall
            val osmAttributionTextColor = MaterialTheme.colorScheme.onSurface
            val osmAttributionSurface =
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .5f)

            Canvas(modifier = Modifier.matchParentSize()) {
                if (userLocation != null) {
                    val (yUser, xUser) = getDoubleTileCoordinates(
                        latitude = userLocation.first,
                        longitude = userLocation.second,
                        zoomLevel
                    )
                    // user inside of map tiles?
                    if (start.y < yUser && yUser < stop.y + 1 &&
                        start.x < xUser && xUser < stop.x + 1
                    ) {
                        val userIndicatorOffset =
                            Offset(xUser.toFloat(), yUser.toFloat())
                                .scaleToTiles(start, sideLength, size)
                        drawCircle(
                            color = userLocationBorderColor,
                            radius = 18.5f * locationIndicatorAnimation,
                            center = userIndicatorOffset
                        )
                        drawCircle(
                            color = userLocationColor,
                            radius = 13.5f * locationIndicatorAnimation,
                            center = userIndicatorOffset
                        )
                    }
                }

                val (yLocation, xLocation) = getDoubleTileCoordinates(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    zoomLevel
                )
                val locationIndicatorOffset =
                    Offset(xLocation.toFloat(), yLocation.toFloat())
                        .scaleToTiles(start, sideLength, size)
                drawCircle(
                    color = locationBorderColor,
                    radius = 32f,
                    center = locationIndicatorOffset,
                    alpha = locationIndicatorAnimation,
                    blendMode = BlendMode.DstIn
                )
                if (osmAttribution != null) {
                    val measureResult = textMeasurer.measure(
                        osmAttribution,
                        maxLines = 1,
                        style = osmAttributionTextStyle
                    )
                    val osmLabelPadding = 6f
                    val textOffset = Offset(
                        x = size.width - measureResult.size.width - osmLabelPadding,
                        y = size.height - measureResult.size.height - osmLabelPadding
                    )
                    drawRoundRect(
                        color = osmAttributionSurface,
                        topLeft = textOffset - Offset(osmLabelPadding, 0f),
                        size = Size(
                            width = measureResult.size.width + 2 * osmLabelPadding,
                            height = measureResult.size.height + osmLabelPadding
                        ),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                    drawText(
                        measureResult,
                        color = osmAttributionTextColor,
                        topLeft = textOffset
                    )
                }
            }
        } else {
            val loadingColor = MaterialTheme.colorScheme.secondary
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(.15f),
                color = loadingColor,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

// this scaling is not correct, as this linearity may not hold (mercator projection)
// but at this zoom level, it should not be too bad
// still, this does not return the correct offset
// maybe osm does not display its labels correctly?
private fun Offset.scaleToTiles(
    tilesTopLeft: IntOffset,
    sideLenTiles: Float,
    boardSize: Size,
): Offset {
    assert(boardSize.width == boardSize.height)

    return (this - tilesTopLeft) * (boardSize.width / sideLenTiles)
}

private object TileMapRepository : KoinComponent {
    private val context: Context by inject()

    val userAgent = "${context.packageName}/${
        context.packageManager.getPackageInfo(
            context.packageName,
            0
        )?.versionName ?: "dev"
    }"

    val loader = ImageLoader
        .Builder(context)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.05)
                .build()
        }
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("osm_tiles"))
                .maxSizePercent(0.01)
                .build()
        }
        .diskCachePolicy(CachePolicy.ENABLED)
        .respectCacheHeaders(true)
        .networkCachePolicy(CachePolicy.ENABLED)
        .build()
}

private fun getDoubleTileCoordinates(
    latitude: Double,
    longitude: Double,
    zoomLevel: Int
): Pair<Double, Double> {
    // https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames#Mathematics
    val latRadians = Math.toRadians(latitude)
    val xCoordinate = (longitude + 180.0) / 360.0 * (1 shl zoomLevel)
    val yCoordinate =
        (1.0 - ln(tan(latRadians) + 1.0 / cos(latRadians)) / Math.PI) * (1 shl (zoomLevel - 1))

    return yCoordinate to xCoordinate
}

private fun getTopLeftLatLon(xTile: Int, yTile: Int, zoom: Int): Pair<Double, Double> {
    val n = 1 shl zoom
    val lonDeg = xTile / n * 360.0 - 180.0
    val latRad = atan(sinh(Math.PI * (1.0 - 2.0 * yTile / n)))
    val latDeg = Math.toDegrees(latRad)

    return latDeg to lonDeg
}

data class TileCoordinateRange(val start: IntOffset, val stop: IntOffset)

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

    return TileCoordinateRange(IntOffset(xStart, yStart), IntOffset(xStop, yStop))
}

@Preview
@Composable
private fun MapTilesPreview() {
    val context = LocalContext.current

    if (GlobalContext.getKoinApplicationOrNull() == null) {
        startKoin {
            androidContext(context)
        }
    }

    val borderShape = MaterialTheme.shapes.medium

    MapTiles(
        modifier = Modifier
            .size(300.dp)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                borderShape
            )
            .clip(borderShape),
        tileServerUrl = "https://tile.openstreetmap.org",
        location = MockLocation,
        zoomLevel = 19,
        numberOfTiles = 9,
        applyTheming = false,
        userLocation = 52.51623 to 13.4048
    )
}

internal object MockLocation : Location {

    override val domain: String = "MOCKLOCATION"
    override val key: String = "MOCKLOCATION"
    override val label: String = "Brandenburger Tor"

    override val latitude = 52.5162700
    override val longitude = 13.3777021

    override suspend fun getCategory(): LocationCategory = LocationCategory.OTHER

    override suspend fun getStreet(): String = "Pariser Platz"

    override suspend fun getHouseNumber(): String = "1"

    override suspend fun getOpeningHours(): ImmutableList<OpeningTime> =
        enumValues<DayOfWeek>().map {
            OpeningTime(
                dayOfWeek = it,
                startTime = LocalTime.MIDNIGHT,
                duration = Duration.ofDays(1)
            )
        }.toImmutableList()

    override suspend fun getWebsiteUrl(): String = "https://en.wikipedia.org/wiki/Brandenburg_Gate"

    override fun overrideLabel(label: String): SavableSearchable = TODO()

    override fun launch(context: Context, options: Bundle?): Boolean =
        context.tryStartActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://en.wikipedia.org/wiki/Brandenburg_Gate")
            )
        )

    override fun getSerializer(): SearchableSerializer = TODO()
}
