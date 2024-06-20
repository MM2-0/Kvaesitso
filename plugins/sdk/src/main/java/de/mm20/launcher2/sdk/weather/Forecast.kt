package de.mm20.launcher2.sdk.weather

/**
 * Temperature value.
 * Use [Double].[C], [Double].[F] or [Double].[K] to create a temperature value:
 * ```kt
 * val temp = 20.0.C
 * val temp2 = 68.0.F
 * val temp3 = 293.15.K
 * ```
 */
@JvmInline
value class Temperature internal constructor(internal val kelvin: Double)

/**
 * Temperature in degrees Celsius
 */
val Double.C
    get() = Temperature(this + 273.15)

/**
 * Temperature in degrees Fahrenheit
 */
val Double.F
    get() = Temperature((this - 32.0) * (5.0 / 9.0) + 273.15)

/**
 * Temperature in Kelvin
 */
val Double.K
    get() = Temperature(this)

/**
 * Wind speed value.
 * Use [Double].[m_s], [Double].[km_h] or [Double].[mph] to create a wind speed value:
 * ```kt
 * val wind = 5.0.m_s
 * val wind2 = 18.0.km_h
 * val wind3 = 11.1847.mph
 * ```
 */
@JvmInline
value class WindSpeed internal constructor(internal val metersPerSecond: Double)

/**
 * Wind speed in meters per second
 */
val Double.m_s
    get() = WindSpeed(this)

/**
 * Wind speed in kilometers per hour
 */
val Double.km_h
    get() = WindSpeed(this * 0.277778)

/**
 * Wind speed in miles per hour
 */
val Double.mph
    get() = WindSpeed(this * 0.44704)

/**
 * Pressure value.
 * Use [Double].[hPa] or [Double].[mbar] to create a pressure value:
 * ```kt
 * val pressure = 1013.25.hPa
 * val pressure2 = 1013.25.mbar
 * ```
 */
@JvmInline
value class Pressure internal constructor(internal val hPa: Double)

/**
 * Pressure in hectopascal
 */
val Double.hPa
    get() = Pressure(this)

/**
 * Pressure in millibar
 */
val Double.mbar
    get() = Pressure(this)

@JvmInline
value class Precipitation internal constructor(internal val mm: Double)

/**
 * Precipitation in millimeters
 */
val Double.mm
    get() = Precipitation(this)

/**
 * Precipitation in inches
 */

val Double.inch
    get() = Precipitation(this * 25.4)

@Deprecated("Use de.mm20.launcher2.weather.WeatherIcon")
typealias WeatherIcon = de.mm20.launcher2.weather.WeatherIcon

data class Forecast(
    /**
     * Unix timestamp of the time that this forecast is valid for, in milliseconds
     */
    val timestamp: Long,
    /**
     * Unix timestamp of the time that this forecast was created, in milliseconds
     */
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * The temperature
     * @see [Double].[C]
     * @see [Double].[F]
     * @see [Double].[K]
     */
    val temperature: Temperature,
    /**
     * The weather condition
     */
    val condition: String,
    /**
     * The weather icon
     */
    val icon: WeatherIcon,
    /**
     * If true, weather icons will use the moon icon instead of the sun icon
     */
    val night: Boolean = false,

    /**
     * The minimum temperature
     * @see [Double].[C]
     * @see [Double].[F]
     * @see [Double].[K]
     */
    val minTemp: Temperature? = null,
    /**
     * The maximum temperature
     * @see [Double].[C]
     * @see [Double].[F]
     * @see [Double].[K]
     */
    val maxTemp: Temperature? = null,
    /**
     * Air pressure
     * @see [Double].[hPa]
     * @see [Double].[mbar]
     */
    val pressure: Pressure? = null,
    /**
     * Air humidity in percent
     */
    val humidity: Int? = null,
    /**
     * Wind speed
     * @see [Double].[m_s]
     * @see [Double].[km_h]
     * @see [Double].[mph]
     */
    val windSpeed: WindSpeed? = null,
    /**
     * Wind direction in degrees
     */
    val windDirection: Double? = null,
    /**
     * Precipitation
     * @see [Double].[mm]
     * @see [Double].[inch]
     */
    val precipitation: Precipitation? = null,
    /**
     * Rain probability in percent
     */
    val rainProbability: Int? = null,
    /**
     * Clouds in percent
     */
    val clouds: Int? = null,
    /**
     * Location name
     */
    val location: String,
    /**
     * Provider name
     */
    val provider: String,
    /**
     * Url to the provider and more weather information
     */
    val providerUrl: String? = null,
)