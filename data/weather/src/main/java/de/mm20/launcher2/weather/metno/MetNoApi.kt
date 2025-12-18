package de.mm20.launcher2.weather.metno

import de.mm20.launcher2.serialization.Json
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
internal data class LocationForecast(
    val properties: Properties,
) {
    @Serializable
    internal data class Properties(
        val meta: Meta,
        val timeseries: List<Timeseries>,
    ) {

        @Serializable
        internal data class Meta(
            @SerialName("updated_at") val updatedAt: String,
        )

        @Serializable
        internal data class Timeseries(
            val time: String,
            val data: Data,
        ) {
            @Serializable
            internal data class Data(
                val instant: Instant,
                @SerialName("next_1_hours") val next1Hours: NextNHours?,
                @SerialName("next_6_hours") val next6Hours: NextNHours?,
                @SerialName("next_12_hours") val next12Hours: NextNHours?,
            ) {
                @Serializable
                internal data class Instant(
                    val details: Details
                ) {
                    @Serializable
                    internal data class Details(
                        @SerialName("air_temperature") val airTemperature: Double,
                        @SerialName("cloud_area_fraction") val cloudAreaFraction: Double,
                        @SerialName("relative_humidity") val relativeHumidity: Double,
                        @SerialName("wind_from_direction") val windFromDirection: Double,
                        @SerialName("wind_speed") val windSpeed: Double,
                        @SerialName("air_pressure_at_sea_level") val airPressureAtSeaLevel: Double,
                    )
                }

                @Serializable
                internal data class NextNHours(
                    val summary: Summary?,
                    val details: Details?,
                ) {
                    @Serializable
                    internal data class Summary(
                        @SerialName("symbol_code") val symbolCode: String?
                    )

                    @Serializable
                    internal data class Details(
                        @SerialName("precipitation_amount") val precipitationAmount: Double?
                    )
                }
            }
        }
    }
}


internal class MetNoApi {

    private val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json.Lenient)
            }
            defaultRequest {
                url("https://api.met.no")
            }
        }
    }

    suspend fun locationForecast(
        lat: Double,
        lon: Double,
        userAgent: String,
        ifModifiedSince: Long,
    ): LocationForecast {
        val httpDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ROOT)

        return httpClient.get {
            url {
                path("weatherapi", "locationforecast", "2.0", "compact")
                parameter("lat", String.format(Locale.ROOT, "%.4f", lat))
                parameter("lon", String.format(Locale.ROOT, "%.4f", lon))
            }
            header("User-Agent", userAgent)
            header("If-Modified-Since", httpDateFormat.format(Date(ifModifiedSince)))
        }.body()
    }
}