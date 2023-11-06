package de.mm20.launcher2.openstreetmaps

import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import java.lang.reflect.Type

data class OverpassQuery(
    val name: String,
    val radius: Int,
    val latitude: Double,
    val longitude: Double,
    val caseInvariant: Boolean = true,
)

data class OverpassResponse(
    val elements: List<OverpassResponseElement>,
)

data class OverpassResponseElement(
    val type: String,
    val id: Long,
    val lat: Double,
    val lon: Double,
    val tags: Map<String, String>,
)

interface OverpassApi {
    @POST("api/interpreter")
    suspend fun search(@Body data: OverpassQuery): OverpassResponse
}

class OverpassQueryConverter : Converter<OverpassQuery, RequestBody> {
    override fun convert(value: OverpassQuery): RequestBody = """
        [out:json];
        node(around:${value.radius},${value.latitude},${value.longitude})
        ["name"~"${value.name}"${if(value.caseInvariant){ ",i" } else { "" }}];
        out;
    """.trimIndent().toRequestBody()
}

class OverpassQueryConverterFactory : Converter.Factory() {
    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        if (type != OverpassQuery::class.java)
            return null

        return OverpassQueryConverter()
    }
}

