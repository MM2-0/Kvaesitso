package de.mm20.launcher2.openstreetmaps

import okhttp3.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface OverpassApi {
    @POST("api/interpreter")
    suspend fun search(@Body query: String): Response
}