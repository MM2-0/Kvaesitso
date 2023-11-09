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
    override fun convert(value: OverpassQuery): RequestBody {

        // allow other characters in between query words, if there are multiple
        // https://dev.overpass-api.de/overpass-doc/en/criteria/per_tag.html#regex
        val escapedQueryName = value
            .name
            .split(' ')
            .joinToString(
                separator = ".*",
                prefix = "\"",
                postfix = "\""
            ) { Regex.escapeReplacement(it) }

        val overpassQlBuilder = StringBuilder()
        overpassQlBuilder.append("[out:json];")
        overpassQlBuilder.append("node(around:", value.radius, ',', value.latitude, ',', value.longitude, ')')
        overpassQlBuilder.append("[name~", escapedQueryName, if (value.caseInvariant) ",i];" else "];")
        overpassQlBuilder.append("out;")

        return overpassQlBuilder.toString().toRequestBody()
    }
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

