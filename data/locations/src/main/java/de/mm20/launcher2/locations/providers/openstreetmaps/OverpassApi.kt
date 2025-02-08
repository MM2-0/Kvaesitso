package de.mm20.launcher2.locations.providers.openstreetmaps

import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import java.lang.reflect.Type
import kotlin.math.cos

/**
 * Overpass API query builder
 * Searches for nodes and ways that at least:
 * - match the query string in their name or brand tag
 * - match one of the given tag groups
 */
data class OverpassFuzzyRadiusQuery(
    /**
     * Free text query to search for.
     */
    val query: String,
    /**
     * Tags groups to search for. Each item represents a group of tags, separated by commas.
     * The query matches if all tags in a group are present in the element.
     * For example:
     * ["amenity=restaurant,cuisine=italian", "amenity=cafe"]
     * This query will match elements that are either a restaurant with italian cuisine or a cafe.
     */
    val tagGroups: List<String>,
    val radius: Int,
    val latitude: Double,
    val longitude: Double,
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
    val tags: Map<String, String>?,
)

interface OverpassApi {
    @POST("api/interpreter")
    suspend fun search(@Body data: OverpassFuzzyRadiusQuery): OverpassResponse

    @POST("api/interpreter")
    suspend fun search(@Body data: OverpassIdQuery): OverpassResponse
}

class OverpassFuzzyRadiusQueryConverter : Converter<OverpassFuzzyRadiusQuery, RequestBody> {
    override fun convert(value: OverpassFuzzyRadiusQuery): RequestBody {
        val encodedQuery = value.query.split(' ')
            .joinToString(
                separator = ".*",
                prefix = "\"",
                postfix = "\""
            ) { Regex.escapeReplacement(it) }

        val overpassQlBuilder = StringBuilder()
        val latDegreeChange = value.radius * 0.00001 / 1.11
        val lonDegreeChange = latDegreeChange / cos(Math.toRadians(value.latitude))
        val boundingBox = arrayOf(
            value.latitude - latDegreeChange, value.longitude - lonDegreeChange,
            value.latitude + latDegreeChange, value.longitude + lonDegreeChange
        )
        overpassQlBuilder.append("[out:json][timeout:10][bbox:" + boundingBox.joinToString(",") + "];")

        overpassQlBuilder.append("(")
        overpassQlBuilder.append("nw[name~$encodedQuery,i];")
        overpassQlBuilder.append("nw[brand~$encodedQuery,i];")
        for (tag in value.tagGroups) {
            val tags = tag.split(',')

            if (tags.isEmpty()) continue

            overpassQlBuilder.append("nw[${tags.joinToString("][")}];")
        }
        overpassQlBuilder.append(");")
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

