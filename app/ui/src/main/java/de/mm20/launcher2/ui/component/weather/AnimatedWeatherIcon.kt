package de.mm20.launcher2.ui.component.weather

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffset
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import kotlin.math.PI
import kotlin.math.sin


@Composable
fun AnimatedWeatherIcon(
    modifier: Modifier = Modifier,
    icon: WeatherIcon,
    night: Boolean = false,
    colors: WeatherIconColors = WeatherIconDefaults.colors(),
) {

    Box(
        modifier = modifier
            .size(64.dp)
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        SunMoon(icon, night, colors)
        Precipitation(icon, colors)
        LightningBolt(icon, colors)
        Cloud1(icon, colors)
        Cloud2(icon, colors)
        Cloud3(icon, colors)
        Fog(icon, colors)
        Hot(icon, colors)
        Cold(icon, colors)
        Wind1(icon, colors)
    }
}

@Composable
private fun SunMoon(icon: WeatherIcon, night: Boolean, colors: WeatherIconColors) {
    val sunMoonIcon = AnimatedImageVector.animatedVectorResource(R.drawable.anim_weather_sun_moon)
    val transition = updateTransition(targetState = icon, "AnimatedWeatherIcon")

    val color by animateColorAsState(if (night) colors.moon else colors.sun)
    val scale by transition.animateFloat(label = "sunScale") {
        when (it) {
            WeatherIcon.Clear,
            WeatherIcon.PartlyCloudy,
            WeatherIcon.Haze -> 1f

            else -> 0f
        }
    }
    val offset by transition.animateOffset(label = "sunOffset") {
        when (it) {
            WeatherIcon.Clear,
            WeatherIcon.Haze -> Offset.Zero

            WeatherIcon.PartlyCloudy -> Offset(3f, -2f)
            else -> Offset.Zero
        }
    }

    Icon(
        rememberAnimatedVectorPainter(sunMoonIcon, atEnd = night),
        null,
        modifier = Modifier
            .size(32.dp)
            .offset(offset.x.dp, offset.y.dp)
            .scale(scale),
        tint = color
    )
}

@Composable
private fun LightningBolt(icon: WeatherIcon, colors: WeatherIconColors) {
    val transition = updateTransition(targetState = icon, "AnimatedWeatherIconBolt")

    val scale by transition.animateFloat(label = "scale") {
        when (it) {
            WeatherIcon.Thunder,
            WeatherIcon.Thunderstorm -> 0.6f

            else -> 0f
        }
    }
    val offset by transition.animateOffset(label = "offset") {
        when (it) {
            WeatherIcon.Thunder,
            WeatherIcon.Thunderstorm -> Offset(1f, 8f)

            else -> Offset.Zero
        }
    }

    Icon(
        painter = painterResource(R.drawable.bolt_24px_filled),
        null,
        modifier = Modifier
            .size(32.dp)
            .offset(offset.x.dp, offset.y.dp)
            .scale(scale),
        tint = colors.lightningBolt
    )
}


@Composable
private fun Cloud1(icon: WeatherIcon, colors: WeatherIconColors) {
    val transition = updateTransition(targetState = icon, "AnimatedWeatherIconCloud1")

    val scale by transition.animateFloat(label = "scale") {
        when (it) {
            WeatherIcon.Clear -> 0f
            WeatherIcon.Thunder,
            WeatherIcon.Thunderstorm,
            WeatherIcon.LightRain,
            WeatherIcon.Rain,
            WeatherIcon.HeavyRain,
            WeatherIcon.Sleet,
            WeatherIcon.Snow,
            WeatherIcon.Hail,
            WeatherIcon.Wind,
            WeatherIcon.Overcast,
            WeatherIcon.Wind,
            WeatherIcon.Fog -> 1.4f

            WeatherIcon.PartlyCloudy -> 1f
            else -> 0f
        }
    }
    val offset by transition.animateOffset(label = "offset") {
        when (it) {
            WeatherIcon.LightRain,
            WeatherIcon.Rain,
            WeatherIcon.HeavyRain,
            WeatherIcon.Sleet,
            WeatherIcon.Snow,
            WeatherIcon.Hail,
            WeatherIcon.Thunder,
            WeatherIcon.Thunderstorm -> Offset(0f, -14f)

            WeatherIcon.Overcast,
            WeatherIcon.Wind,
            WeatherIcon.Fog -> Offset(0f, -5f)

            WeatherIcon.PartlyCloudy -> Offset(-3f, 4f)
            else -> Offset.Zero
        }
    }
    val color by transition.animateColor(label = "color") {
        when (it) {
            WeatherIcon.Thunderstorm,
            WeatherIcon.Thunder,
            WeatherIcon.Wind -> colors.cloudDark2

            WeatherIcon.Rain,
            WeatherIcon.HeavyRain,
            WeatherIcon.Sleet,
            WeatherIcon.Hail,
            WeatherIcon.Overcast,
            WeatherIcon.Wind,
            WeatherIcon.Fog -> colors.cloudDark1

            WeatherIcon.LightRain,
            WeatherIcon.Snow -> colors.cloudMedium2

            WeatherIcon.PartlyCloudy -> colors.cloudLight1
            else -> colors.cloudMedium2
        }
    }

    Icon(
        WeatherCloud,
        null,
        modifier = Modifier
            .size(32.dp)
            .offset(offset.x.dp, offset.y.dp)
            .scale(scale),
        tint = color
    )
}

@Composable
private fun Cloud2(icon: WeatherIcon, colors: WeatherIconColors) {
    val transition = updateTransition(targetState = icon, "AnimatedWeatherIconCloud2")

    val scale by transition.animateFloat(label = "scale") {
        when (it) {
            WeatherIcon.Thunder,
            WeatherIcon.Thunderstorm,
            WeatherIcon.Rain,
            WeatherIcon.HeavyRain,
            WeatherIcon.LightRain,
            WeatherIcon.Sleet,
            WeatherIcon.Snow,
            WeatherIcon.Hail,
            WeatherIcon.Overcast,
            WeatherIcon.Wind,
            WeatherIcon.Fog -> 1.1f

            else -> 0f
        }
    }
    val offset by transition.animateOffset(label = "offset") {
        when (it) {
            WeatherIcon.Rain,
            WeatherIcon.LightRain,
            WeatherIcon.HeavyRain,
            WeatherIcon.Sleet,
            WeatherIcon.Snow,
            WeatherIcon.Hail,
            WeatherIcon.Thunderstorm,
            WeatherIcon.Thunder -> Offset(-6f, -6f)

            WeatherIcon.Overcast,
            WeatherIcon.Wind,
            WeatherIcon.Fog -> Offset(-6f, 3f)

            else -> Offset.Zero
        }
    }
    val color by transition.animateColor(label = "color") {
        when (it) {
            WeatherIcon.Thunderstorm,
            WeatherIcon.Thunder,
            WeatherIcon.Wind -> colors.cloudMedium2

            WeatherIcon.Rain,
            WeatherIcon.HeavyRain,
            WeatherIcon.LightRain,
            WeatherIcon.Sleet,
            WeatherIcon.Snow,
            WeatherIcon.Hail,
            WeatherIcon.Overcast,
            WeatherIcon.Wind,
            WeatherIcon.Fog -> colors.cloudMedium1

            else -> colors.cloudMedium2
        }
    }

    Icon(
        WeatherCloud,
        null,
        modifier = Modifier
            .size(32.dp)
            .offset(offset.x.dp, offset.y.dp)
            .scale(scale),
        tint = color
    )
}

@Composable
private fun Cloud3(icon: WeatherIcon, colors: WeatherIconColors) {
    val transition = updateTransition(targetState = icon, "AnimatedWeatherIconCloud3")

    val scale by transition.animateFloat(label = "scale") {
        when (it) {
            WeatherIcon.Rain,
            WeatherIcon.LightRain,
            WeatherIcon.HeavyRain,
            WeatherIcon.Sleet,
            WeatherIcon.Snow,
            WeatherIcon.Hail,
            WeatherIcon.Thunderstorm,
            WeatherIcon.Thunder,
            WeatherIcon.Overcast,
            WeatherIcon.Wind -> 1f

            else -> 0f
        }
    }
    val offset by transition.animateOffset(label = "offset") {
        when (it) {
            WeatherIcon.LightRain,
            WeatherIcon.HeavyRain,
            WeatherIcon.Rain,
            WeatherIcon.Sleet,
            WeatherIcon.Snow,
            WeatherIcon.Hail,
            WeatherIcon.Thunderstorm,
            WeatherIcon.Thunder -> Offset(6f, -4f)

            WeatherIcon.Overcast,
            WeatherIcon.Wind -> Offset(6f, 5f)

            else -> Offset.Zero
        }
    }
    val color by transition.animateColor(label = "color") {
        when (it) {
            WeatherIcon.Thunderstorm,
            WeatherIcon.Thunder,
            WeatherIcon.Wind -> colors.cloudDark1

            WeatherIcon.Rain,
            WeatherIcon.HeavyRain,
            WeatherIcon.Sleet,
            WeatherIcon.Hail,
            WeatherIcon.Overcast,
            WeatherIcon.Wind -> colors.cloudMedium2

            WeatherIcon.LightRain,
            WeatherIcon.Snow -> colors.cloudLight2

            else -> colors.cloudMedium2
        }
    }

    Icon(
        WeatherCloud,
        null,
        modifier = Modifier
            .size(32.dp)
            .offset(offset.x.dp, offset.y.dp)
            .scale(scale),
        tint = color
    )
}

@Composable
private fun Hot(icon: WeatherIcon, colors: WeatherIconColors) {
    val scale by animateFloatAsState(
        if (icon == WeatherIcon.ExtremeHeat) {
            1f
        } else {
            0f
        }
    )
    Icon(
        painterResource(R.drawable.heat_24px),
        null,
        modifier = Modifier
            .scale(scale)
            .size(32.dp),
        tint = colors.hot
    )
}

@Composable
private fun Cold(icon: WeatherIcon, colors: WeatherIconColors) {
    val scale by animateFloatAsState(
        if (icon == WeatherIcon.ExtremeCold) {
            1f
        } else {
            0f
        }
    )
    Icon(
        painterResource(R.drawable.severe_cold_24px),
        null,
        modifier = Modifier
            .scale(scale)
            .size(32.dp),
        tint = colors.cold
    )
}

@Composable
private fun Wind1(icon: WeatherIcon, colors: WeatherIconColors) {
    val scale by animateFloatAsState(
        if (icon == WeatherIcon.Wind) {
            0.6f
        } else {
            0f
        }
    )
    Icon(
        painterResource(R.drawable.air_20px),
        null,
        modifier = Modifier
            .scale(scale)
            .offset(12.dp, 11.dp)
            .size(32.dp),
        tint = colors.wind
    )
}


@Preview
@Composable
fun AnimatedWeatherIconTestPanel() {
    var icon by remember { mutableStateOf(WeatherIcon.Thunderstorm) }
    var night by remember { mutableStateOf(false) }
    Row {
        val icons = WeatherIcon.entries
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedWeatherIcon(icon = icon, night = night)
            WeatherIcon(icon = icon, night = night)

            val monochromeColors = WeatherIconDefaults.monochromeColors(
                MaterialTheme.colorScheme.secondary
            )

            AnimatedWeatherIcon(
                modifier = Modifier.background(MaterialTheme.colorScheme.secondary),
                icon = icon,
                night = night,
                colors = monochromeColors
            )

            WeatherIcon(
                modifier = Modifier.background(MaterialTheme.colorScheme.secondary),
                icon = icon,
                night = night,
                colors = monochromeColors
            )
        }

        Column {
            Box {
                var menu by remember { mutableStateOf(false) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    for (ic in icons) {
                        DropdownMenuItem(
                            onClick = {
                                icon = ic
                                menu = false
                            },
                            text = {
                                Text(text = ic.name)
                            }
                        )
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
private fun Precipitation(icon: WeatherIcon, colors: WeatherIconColors) {

    Box(
        modifier = Modifier
            .size(32.dp)
            .clipToBounds()
            .rotate(10f)
    ) {
        Crossfade(icon) {
            when (it) {
                WeatherIcon.LightRain, WeatherIcon.Rain -> {
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
                        imageVector = WeatherLightRainAnimatable,
                        contentDescription = null,
                        tint = colors.rain
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
                        imageVector = WeatherHailAnimatable,
                        contentDescription = null,
                        tint = colors.hail,
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
                        imageVector = WeatherHailAnimatable,
                        contentDescription = null,
                        tint = colors.snow,
                    )
                }

                WeatherIcon.HeavyRain,
                WeatherIcon.Thunderstorm -> {
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
                        imageVector = WeatherRainAnimatable,
                        contentDescription = null,
                        tint = colors.rain,
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
                        imageVector = WeatherSleetRainAnimatable,
                        contentDescription = null,
                        tint = colors.rain,
                    )
                    Icon(
                        modifier = Modifier
                            .size(32.dp)
                            .offset(y = 8.dp + 11.dp * animProgress),
                        imageVector = WeatherSleetSnowAnimatable,
                        contentDescription = null,
                        tint = colors.snow,
                    )
                }

                else -> {}
            }
        }
    }
}

@Composable
private fun Fog(icon: WeatherIcon, colors: WeatherIconColors) {
    val scale by animateFloatAsState(if (icon == WeatherIcon.Fog || icon == WeatherIcon.Haze) 1f else 0f)

    Icon(
        modifier = Modifier
            .size(32.dp)
            .scale(scale)
            .offset(x = 6.dp, y = 7.dp),
        imageVector = WeatherFog,
        contentDescription = null,
        tint = colors.fog
    )
}

enum class WeatherIcon {
    Unknown,

    Clear,
    PartlyCloudy,
    Overcast,

    LightRain,
    Rain,
    HeavyRain,

    /**
     * Thunder and lightning without rain
     */
    Thunder,

    /**
     * Thunder and lightning with rain
     */
    Thunderstorm,

    Snow,
    Sleet,
    Hail,

    ExtremeHeat,
    ExtremeCold,

    Wind,

    Fog,
    Haze,

}