package de.mm20.launcher2.ui.launcher.search.location

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
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
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.location.Address
import de.mm20.launcher2.search.location.Departure
import de.mm20.launcher2.search.location.LineType
import de.mm20.launcher2.search.location.LocationIcon
import de.mm20.launcher2.search.location.OpeningSchedule
import de.mm20.launcher2.search.location.PaymentMethod
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.ktx.DegreesConverter
import de.mm20.launcher2.ui.ktx.contrast
import de.mm20.launcher2.ui.ktx.hue
import de.mm20.launcher2.ui.ktx.hueRotate
import de.mm20.launcher2.ui.ktx.invert
import de.mm20.launcher2.ui.ktx.toDp
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import java.time.Duration
import java.time.ZonedDateTime
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

    val tileRange = remember(location, userLocation) {
        getTileRange(location, userLocation, tiles, maxZoomLevel)
    }


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

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr
    ) {
        BoxWithConstraints(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                tileRange,
                transitionSpec = {
                    if (targetState.zoomLevel == initialState.zoomLevel) {
                        val initialCenterX = (initialState.stop.x + initialState.start.x) / 2f
                        val targetCenterX = (targetState.stop.x + targetState.start.x) / 2f
                        val initialCenterY = (initialState.stop.y + initialState.start.y) / 2f
                        val targetCenterY = (targetState.stop.y + targetState.start.y) / 2f
                        val initialDeltaX = targetCenterX - initialCenterX
                        val targetDeltaX = targetCenterX - initialCenterX
                        val initialDeltaY = targetCenterY - initialCenterY
                        val targetDeltaY = targetCenterY - initialCenterY

                        return@AnimatedContent slideIn {
                            IntOffset(
                                (targetDeltaX * (it.width / tiles.width)).toInt(),
                                (targetDeltaY * (it.height / tiles.height)).toInt()
                            )
                        } togetherWith slideOut {
                            IntOffset(
                                -(initialDeltaX * (it.width / tiles.width)).toInt(),
                                -(initialDeltaY * (it.height / tiles.height)).toInt()
                            )
                        }
                    }
                    val scale = 2f.pow(targetState.zoomLevel - initialState.zoomLevel)

                    fadeIn() + scaleIn(initialScale = 1f / scale) togetherWith
                            fadeOut() + scaleOut(targetScale = scale)
                }
            ) { (start, stop, zoom) ->
                var tileWidth by remember { mutableIntStateOf(0) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Needed to force all tiles to be the _exact_ same size. With weight(1f) we get rounding errors and gaps.
                        .onSizeChanged { tileWidth = it.width / (stop.x - start.x + 1) }
                ) {
                    for (y in start.y..stop.y) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (x in start.x..stop.x) {
                                AsyncImage(
                                    modifier = Modifier
                                        .width(tileWidth.toDp())
                                        .height(tileWidth.toDp())
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
                    painterResource(R.drawable.location_on_24px_filled),
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
                    val userIndicatorOffset by animateOffsetAsState(
                        targetValue = getTileCoordinates(
                            userLocation.lat,
                            userLocation.lon,
                            zoom
                        ).let {
                            Offset(
                                (it.x - start.x) * tileSize.value - 10f,
                                (it.y - start.y) * tileSize.value - 10f
                            )
                        },
                        animationSpec = tween()
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(20.dp)
                            .absoluteOffset(
                                userIndicatorOffset.x.dp,
                                userIndicatorOffset.y.dp
                            )
                            .background(MaterialTheme.colorScheme.onTertiary, CircleShape)
                            .shadow(1.dp, CircleShape)
                    ) {
                        if (userLocation.heading != null) {
                            val headingAnim by animateValueAsState(
                                targetValue = userLocation.heading,
                                typeConverter = Float.DegreesConverter
                            )
                            Icon(
                                painterResource(R.drawable.assistant_navigation_20px),
                                null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(headingAnim)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .size(16.dp)
                                    .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                            )
                        }
                    }

                    if (osmAttribution != null) {
                        Text(
                            text = osmAttribution,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                                        alpha = .5f
                                    )
                                )
                                .padding(top = 2.dp, bottom = 2.dp, start = 4.dp, end = 4.dp)
                        )
                    }
                }
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
        val url = if (
            tileServerUrl.contains("\${x}") &&
            tileServerUrl.contains("\${y}") &&
            tileServerUrl.contains("\${z}")
        ) {
            tileServerUrl
                .replace("\${x}", x.toString())
                .replace("\${y}", y.toString())
                .replace("\${z}", zoom.toString())
        } else {
            "$tileServerUrl/$zoom/$x/$y.png"
        }
        return ImageRequest.Builder(context)
            .data(url)
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

private object MockLocation : Location {

    override val domain: String = "MOCKLOCATION"
    override val key: String = "MOCKLOCATION"
    override val label: String = "Brandenburger Tor"
    override val fixMeUrl: String = "https://www.openstreetmap.org/fixthemap"

    override val latitude = 52.5162700
    override val longitude = 13.3777021

    override val icon: LocationIcon? = null
    override var category: String? = "Landmark"

    override val address: Address = Address(
        address = "Pariser Platz 1",
        city = "Berlin",
        postalCode = "10117",
        country = "Germany"
    )

    override val openingSchedule: OpeningSchedule =
        OpeningSchedule.TwentyFourSeven

    override val acceptedPaymentMethods: Map<PaymentMethod, Boolean>?
        get() = mapOf(PaymentMethod.Card to true, PaymentMethod.Cash to false)

    override val websiteUrl: String = "https://en.wikipedia.org/wiki/Brandenburg_Gate"

    override val phoneNumber: String = "+49 1234567"

    override val emailAddress: String = "abc@de.fg"

    override fun overrideLabel(label: String): SavableSearchable = TODO()

    override fun launch(context: Context, options: Bundle?): Boolean =
        context.tryStartActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://en.wikipedia.org/wiki/Brandenburg_Gate")
            )
        )

    override fun getSerializer(): SearchableSerializer = TODO()

    override val departures: List<Departure> = listOf(
        Departure(
            ZonedDateTime.now() + Duration.ofMinutes(3),
            Duration.ofMinutes(1),
            "B2",
            "heaven",
            LineType.Bus,
            android.graphics.Color.valueOf(0xFAFAFAFA)
        )
    )

    override val userRating: Float
        get() = 0.9f

    override val userRatingCount: Int = 553
}
