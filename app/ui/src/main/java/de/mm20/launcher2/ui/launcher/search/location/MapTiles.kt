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
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
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
import de.mm20.launcher2.search.OpeningHours
import de.mm20.launcher2.search.OpeningSchedule
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer
import kotlinx.collections.immutable.toImmutableList
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import kotlin.math.asinh
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

typealias UserLocation = Pair<Double, Double>

@Composable
fun MapTiles(
    tileServerUrl: String,
    location: Location,
    initialZoomLevel: Int,
    numberOfTiles: Int,
    userLocation: UserLocation?,
    applyTheming: Boolean,
    modifier: Modifier = Modifier,
    // https://wiki.openstreetmap.org/wiki/Attribution_guidelines/2021-06-04_draft#Attribution_text
    osmAttribution: String? = "© OpenStreetMap",
) {
    val context = LocalContext.current
    val tintColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val sideLength = sqrt(numberOfTiles.toFloat())

    val previousZoomLevel = remember { mutableIntStateOf(-1) }
    val (start, stop, zoom) = remember(userLocation) {
        userLocation
            ?.runCatching {
                getEnclosingTiles(
                    location,
                    numberOfTiles,
                    this,
                    previousZoomLevel
                )
            }
            ?.onFailure {
                Log.e("MapTiles", "Enclosing calculation failed", it)
            }
            ?.getOrNull()
            ?: getTilesAround(location, initialZoomLevel, numberOfTiles)
    }

    val imageStates = remember { (0 until numberOfTiles).map { false }.toMutableStateList() }

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
                                .data("$tileServerUrl/$zoom/$x/$y.png")
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
                                val stateIndex =
                                    (y - start.y) * (stop.y - start.y + 1) + (x - start.x)
                                if (it is AsyncImagePainter.State.Loading)
                                    imageStates[stateIndex] = false
                                if (it is AsyncImagePainter.State.Success)
                                    imageStates[stateIndex] = true
                                if (it is AsyncImagePainter.State.Error)
                                    Log.e(
                                        "MapTiles",
                                        "Error loading tile: $x, $y @$zoom",
                                        it.result.throwable
                                    )
                            }
                        )
                    }
                }
            }
        }

        if (imageStates.all { it }) {
            val locationBorderColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                        zoom
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
                    zoom
                )
                val locationIndicatorOffset =
                    Offset(xLocation.toFloat(), yLocation.toFloat())
                        .scaleToTiles(start, sideLength, size)
                drawCircle(
                    color = locationBorderColor,
                    radius = 32f,
                    center = locationIndicatorOffset,
                    alpha = locationIndicatorAnimation * 0.5f
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

// osm does not necessarily display its labels correctly for nodes, so this will be off in some cases
private fun Offset.scaleToTiles(
    tilesTopLeft: IntOffset,
    sideLenTiles: Float,
    boardSize: Size,
): Offset {
    assert(boardSize.width == boardSize.height)

    return (this - tilesTopLeft) * (boardSize.width / sideLenTiles)
}

private fun getDoubleTileCoordinates(
    latitude: Double,
    longitude: Double,
    zoomLevel: Int
): Pair<Double, Double> {
    // https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames#Mathematics
    val latRadians = Math.toRadians(latitude)
    val xCoordinate = (longitude + 180.0) / 360.0 * (1 shl zoomLevel)
    val yCoordinate = (1.0 - asinh(tan(latRadians)) / Math.PI) * (1 shl (zoomLevel - 1))

    return yCoordinate to xCoordinate
}

data class TileCoordinateRange(val start: IntOffset, val stop: IntOffset, val zoomLevel: Int)

private fun getTilesAround(
    location: Location,
    zoomLevel: Int,
    nTiles: Int
): TileCoordinateRange {
    if (sqrt(nTiles.toDouble()) % 1.0 != 0.0)
        throw IllegalArgumentException("nTiles must be a square number")

    val sideLen = sqrt(nTiles.toDouble()).toInt()
    val sideLenHalf = sideLen / 2

    val (yCoordinate, xCoordinate) = getDoubleTileCoordinates(
        location.latitude,
        location.longitude,
        zoomLevel
    )
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

        yStart = if (topOfCenter) yTile - sideLenHalf else yTile - sideLenHalf + 1
        yStop = if (topOfCenter) yTile + sideLenHalf - 1 else yTile + sideLenHalf
        xStart = if (leftOfCenter) xTile - sideLenHalf else xTile - sideLenHalf + 1
        xStop = if (leftOfCenter) xTile + sideLenHalf - 1 else xTile + sideLenHalf
    }

    return TileCoordinateRange(IntOffset(xStart, yStart), IntOffset(xStop, yStop), zoomLevel)
}

const val ZOOM_MAX = 19
const val ZOOM_MIN = 0

private fun getEnclosingTiles(
    location: Location,
    nTiles: Int,
    userLocation: UserLocation,
    previousZoomLevel: MutableIntState,
): TileCoordinateRange {
    if (sqrt(nTiles.toDouble()) % 1.0 != 0.0)
        throw IllegalArgumentException("nTiles must be a square number")

    val sideLen = sqrt(nTiles.toDouble()).toInt()
    val sideLenHalf = sideLen / 2

    // start at previous zoom to do less calculations, because if
    //  - user comes closer to location:
    //      we might be able to increase the zoom level by one
    //  - user moves further away from location (which is less likely):
    //      we still iterate down to minimum zoom
    for (zoomLevel in previousZoomLevel
        .intValue
        .let { if (it == -1) ZOOM_MAX else min(it + 1, ZOOM_MAX) } downTo ZOOM_MIN
    ) {

        val (locationY, locationX) = getDoubleTileCoordinates(
            location.latitude,
            location.longitude,
            zoomLevel
        )
        val (userY, userX) = getDoubleTileCoordinates(
            userLocation.first,
            userLocation.second,
            zoomLevel
        )

        val (locationTileY, locationTileX) = locationY.toInt() to locationX.toInt()
        val (userTileY, userTileX) = userY.toInt() to userX.toInt()

        if (locationTileY - sideLenHalf <= userTileY && userTileY <= locationTileY + sideLenHalf &&
            locationTileX - sideLenHalf <= userTileX && userTileX <= locationTileX + sideLenHalf
        ) {
            var xStart = min(locationTileX, userTileX)
            var yStart = min(locationTileY, userTileY)
            var xStop = max(locationTileX, userTileX)
            var yStop = max(locationTileY, userTileY)

            val xRem = sideLen - xStop + xStart - 1
            if (0 < xRem) {
                val leftOfCenter = (locationX % 1.0) < 0.5
                val ceil = ceil(xRem / 2.0).toInt()
                val floor = floor(xRem / 2.0).toInt()

                if (leftOfCenter) {
                    xStart -= ceil
                    xStop += floor
                } else {
                    xStart -= floor
                    xStop += ceil
                }
            }
            val yRem = sideLen - yStop + yStart - 1
            if (0 < yRem) {
                val topOfCenter = (locationY % 1.0) < 0.5
                val ceil = ceil(yRem / 2.0).toInt()
                val floor = floor(yRem / 2.0).toInt()

                if (topOfCenter) {
                    yStart -= ceil
                    yStop += floor
                } else {
                    yStart -= floor
                    yStop += ceil
                }
            }

            previousZoomLevel.intValue = zoomLevel

            return TileCoordinateRange(
                IntOffset(xStart, yStart),
                IntOffset(xStop, yStop),
                zoomLevel
            )
        }
    }

    throw IllegalStateException("Unreachable (right?) | lat: ${location.latitude} | lon: ${location.longitude} | user: $userLocation | nTiles: $nTiles")
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
        initialZoomLevel = 19,
        numberOfTiles = 9,
        applyTheming = false,
        userLocation = 52.51623 to 13.4048
    )
}

internal object MockLocation : Location {

    override val domain: String = "MOCKLOCATION"
    override val key: String = "MOCKLOCATION"
    override val label: String = "Brandenburger Tor"
    override val fixMeUrl: String = "https://www.openstreetmap.org/fixthemap"

    override val latitude = 52.5162700
    override val longitude = 13.3777021

    override suspend fun getCategory(): LocationCategory = LocationCategory.OTHER

    override suspend fun getStreet(): String = "Pariser Platz"

    override suspend fun getHouseNumber(): String = "1"

    override suspend fun getOpeningSchedule(): OpeningSchedule =
        OpeningSchedule(true, emptyList<OpeningHours>().toImmutableList())

    override suspend fun getWebsiteUrl(): String = "https://en.wikipedia.org/wiki/Brandenburg_Gate"

    override suspend fun getPhoneNumber(): String = "+49 1234567"

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
