package de.mm20.launcher2.locations.providers.openstreetmaps

import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import java.lang.reflect.Type

data class OverpassFuzzyRadiusQuery(
    val tag: String = "name",
    val query: String,
    val radius: Int,
    val latitude: Double,
    val longitude: Double,
    val caseInvariant: Boolean = true,
)

data class OverpassIdQuery(
    val id: Long,
)

data class OverpassResponse(
    val elements: List<OverpassResponseElement>,
)

data class OverpassResponseElementCenter(
    val lat: Double,
    val lon: Double,
)

data class OverpassResponseElement(
    val type: String,
    val id: Long,
    val lat: Double?,
    val lon: Double?,
    val center: OverpassResponseElementCenter?,
    val tags: Map<String, String>,
)

interface OverpassApi {
    @POST("api/interpreter")
    suspend fun search(@Body data: OverpassFuzzyRadiusQuery): OverpassResponse

    @POST("api/interpreter")
    suspend fun search(@Body data: OverpassIdQuery): OverpassResponse
}

class OverpassFuzzyRadiusQueryConverter : Converter<OverpassFuzzyRadiusQuery, RequestBody> {
    override fun convert(value: OverpassFuzzyRadiusQuery): RequestBody {

        // allow other characters in between query words, if there are multiple
        // https://dev.overpass-api.de/overpass-doc/en/criteria/per_tag.html#regex
        val escapedQueryName = value
            .query
            .split(' ')
            .joinToString(
                separator = ".*",
                prefix = "\"",
                postfix = "\""
            ) { Regex.escapeReplacement(it) }

        val overpassQlBuilder = StringBuilder()
        overpassQlBuilder.append("[out:json];")
        // nw: node or way
        overpassQlBuilder.append("nw(around:", value.radius, ',', value.latitude, ',', value.longitude, ')')
        overpassQlBuilder.append('[', value.tag, '~', escapedQueryName, if (value.caseInvariant) ",i];" else "];")
        // center to add the center coordinate of a way to the result, if applicable
        overpassQlBuilder.append("out center;")

        return overpassQlBuilder.toString().toRequestBody()
    }
}

class OverpassIdQueryConverter : Converter<OverpassIdQuery, RequestBody> {
    override fun convert(value: OverpassIdQuery): RequestBody = """
        [out:json];
        nw(${value.id});
        out center;
    """.trimIndent().toRequestBody()
}

class OverpassQueryConverterFactory : Converter.Factory() {
    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        if (type == OverpassFuzzyRadiusQuery::class.java)
            return OverpassFuzzyRadiusQueryConverter()

        if (type == OverpassIdQuery::class.java)
            return OverpassIdQueryConverter()

        return null
    }
}

