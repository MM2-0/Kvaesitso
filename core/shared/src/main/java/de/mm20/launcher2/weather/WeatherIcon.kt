package de.mm20.launcher2.weather

enum class WeatherIcon(val id: Int) {
    Unknown(-1),
    Clear(0),
    Overcast(1),
    ExtremeCold(2),
    Haze(4),
    Fog(5),
    Hail(6),
    ExtremeHeat(9),
    PartlyCloudy(11),
    Sleet(13),
    Snow(14),
    Thunder(16),
    Thunderstorm(17),
    Wind(18),
    Rain(12),
    LightRain(3),
    HeavyRain(20),

    @Deprecated("Deprecated", ReplaceWith("WeatherIcon.PartlyCloudy"))
    BrokenClouds(19),
    @Deprecated("Deprecated", ReplaceWith("WeatherIcon.Thunder"))
    HeavyThunderstorm(7),
    @Deprecated("Deprecated", ReplaceWith("WeatherIcon.Thunderstorm"))
    HeavyThunderstormWithRain(8),
    @Deprecated("Deprecated", ReplaceWith("WeatherIcon.Thunderstorm"))
    ThunderstormWithRain(17),
    @Deprecated("Deprecated", ReplaceWith("WeatherIcon.Rain"))
    Showers(12),
    @Deprecated("Deprecated", ReplaceWith("WeatherIcon.LightRain"))
    Drizzle(3),
    @Deprecated("Deprecated", ReplaceWith("WeatherIcon.PartlyCloudy"))
    MostlyCloudy(10),
    @Deprecated("Deprecated", ReplaceWith("WeatherIcon.Overcast"))
    Cloudy(1),
    @Deprecated("Deprecated", ReplaceWith("WeatherIcon.ExtremeCold"))
    Cold(2),
    @Deprecated("Deprecated", ReplaceWith("WeatherIcon.ExtremeHeat"))
    Hot(9),
    @Deprecated("Deprecated", ReplaceWith("WeatherIcon.Wind"))
    Storm(15),
}