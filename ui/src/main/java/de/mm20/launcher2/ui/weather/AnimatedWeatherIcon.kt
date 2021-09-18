package de.mm20.launcher2.ui.weather

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.*
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.icons.*
import kotlin.math.PI
import kotlin.math.sin


@Composable
fun AnimatedWeatherIcon(
    modifier: Modifier = Modifier,
    icon: WeatherIcon,
    night: Boolean = false
) {

    Box(
        modifier = modifier
            .padding(8.dp)
            .size(64.dp)
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        SunMoon(icon, night)
        Precipitation(icon)
        LightningBolt(icon)
        LightningBolt2(icon)
        Cloud1(icon)
        Wind2(icon)
        Cloud2(icon)
        Cloud3(icon)
        Fog(icon)
        Hot(icon)
        Cold(icon)
        Wind1(icon)
    }
}

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
private fun SunMoon(icon: WeatherIcon, night: Boolean) {
    val sunMoonIcon = animatedVectorResource(R.drawable.anim_weather_sun_moon)
    val transition = updateTransition(targetState = icon, "AnimatedWeatherIcon")

    val color by animateColorAsState(
        if (night) MaterialTheme.colors.weatherMoon else MaterialTheme.colors.weatherSun
    )
    val scale by transition.animateFloat(label = "sunScale") {
        when (it) {
            WeatherIcon.Clear,
            WeatherIcon.PartlyCloudy,
            WeatherIcon.BrokenClouds,
            WeatherIcon.Haze -> 1f
            WeatherIcon.MostlyCloudy -> 0.8f
            else -> 0f
        }
    }
    val offset by transition.animateOffset(label = "sunOffset") {
        when (it) {
            WeatherIcon.Clear,
            WeatherIcon.BrokenClouds,
            WeatherIcon.Haze -> Offset.Zero
            WeatherIcon.MostlyCloudy -> Offset(6f, -4f)
            WeatherIcon.PartlyCloudy -> Offset(3f, -2f)
            else -> Offset.Zero
        }
    }

    Icon(
        sunMoonIcon.painterFor(atEnd = night),
        null,
        modifier = Modifier
            .size(32.dp)
            .offset(offset.x.dp, offset.y.dp)
            .scale(scale),
        tint = color
    )
}

@Composable
private fun LightningBolt(icon: WeatherIcon) {
    val transition = updateTransition(targetState = icon, "AnimatedWeatherIconBolt")

    val scale by transition.animateFloat(label = "scale") {
        when (it) {
            WeatherIcon.Thunderstorm,
            WeatherIcon.ThunderstormWithRain,
            WeatherIcon.HeavyThunderstormWithRain,
            WeatherIcon.HeavyThunderstorm -> 0.6f
            else -> 0f
        }
    }
    val offset by transition.animateOffset(label = "offset") {
        when (it) {
            WeatherIcon.ThunderstormWithRain,
            WeatherIcon.Thunderstorm -> Offset(1f, 8f)
            WeatherIcon.HeavyThunderstormWithRain,
            WeatherIcon.HeavyThunderstorm -> Offset(6f, 8f)
            else -> Offset.Zero
        }
    }

    Icon(
        Icons.Rounded.Bolt,
        null,
        modifier = Modifier
            .size(32.dp)
            .offset(offset.x.dp, offset.y.dp)
            .scale(scale),
        tint = MaterialTheme.colors.weatherBolt
    )
}

@Composable
private fun LightningBolt2(icon: WeatherIcon) {
    val transition = updateTransition(targetState = icon, "AnimatedWeatherIconBolt2")

    val scale by transition.animateFloat(label = "scale") {
        when (it) {
            WeatherIcon.HeavyThunderstormWithRain,
            WeatherIcon.HeavyThunderstorm -> 0.5f
            else -> 0f
        }
    }
    val offset by transition.animateOffset(label = "offset") {
        when (it) {
            WeatherIcon.HeavyThunderstormWithRain,
            WeatherIcon.HeavyThunderstorm -> Offset(-9f, 5f)
            else -> Offset.Zero
        }
    }

    Icon(
        Icons.Rounded.Bolt,
        null,
        modifier = Modifier
            .size(32.dp)
            .offset(offset.x.dp, offset.y.dp)
            .scale(scale),
        tint = MaterialTheme.colors.weatherBolt
    )
}

@Composable
private fun Cloud1(icon: WeatherIcon) {
    val transition = updateTransition(targetState = icon, "AnimatedWeatherIconCloud1")

    val scale by transition.animateFloat(label = "scale") {
        when (it) {
            WeatherIcon.Clear -> 0f
            WeatherIcon.Thunderstorm,
            WeatherIcon.ThunderstormWithRain,
            WeatherIcon.HeavyThunderstorm,
            WeatherIcon.HeavyThunderstormWithRain,
            WeatherIcon.Showers,
            WeatherIcon.Drizzle,
            WeatherIcon.Sleet,
            WeatherIcon.Snow,
            WeatherIcon.Hail,
            WeatherIcon.Wind,
            WeatherIcon.Cloudy,
            WeatherIcon.Storm,
            WeatherIcon.Fog -> 1.4f
            WeatherIcon.MostlyCloudy,
            WeatherIcon.PartlyCloudy -> 1f
            WeatherIcon.BrokenClouds -> 0.9f
            else -> 0f
        }
    }
    val offset by transition.animateOffset(label = "offset") {
        when (it) {
            WeatherIcon.Showers,
            WeatherIcon.Drizzle,
            WeatherIcon.Sleet,
            WeatherIcon.Snow,
            WeatherIcon.Hail,
            WeatherIcon.Thunderstorm,
            WeatherIcon.ThunderstormWithRain,
            WeatherIcon.HeavyThunderstorm,
            WeatherIcon.HeavyThunderstormWithRain -> Offset(0f, -14f)
            WeatherIcon.Cloudy,
            WeatherIcon.Wind,
            WeatherIcon.Storm,
            WeatherIcon.Fog -> Offset(0f, -5f)
            WeatherIcon.MostlyCloudy -> Offset(-5f, 0f)
            WeatherIcon.PartlyCloudy -> Offset(-3f, 4f)
            WeatherIcon.BrokenClouds -> Offset(-5f, 7f)
            else -> Offset.Zero
        }
    }
    val color by transition.animateColor(label = "color") {
        when (it) {
            WeatherIcon.Thunderstorm,
            WeatherIcon.ThunderstormWithRain,
            WeatherIcon.HeavyThunderstorm,
            WeatherIcon.HeavyThunderstormWithRain,
            WeatherIcon.Storm -> MaterialTheme.colors.weatherCloudDark2
            WeatherIcon.Showers,
            WeatherIcon.Sleet,
            WeatherIcon.Hail,
            WeatherIcon.Cloudy,
            WeatherIcon.Wind,
            WeatherIcon.Fog -> MaterialTheme.colors.weatherCloudDark1
            WeatherIcon.Drizzle,
            WeatherIcon.Snow -> MaterialTheme.colors.weatherCloudMedium2
            WeatherIcon.MostlyCloudy -> MaterialTheme.colors.weatherCloudLight2
            WeatherIcon.PartlyCloudy,
            WeatherIcon.BrokenClouds -> MaterialTheme.colors.weatherCloudLight1
            else -> MaterialTheme.colors.weatherCloudMedium2
        }
    }

    Icon(
        Icons.Rounded.WeatherCloud,
        null,
        modifier = Modifier
            .size(32.dp)
            .offset(offset.x.dp, offset.y.dp)
            .scale(scale),
        tint = color
    )
}

@Composable
private fun Cloud2(icon: WeatherIcon) {
    val transition = updateTransition(targetState = icon, "AnimatedWeatherIconCloud2")

    val scale by transition.animateFloat(label = "scale") {
        when (it) {
            WeatherIcon.Thunderstorm,
            WeatherIcon.ThunderstormWithRain,
            WeatherIcon.HeavyThunderstorm,
            WeatherIcon.HeavyThunderstormWithRain,
            WeatherIcon.MostlyCloudy,
            WeatherIcon.Showers,
            WeatherIcon.Drizzle,
            WeatherIcon.Sleet,
            WeatherIcon.Snow,
            WeatherIcon.Hail,
            WeatherIcon.Cloudy,
            WeatherIcon.Wind,
            WeatherIcon.Storm,
            WeatherIcon.Fog -> 1.1f
            WeatherIcon.BrokenClouds -> 0.7f
            else -> 0f
        }
    }
    val offset by transition.animateOffset(label = "offset") {
        when (it) {
            WeatherIcon.Showers,
            WeatherIcon.Drizzle,
            WeatherIcon.Sleet,
            WeatherIcon.Snow,
            WeatherIcon.Hail,
            WeatherIcon.Thunderstorm,
            WeatherIcon.ThunderstormWithRain,
            WeatherIcon.HeavyThunderstorm,
            WeatherIcon.HeavyThunderstormWithRain -> Offset(-6f, -6f)
            WeatherIcon.Cloudy,
            WeatherIcon.Wind,
            WeatherIcon.Storm,
            WeatherIcon.Fog -> Offset(-6f, 3f)
            WeatherIcon.BrokenClouds -> Offset(9f, -9f)
            WeatherIcon.MostlyCloudy -> Offset(4f, 5f)
            else -> Offset.Zero
        }
    }
    val color by transition.animateColor(label = "color") {
        when (it) {
            WeatherIcon.BrokenClouds -> MaterialTheme.colors.weatherCloudLight2
            WeatherIcon.Thunderstorm,
            WeatherIcon.ThunderstormWithRain,
            WeatherIcon.HeavyThunderstorm,
            WeatherIcon.HeavyThunderstormWithRain,
            WeatherIcon.Storm -> MaterialTheme.colors.weatherCloudMedium2
            WeatherIcon.Showers,
            WeatherIcon.Drizzle,
            WeatherIcon.Sleet,
            WeatherIcon.Snow,
            WeatherIcon.Hail,
            WeatherIcon.Cloudy,
            WeatherIcon.Wind,
            WeatherIcon.Fog -> MaterialTheme.colors.weatherCloudMedium1
            WeatherIcon.MostlyCloudy -> MaterialTheme.colors.weatherCloudMedium1
            else -> MaterialTheme.colors.weatherCloudMedium2
        }
    }

    Icon(
        Icons.Rounded.WeatherCloud,
        null,
        modifier = Modifier
            .size(32.dp)
            .offset(offset.x.dp, offset.y.dp)
            .scale(scale),
        tint = color
    )
}

@Composable
private fun Cloud3(icon: WeatherIcon) {
    val transition = updateTransition(targetState = icon, "AnimatedWeatherIconCloud3")

    val scale by transition.animateFloat(label = "scale") {
        when (it) {
            WeatherIcon.Showers,
            WeatherIcon.Drizzle,
            WeatherIcon.Sleet,
            WeatherIcon.Snow,
            WeatherIcon.Hail,
            WeatherIcon.Thunderstorm,
            WeatherIcon.ThunderstormWithRain,
            WeatherIcon.HeavyThunderstorm,
            WeatherIcon.HeavyThunderstormWithRain,
            WeatherIcon.Cloudy,
            WeatherIcon.Wind,
            WeatherIcon.Storm -> 1f
            else -> 0f
        }
    }
    val offset by transition.animateOffset(label = "offset") {
        when (it) {
            WeatherIcon.Showers,
            WeatherIcon.Drizzle,
            WeatherIcon.Sleet,
            WeatherIcon.Snow,
            WeatherIcon.Hail,
            WeatherIcon.Thunderstorm,
            WeatherIcon.ThunderstormWithRain,
            WeatherIcon.HeavyThunderstorm,
            WeatherIcon.HeavyThunderstormWithRain -> Offset(6f, -4f)
            WeatherIcon.Cloudy,
            WeatherIcon.Wind,
            WeatherIcon.Storm -> Offset(6f, 5f)
            else -> Offset.Zero
        }
    }
    val color by transition.animateColor(label = "color") {
        when (it) {
            WeatherIcon.Thunderstorm,
            WeatherIcon.ThunderstormWithRain,
            WeatherIcon.HeavyThunderstorm,
            WeatherIcon.HeavyThunderstormWithRain,
            WeatherIcon.Storm -> MaterialTheme.colors.weatherCloudDark1
            WeatherIcon.Showers,
            WeatherIcon.Sleet,
            WeatherIcon.Hail,
            WeatherIcon.Cloudy,
            WeatherIcon.Wind -> MaterialTheme.colors.weatherCloudMedium2
            WeatherIcon.Drizzle,
            WeatherIcon.Snow -> MaterialTheme.colors.weatherCloudLight2
            else -> MaterialTheme.colors.weatherCloudMedium2
        }
    }

    Icon(
        Icons.Rounded.WeatherCloud,
        null,
        modifier = Modifier
            .size(32.dp)
            .offset(offset.x.dp, offset.y.dp)
            .scale(scale),
        tint = color
    )
}

@Composable
private fun Hot(icon: WeatherIcon) {
    val scale by animateFloatAsState(
        if (icon == WeatherIcon.Hot) {
            1f
        } else {
            0f
        }
    )
    Icon(
        Icons.Rounded.Thermostat,
        null,
        modifier = Modifier
            .scale(scale)
            .size(32.dp),
        tint = MaterialTheme.colors.weatherHot
    )
}

@Composable
private fun Cold(icon: WeatherIcon) {
    val scale by animateFloatAsState(
        if (icon == WeatherIcon.Cold) {
            1f
        } else {
            0f
        }
    )
    Icon(
        Icons.Rounded.AcUnit,
        null,
        modifier = Modifier
            .scale(scale)
            .size(32.dp),
        tint = MaterialTheme.colors.weatherCold
    )
}

@Composable
private fun Wind1(icon: WeatherIcon) {
    val scale by animateFloatAsState(
        if (icon == WeatherIcon.Wind || icon == WeatherIcon.Storm) {
            0.6f
        } else {
            0f
        }
    )
    Icon(
        Icons.Rounded.Air,
        null,
        modifier = Modifier
            .scale(scale)
            .offset(12.dp, 11.dp)
            .size(32.dp),
        tint = MaterialTheme.colors.weatherWind
    )
}

@Composable
private fun Wind2(icon: WeatherIcon) {
    val scale by animateFloatAsState(
        if (icon == WeatherIcon.Storm) {
            0.6f
        } else {
            0f
        }
    )
    Icon(
        Icons.Rounded.Air,
        null,
        modifier = Modifier
            .scale(scale)
            .offset(8.dp, -1.dp)
            .size(32.dp),
        tint = MaterialTheme.colors.weatherWindDark
    )
}

@Preview
@Composable
fun AnimatedWeatherIconTestPanel() {
    var icon by remember { mutableStateOf(WeatherIcon.MostlyCloudy) }
    var night by remember { mutableStateOf(false) }
    Row {
        val icons = WeatherIcon.values()
        Column(
            modifier = Modifier.weight(1f)
        ) {
            AnimatedWeatherIcon(icon = icon, night = night)
            WeatherIcon(icon = icon, night = night)
        }

        Column {
            Box {
                var menu by remember { mutableStateOf(false) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    for (ic in icons) {
                        DropdownMenuItem(onClick = {
                            icon = ic
                            menu = false
                        }) {
                            Text(text = ic.name)
                        }
                    }
                }
                Button(onClick = { menu = true }) {
                    Text(text = icon.name)
                }
            }
            Button(onClick = { night = !night }) {
                Text(text = if (night) "Night" else "Day")
            }
        }

    }

}

@Composable
private fun Precipitation(icon: WeatherIcon) {

    Box(
        modifier = Modifier
            .size(32.dp)
            .clipToBounds()
            .rotate(10f)
    ) {
        Crossfade(icon) {
            when (it) {
                WeatherIcon.Drizzle -> {
                    val anim = rememberInfiniteTransition()
                    val animProgress by anim.animateFloat(
                        initialValue = 0f, targetValue = 1f, animationSpec = InfiniteRepeatableSpec(
                            tween(300, easing = LinearEasing)
                        )
                    )
                    Icon(
                        modifier = Modifier
                            .size(32.dp)
                            .offset(y = 8.dp + 11.dp * animProgress),
                        imageVector = Icons.Rounded.WeatherLightRainAnimatable,
                        contentDescription = null,
                        tint = MaterialTheme.colors.weatherRain
                    )
                }
                WeatherIcon.Hail -> {
                    val anim = rememberInfiniteTransition()
                    val animProgress by anim.animateFloat(
                        initialValue = 0f, targetValue = 1f, animationSpec = InfiniteRepeatableSpec(
                            tween(300, easing = LinearEasing)
                        )
                    )
                    Icon(
                        modifier = Modifier
                            .size(32.dp)
                            .offset(y = 8.dp + 11.dp * animProgress),
                        imageVector = Icons.Rounded.WeatherHailAnimatable,
                        contentDescription = null,
                        tint = MaterialTheme.colors.weatherHail
                    )
                }
                WeatherIcon.Snow -> {
                    val anim = rememberInfiniteTransition()
                    val animProgress by anim.animateFloat(
                        initialValue = 0f, targetValue = 1f, animationSpec = InfiniteRepeatableSpec(
                            tween(1000, easing = LinearEasing)
                        )
                    )
                    Icon(
                        modifier = Modifier
                            .size(32.dp)
                            .offset(
                                x = sin(animProgress * 2 * PI).dp,
                                y = 8.dp + 11.dp * animProgress
                            ),
                        imageVector = Icons.Rounded.WeatherHailAnimatable,
                        contentDescription = null,
                        tint = MaterialTheme.colors.weatherSnow
                    )
                }
                WeatherIcon.Showers,
                WeatherIcon.ThunderstormWithRain,
                WeatherIcon.HeavyThunderstormWithRain -> {
                    val anim = rememberInfiniteTransition()
                    val animProgress by anim.animateFloat(
                        initialValue = 0f, targetValue = 1f, animationSpec = InfiniteRepeatableSpec(
                            tween(300, easing = LinearEasing)
                        )
                    )
                    Icon(
                        modifier = Modifier
                            .size(32.dp)
                            .offset(y = 8.dp + 11.dp * animProgress),
                        imageVector = Icons.Rounded.WeatherRainAnimatable,
                        contentDescription = null,
                        tint = MaterialTheme.colors.weatherRain
                    )
                }
                WeatherIcon.Sleet -> {
                    val anim = rememberInfiniteTransition()
                    val animProgress by anim.animateFloat(
                        initialValue = 0f, targetValue = 1f, animationSpec = InfiniteRepeatableSpec(
                            tween(300, easing = LinearEasing)
                        )
                    )
                    Icon(
                        modifier = Modifier
                            .size(32.dp)
                            .offset(y = 8.dp + 11.dp * animProgress),
                        imageVector = Icons.Rounded.WeatherSleetRainAnimatable,
                        contentDescription = null,
                        tint = MaterialTheme.colors.weatherRain
                    )
                    Icon(
                        modifier = Modifier
                            .size(32.dp)
                            .offset(y = 8.dp + 11.dp * animProgress),
                        imageVector = Icons.Rounded.WeatherSleetSnowAnimatable,
                        contentDescription = null,
                        tint = MaterialTheme.colors.weatherSnow
                    )
                }
            }
        }
    }
}

@Composable
private fun Fog(icon: WeatherIcon) {
    val scale by animateFloatAsState(if (icon == WeatherIcon.Fog || icon == WeatherIcon.Haze) 1f else 0f)

    Icon(
        modifier = Modifier
            .size(32.dp)
            .scale(scale)
            .offset(x = 6.dp, y = 7.dp),
        imageVector = Icons.Rounded.WeatherFog,
        contentDescription = null,
        tint = MaterialTheme.colors.weatherFog
    )
}

enum class WeatherIcon {
    None,
    Clear,
    Cloudy,
    Cold,
    Drizzle,
    Haze,
    Fog,
    Hail,
    HeavyThunderstorm,
    HeavyThunderstormWithRain,
    Hot,
    MostlyCloudy,
    PartlyCloudy,
    Showers,
    Sleet,
    Snow,
    Storm,
    Thunderstorm,
    ThunderstormWithRain,
    Wind,
    BrokenClouds
}