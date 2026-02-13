package de.mm20.launcher2.ui.component.weather

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R

@Composable
fun WeatherIcon(
    icon: WeatherIcon,
    modifier: Modifier = Modifier,
    night: Boolean = false,
    colors: WeatherIconColors = WeatherIconDefaults.colors(),
) {
    Box(
        modifier = modifier
            .size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        SunMoon(icon, night, colors)
        Precipitation(icon, colors)
        LightningBolts(icon, colors)
        Cloud1(icon, colors)
        Cloud2(icon, colors)
        Cloud3(icon, colors)
        HotCold(icon, colors)
        Wind(icon, colors)
        Fog(icon, colors)
    }
}

@Composable
private fun SunMoon(icon: WeatherIcon, night: Boolean, colors: WeatherIconColors) {
    when (icon) {
        WeatherIcon.Clear,
        WeatherIcon.PartlyCloudy,
        WeatherIcon.Haze, -> {}

        else -> return
    }

    val color = if (night) colors.moon else colors.sun

    val scale = when (icon) {
        WeatherIcon.Clear,
        WeatherIcon.PartlyCloudy,
        WeatherIcon.Haze -> 1f
    }

    val offset = when (icon) {
        WeatherIcon.PartlyCloudy -> DpOffset(2.dp, -2.dp)
        else -> DpOffset.Zero
    }

    Icon(
        painterResource(if (night) R.drawable.dark_mode_24px_filled else R.drawable.light_mode_24px_filled),
        null,
        modifier = Modifier
            .size(24.dp)
            .offset(offset.x, offset.y)
            .scale(scale),
        tint = color
    )
}

@Composable
private fun Cloud1(icon: WeatherIcon, colors: WeatherIconColors) {

    when (icon) {
        WeatherIcon.Thunderstorm,
        WeatherIcon.Thunder,
        WeatherIcon.Rain,
        WeatherIcon.LightRain,
        WeatherIcon.HeavyRain,
        WeatherIcon.Sleet,
        WeatherIcon.Snow,
        WeatherIcon.Hail,
        WeatherIcon.Overcast,
        WeatherIcon.Fog,
        WeatherIcon.PartlyCloudy -> {
        }

        else -> return
    }

    val scale = when (icon) {
        WeatherIcon.Thunderstorm,
        WeatherIcon.Thunder,
        WeatherIcon.Rain,
        WeatherIcon.LightRain,
        WeatherIcon.HeavyRain,
        WeatherIcon.Sleet,
        WeatherIcon.Snow,
        WeatherIcon.Hail,
        WeatherIcon.Overcast,
        WeatherIcon.Fog -> 1.4f

        WeatherIcon.PartlyCloudy -> 1f
    }
    val offset = when (icon) {
        WeatherIcon.HeavyRain,
        WeatherIcon.Rain,
        WeatherIcon.LightRain,
        WeatherIcon.Sleet,
        WeatherIcon.Snow,
        WeatherIcon.Hail,
        WeatherIcon.Thunderstorm,
        WeatherIcon.Thunder -> DpOffset(0.dp, -7.dp)

        WeatherIcon.Overcast,
        WeatherIcon.Fog -> DpOffset(0.dp, -2.5.dp)

        WeatherIcon.PartlyCloudy -> DpOffset(-1.5.dp, 2.dp)
    }
    val color = when (icon) {
        WeatherIcon.Thunderstorm,
        WeatherIcon.Thunder -> colors.cloudDark2

        WeatherIcon.Rain,
        WeatherIcon.HeavyRain,
        WeatherIcon.Sleet,
        WeatherIcon.Hail,
        WeatherIcon.Overcast,
        WeatherIcon.Fog -> colors.cloudDark1

        WeatherIcon.LightRain,
        WeatherIcon.Snow -> colors.cloudMedium2

        WeatherIcon.PartlyCloudy -> colors.cloudLight1
    }

    Icon(
        WeatherCloud,
        null,
        modifier = Modifier
            .size(20.dp)
            .offset(offset.x, offset.y)
            .scale(scale),
        tint = color
    )
}

@Composable
private fun Cloud2(icon: WeatherIcon, colors: WeatherIconColors) {

    when (icon) {
        WeatherIcon.Thunderstorm,
        WeatherIcon.Thunder,
        WeatherIcon.Rain,
        WeatherIcon.HeavyRain,
        WeatherIcon.LightRain,
        WeatherIcon.Sleet,
        WeatherIcon.Snow,
        WeatherIcon.Hail,
        WeatherIcon.Overcast,
        WeatherIcon.Fog -> {
        }

        else -> return
    }

    val scale = when (icon) {
        WeatherIcon.Thunderstorm,
        WeatherIcon.Thunder,
        WeatherIcon.Rain,
        WeatherIcon.LightRain,
        WeatherIcon.HeavyRain,
        WeatherIcon.Sleet,
        WeatherIcon.Snow,
        WeatherIcon.Hail,
        WeatherIcon.Overcast,
        WeatherIcon.Fog -> 1.1f
    }
    val offset = when (icon) {
        WeatherIcon.Rain,
        WeatherIcon.HeavyRain,
        WeatherIcon.LightRain,
        WeatherIcon.Sleet,
        WeatherIcon.Snow,
        WeatherIcon.Hail,
        WeatherIcon.Thunderstorm,
        WeatherIcon.Thunder -> DpOffset(-3.dp, -3.dp)

        WeatherIcon.Overcast,
        WeatherIcon.Fog -> DpOffset(-3.dp, 1.5.dp)
    }
    val color = when (icon) {
        WeatherIcon.Thunderstorm,
        WeatherIcon.Thunder -> colors.cloudMedium2

        WeatherIcon.Rain,
        WeatherIcon.HeavyRain,
        WeatherIcon.LightRain,
        WeatherIcon.Sleet,
        WeatherIcon.Snow,
        WeatherIcon.Hail,
        WeatherIcon.Overcast,
        WeatherIcon.Fog -> colors.cloudMedium1
    }

    Icon(
        WeatherCloud,
        null,
        modifier = Modifier
            .size(20.dp)
            .offset(offset.x, offset.y)
            .scale(scale),
        tint = color
    )
}

@Composable
private fun Cloud3(icon: WeatherIcon, colors: WeatherIconColors) {
    when (icon) {
        WeatherIcon.LightRain,
        WeatherIcon.HeavyRain,
        WeatherIcon.Rain,
        WeatherIcon.Sleet,
        WeatherIcon.Snow,
        WeatherIcon.Hail,
        WeatherIcon.Thunderstorm,
        WeatherIcon.Thunder,
        WeatherIcon.Overcast -> {}

        else -> return
    }
    val offset = when (icon) {
        WeatherIcon.LightRain,
        WeatherIcon.HeavyRain,
        WeatherIcon.Rain,
        WeatherIcon.Sleet,
        WeatherIcon.Snow,
        WeatherIcon.Hail,
        WeatherIcon.Thunderstorm,
        WeatherIcon.Thunder -> DpOffset(3.dp, -2.dp)
        WeatherIcon.Overcast -> DpOffset(3.dp, 2.5.dp)
    }
    val color = when (icon) {
        WeatherIcon.Thunderstorm,
        WeatherIcon.Thunder -> colors.cloudDark1

        WeatherIcon.Rain,
        WeatherIcon.HeavyRain,
        WeatherIcon.Sleet,
        WeatherIcon.Hail,
        WeatherIcon.Overcast -> colors.cloudMedium2

        WeatherIcon.LightRain,
        WeatherIcon.Snow -> colors.cloudLight2
    }

    Icon(
        WeatherCloud,
        null,
        modifier = Modifier
            .size(32.dp)
            .offset(offset.x, offset.y),
        tint = color
    )
}

@Composable
private fun Precipitation(icon: WeatherIcon, colors: WeatherIconColors) {
    if (icon == WeatherIcon.Sleet) {
        Icon(
            modifier = Modifier
                .offset(y = 2.dp)
                .size(32.dp),
            imageVector = WeatherSleetSnow,
            contentDescription = null,
            tint = colors.snow
        )
        Icon(
            modifier = Modifier
                .offset(y = 2.dp)
                .size(32.dp),
            imageVector = WeatherSleetRain,
            contentDescription = null,
            tint = colors.rain
        )
        return
    }

    when (icon) {
        WeatherIcon.Rain,
        WeatherIcon.LightRain,
        WeatherIcon.HeavyRain,
        WeatherIcon.Hail,
        WeatherIcon.Snow,
        WeatherIcon.Thunderstorm -> {
        }
        else -> return
    }
    val vector = when (icon) {
        WeatherIcon.LightRain, WeatherIcon.Rain -> WeatherLightRain
        WeatherIcon.HeavyRain,
        WeatherIcon.Thunderstorm -> WeatherRain
        WeatherIcon.Hail, WeatherIcon.Snow -> WeatherHail
    }
    val color = when (icon) {
        WeatherIcon.LightRain,
        WeatherIcon.Rain,
        WeatherIcon.HeavyRain,
        WeatherIcon.Thunderstorm -> colors.rain

        WeatherIcon.Hail -> colors.hail
        WeatherIcon.Snow -> colors.snow
        else -> Color.Unspecified
    }
    Icon(
        modifier = Modifier
            .offset(y = 2.dp)
            .size(32.dp),
        imageVector = vector,
        contentDescription = null,
        tint = color
    )
}

@Composable
private fun HotCold(icon: WeatherIcon, colors: WeatherIconColors) {
    if (icon == WeatherIcon.ExtremeHeat) {
        Icon(
            modifier = Modifier
                .size(32.dp),
            painter = painterResource(R.drawable.heat_24px),
            contentDescription = null,
            tint = colors.hot
        )
    }
    if (icon == WeatherIcon.ExtremeCold) {
        Icon(
            modifier = Modifier
                .size(32.dp),
            painter = painterResource(R.drawable.severe_cold_24px),
            contentDescription = null,
            tint = colors.cold
        )
    }
}

@Composable
private fun Wind(icon: WeatherIcon, colors: WeatherIconColors) {
    if (icon == WeatherIcon.Wind) {
        Icon(
            modifier = Modifier
                .size(32.dp),
            painter = painterResource(R.drawable.air_24px),
            contentDescription = null,
            tint = colors.wind
        )
    }
}

@Composable
private fun Fog(icon: WeatherIcon, colors: WeatherIconColors) {

    if (icon != WeatherIcon.Haze && icon != WeatherIcon.Fog) {
        return
    }

    Icon(
        modifier = Modifier
            .size(24.dp)
            .offset(x = 3.dp, y = 4.dp),
        imageVector = WeatherFog,
        contentDescription = null,
        tint = colors.fog
    )
}

@Composable
private fun LightningBolts(icon: WeatherIcon, colors: WeatherIconColors) {

    if (icon != WeatherIcon.Thunderstorm
        && icon != WeatherIcon.Thunder
    ) {
        return
    }

    Icon(
        modifier = Modifier
            .size(12.dp)
            .offset(x = 1.dp, y = 6.dp),
        painter = painterResource(R.drawable.bolt_24px_filled),
        contentDescription = null,
        tint = colors.lightningBolt
    )
}