package de.mm20.launcher2.ui.launcher.search.location

import android.content.Context
import androidx.compose.animation.core.EaseInOutBounce
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import de.mm20.launcher2.search.Location
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.asinh
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
    modifier: Modifier = Modifier,
    // https://wiki.openstreetmap.org/wiki/Attribution_guidelines/2021-06-04_draft#Attribution_text
    osmAttribution: String? = "© OpenStreetMap",
) {
    val context = LocalContext.current
    val tintColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val (yStart, yStop, xStart, xStop) = getRowColTileCoordinatesAround(
        location.latitude,
        location.longitude,
        zoomLevel,
        numberOfTiles
    )

    val sideLength = sqrt(numberOfTiles.toFloat())

    Box(modifier = modifier) {
        Column(modifier = Modifier.matchParentSize()) {
            for (y in yStart..yStop) {
                Row(
                    modifier = Modifier
                        .weight(1f / sideLength)
                        .fillMaxSize()
                ) {
                    for (x in xStart..xStop) {
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
                            colorFilter = ColorFilter.tint(tintColor, BlendMode.Saturation),
                        )
                    }
                }
            }
        }
        val locationBorderColor = MaterialTheme.colorScheme.inversePrimary
        val userLocationColor = MaterialTheme.colorScheme.primary
        val userLocationBorderColor = MaterialTheme.colorScheme.outline

        val locationIndicatorTransition = rememberInfiniteTransition("locationIndicatorTransition")
        val locationIndicatorAnimation by locationIndicatorTransition.animateFloat(
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
        val osmAttributaionSurface = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .5f)

        Canvas(modifier = Modifier.matchParentSize()) {
            if (userLocation != null) {
                val (yUser, xUser) = getDoubleTileCoordinates(
                    latitude = userLocation.first,
                    longitude = userLocation.second,
                    zoomLevel
                )
                // user inside of map tiles?
                if (yStart < yUser && yUser < yStop + 1 &&
                    xStart < xUser && xUser < xStop + 1
                ) {
                    val userIndicatorOffset =
                        Offset(xUser.toFloat(), yUser.toFloat())
                            .scaleToTiles(xStart, xStop, yStart, yStop, size)
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
                    .scaleToTiles(xStart, xStop, yStart, yStop, size)
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
                    color = osmAttributaionSurface,
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
    }
}

// this scaling is not correct, as this linearity may not hold (mercator projection)
// but at this zoom level, it should not be too bad
// still, this does not return the correct offset
private fun Offset.scaleToTiles(
    xStart: Int,
    xStop: Int,
    yStart: Int,
    yStop: Int,
    factor: Size
): Offset = Offset(
    x = (x - xStart) / (xStop + 1f - xStart) * factor.width,
    y = (y - yStart) / (yStop + 1f - yStart) * factor.height
)

private object TileMapRepository : KoinComponent {
    private val context: Context by inject()

    val userAgent = "${context.packageName}/${
        context.packageManager.getPackageInfo(
            context.packageName,
            0
        ).versionName
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
        .crossfade(true)
        .build()
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
