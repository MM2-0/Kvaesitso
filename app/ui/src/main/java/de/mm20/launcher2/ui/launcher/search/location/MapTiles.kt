package de.mm20.launcher2.ui.launcher.search.location

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.animation.core.EaseInOutCirc
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import de.mm20.launcher2.ktx.PI
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.LocationCategory
import de.mm20.launcher2.search.OpeningHours
import de.mm20.launcher2.search.OpeningSchedule
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.ui.ktx.DegreesConverter
import de.mm20.launcher2.ui.ktx.contrast
import de.mm20.launcher2.ui.ktx.hue
import de.mm20.launcher2.ui.ktx.hueRotate
import de.mm20.launcher2.ui.ktx.invert
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import kotlinx.collections.immutable.toImmutableList
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

data class UserLocation(val lat: Double, val lon: Double, val heading: Float? = null)

@Composable
fun MapTiles(
    tileServerUrl: String,
    location: Location,
    userLocation: () -> UserLocation?,
    maxZoomLevel: Int,
    tiles: IntSize,
    applyTheming: Boolean,
    modifier: Modifier = Modifier,
    // https://wiki.openstreetmap.org/wiki/Attribution_guidelines/2021-06-04_draft#Attribution_text
    osmAttribution: String? = "© OpenStreetMap",
) {
    val tintColor = MaterialTheme.colorScheme.surface
    val darkMode = LocalDarkTheme.current

    val userLocation = userLocation()

    val (start, stop, zoom) = remember(location, userLocation) {
        getTileRange(location, userLocation, tiles, maxZoomLevel)
    }

    val sideLength = stop.x - start.x + 1

    val colorMatrix = remember(applyTheming, darkMode, tintColor) {
        // darkreader css for openstreetmap tiles
        // invert(93.7%) hue-rotate(180deg) contrast(90.6%)
        val tintHueDeg = tintColor.hue * 180f / Float.PI

        if (!darkMode && applyTheming) {
            ColorMatrix()
                .hueRotate(tintHueDeg)
        } else if (darkMode) {
            ColorMatrix()
                .invert(0.937f)
                .hueRotate(180f + if (applyTheming) tintHueDeg else 0f)
                .contrast(0.906f)
        } else null
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            for (y in start.y..stop.y) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    for (x in start.x..stop.x) {
                        AsyncImage(
                            modifier = Modifier
                                .weight(1f / sideLength)
                                .aspectRatio(1f)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            imageLoader = MapTileLoader.loader,
                            model = MapTileLoader.getTileRequest(tileServerUrl, x, y, zoom),
                            contentDescription = null,
                            colorFilter = colorMatrix?.let { ColorFilter.colorMatrix(it) },
                            filterQuality = FilterQuality.High,
                        )
                    }
                }
            }
        }

        val locationBorderColor =
            if (applyTheming) MaterialTheme.colorScheme.error else Color(0xFFEFA521) // orange-ish
        val userLocationColor =
            if (applyTheming) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF35A82C) // darkish green
        val userLocationBorderColor =
            if (applyTheming) {
                MaterialTheme.colorScheme.errorContainer
            } else if (darkMode) {
                Color(0xFF777777)
            } else {
                Color(0xFFE5E5E5)
            }

        val infiniteTransition = rememberInfiniteTransition("infiniteTransition")
        val userLocAnimation by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "userLocAnimation"
        )
        val poiLocAnimation by infiniteTransition.animateFloat(
            initialValue = 30f,
            targetValue = 20f,
            animationSpec = infiniteRepeatable(
                animation = tween(750, easing = EaseInOutCirc),
                repeatMode = RepeatMode.Reverse
            ),
            label = "poiLocAnimation"
        )
        val tileSize = minWidth / tiles.width.toFloat()
        val locationTileCoordinates =
            getTileCoordinates(location.latitude, location.longitude, zoom)

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(12.dp)
                .height(4.dp)
                .absoluteOffset(
                    x = (locationTileCoordinates.x - start.x) * tileSize - 6.dp,
                    y = (locationTileCoordinates.y - start.y) * tileSize - 2.dp,
                )
                .shadow(1.dp, CircleShape)
        )

        Icon(
            imageVector = Icons.Rounded.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(24.dp)
                .absoluteOffset(
                    x = (locationTileCoordinates.x - start.x) * tileSize - 12.dp,
                    y = (locationTileCoordinates.y - start.y) * tileSize - 22.dp,
                )
        )
        if (userLocation != null) {
            val userTileCoordinates = getTileCoordinates(userLocation.lat, userLocation.lon, zoom)

            if (userLocation.heading != null) {
                val headingAnim by animateValueAsState(
                    targetValue = userLocation.heading,
                    typeConverter = Float.DegreesConverter
                )

                Icon(
                    imageVector = Icons.Rounded.Navigation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(16.dp)
                        .absoluteOffset(
                            x = (userTileCoordinates.x - start.x) * tileSize - 8.dp,
                            y = (userTileCoordinates.y - start.y) * tileSize - 8.dp,
                        )
                        .rotate(headingAnim)
                        .absoluteOffset(y = -8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(16.dp)
                    .absoluteOffset(
                        x = (userTileCoordinates.x - start.x) * tileSize - 8.dp,
                        y = (userTileCoordinates.y - start.y) * tileSize - 8.dp,
                    )
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.onTertiary, CircleShape)
                    .shadow(1.dp, CircleShape)
            )

            if (osmAttribution != null) {
                Text(
                    text = osmAttribution,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .5f))
                        .padding(top = 2.dp, bottom = 2.dp, start = 4.dp, end = 4.dp)
                )
            }
        }
    }
}

/**
 * Returns the tile coordinates for a given location at a given zoom level (not rounded).
 */
private fun getTileCoordinates(
    latitude: Double,
    longitude: Double,
    zoomLevel: Int
): Offset {
    // https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames#Mathematics
    val x = ((longitude + 180) / 360) * 2.0.pow(zoomLevel)
    val latRad = Math.toRadians(latitude)
    val y = (1.0 - ((ln(tan(latRad) + (1.0 / cos(latRad)))) / PI)) * (2.0.pow(zoomLevel - 1))

    return Offset(x.toFloat(), y.toFloat())
}

data class TileCoordinateRange(val start: IntOffset, val stop: IntOffset, val zoomLevel: Int)

private fun getTileRange(
    center: Float,
    tiles: Int,
): IntRange {
    val centerRounded = center.toInt()
    return if (tiles % 2 == 0) {
        if (center % 1 >= 0.5f) {
            (centerRounded - (tiles / 2) + 1)..(centerRounded + (tiles / 2))
        } else {
            (centerRounded - (tiles / 2))..<centerRounded + (tiles / 2)
        }
    } else {
        (centerRounded - (tiles / 2))..(centerRounded + (tiles / 2))
    }
}

private fun getTileRange(
    location: Location,
    userLocation: UserLocation?,
    tiles: IntSize,
    maxZoomLevel: Int = ZOOM_MAX
): TileCoordinateRange {
    if (userLocation == null) {
        val tileCoords = getTileCoordinates(location.latitude, location.longitude, maxZoomLevel)

        val xRange = getTileRange(tileCoords.x, tiles.width)
        val yRange = getTileRange(tileCoords.y, tiles.height)

        return TileCoordinateRange(
            IntOffset(xRange.first, yRange.first),
            IntOffset(xRange.last, yRange.last),
            maxZoomLevel
        )
    }

    for (z in maxZoomLevel downTo ZOOM_MIN) {
        val tileCoords = getTileCoordinates(location.latitude, location.longitude, z)
        val userCoords = getTileCoordinates(userLocation.lat, userLocation.lon, z)

        val centerX = (tileCoords.x + userCoords.x) / 2f
        val centerY = (tileCoords.y + userCoords.y) / 2f

        val minX = floor(minOf(tileCoords.x, userCoords.x)).toInt()
        val maxX = ceil(maxOf(tileCoords.x, userCoords.x)).toInt()
        val minY = floor(minOf(tileCoords.y, userCoords.y)).toInt()
        val maxY = ceil(maxOf(tileCoords.y, userCoords.y)).toInt()

        if (maxX - minX <= tiles.width && maxY - minY <= tiles.height) {
            val diffX = tiles.width - (maxX - minX)
            val diffY = tiles.height - (maxY - minY)

            val xRange = if (diffX % 2 == 0) {
                (minX - diffX / 2)..<(maxX + diffX / 2)
            } else if (centerX % 1 >= 0.5f) {
                (minX - diffX / 2)..<(maxX + diffX / 2 + 1)
            } else {
                (minX - diffX / 2 - 1)..<(maxX + diffX / 2)

            }

            val yRange = if (diffY % 2 == 0) {
                (minY - diffY / 2)..<(maxY + diffY / 2)
            } else if (centerY % 1 >= 0.5f) {
                (minY - diffY / 2)..<(maxY + diffY / 2 + 1)
            } else {
                (minY - diffY / 2 - 1)..<(maxY + diffY / 2)
            }

            return TileCoordinateRange(
                IntOffset(xRange.first, yRange.first),
                IntOffset(xRange.last, yRange.last),
                z
            )
        }
    }

    return TileCoordinateRange(IntOffset(0, 0), IntOffset(0, 0), 0)
}

const val ZOOM_MAX = 19
const val ZOOM_MIN = 0

private object MapTileLoader : KoinComponent {
    private val context: Context by inject()

    private val userAgent = "${context.packageName}/${
        context.packageManager.getPackageInfo(
            context.packageName,
            0
        )?.versionName ?: "dev"
    }"

    fun getTileRequest(tileServerUrl: String, x: Int, y: Int, zoom: Int): ImageRequest {
        return ImageRequest.Builder(context)
            .data("$tileServerUrl/$zoom/$x/$y.png")
            .addHeader(
                "User-Agent",
                userAgent
            )
            .build()
    }

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
        tileServerUrl = "http://tile.openstreetmap.org",
        location = MockLocation,
        maxZoomLevel = 19,
        tiles = IntSize(3, 3),
        applyTheming = false,
        userLocation = { UserLocation(52.51623, 13.4048) }
    )
}

internal object MockLocation : Location {

    override val domain: String = "MOCKLOCATION"
    override val key: String = "MOCKLOCATION"
    override val label: String = "Brandenburger Tor"
    override val fixMeUrl: String = "https://www.openstreetmap.org/fixthemap"

    override val latitude = 52.5162700
    override val longitude = 13.3777021

    override var category: LocationCategory? = LocationCategory.OTHER

    override val street: String = "Pariser Platz"

    override val houseNumber: String = "1"

    override val openingSchedule: OpeningSchedule =
        OpeningSchedule(true, emptyList<OpeningHours>().toImmutableList())

    override val websiteUrl: String = "https://en.wikipedia.org/wiki/Brandenburg_Gate"

    override val phoneNumber: String = "+49 1234567"

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
