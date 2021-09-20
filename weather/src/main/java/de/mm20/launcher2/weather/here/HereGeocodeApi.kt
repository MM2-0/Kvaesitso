package de.mm20.launcher2.weather.here

import retrofit2.http.GET
import retrofit2.http.Query

data class HereGeocodeResult(
    val Response: HereGeocodeResultResponse
)

data class HereGeocodeResultResponse(
    val View: Array<HereGeocodeResultResponseView>?
)

data class HereGeocodeResultResponseView(
    val Result: Array<HereGeocodeResultResponseViewResult>?
)

data class HereGeocodeResultResponseViewResult(
    val Location: HereGeocodeResultResponseViewResultLocation?
)

data class HereGeocodeResultResponseViewResultLocation(
    val LocationId: String?,
    val LocationType: String?,
    val DisplayPosition: HereGeocodeResultResponseViewResultLocationPosition?,
    val Address: HereGeocodeResultResponseViewResultLocationAddress?
)

data class HereGeocodeResultResponseViewResultLocationPosition(
    val Latitude: Double?,
    val Longitude: Double?
)

data class HereGeocodeResultResponseViewResultLocationAddress(
    val Label: String?,
    val Country: String?,
    val State: String?,
    val County: String?,
    val City: String?,
    val District: String?,
    val Street: String?,
    val HouseNumber: String?,
    val PostalCode: String?,
)

interface HereGeocodeApi {
    @GET("geocode.json")
    suspend fun geocode(
        @Query("apiKey") apiKey: String,
        @Query("searchtext") searchtext: String
    ): HereGeocodeResult
}