package de.mm20.launcher2.locations.providers.openstreetmaps

import de.mm20.launcher2.serialization.Json
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.path
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlin.math.cos

internal sealed interface OverpassQuery {
    fun toQueryString(): String
}

/**
 * Overpass API query builder
 * Searches for nodes and ways that at least:
 * - match the query string in their name or brand tag
 * - match one of the given tag groups
 */
internal data class OverpassFuzzyRadiusQuery(
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
) : OverpassQuery {
    override fun toQueryString(): String {
        val encodedQuery = query.split(' ')
            .joinToString(
                separator = ".*",
                prefix = "\"",
                postfix = "\""
            ) { Regex.escapeReplacement(it) }

        val overpassQlBuilder = StringBuilder()
        val latDegreeChange = radius * 0.00001 / 1.11
        val lonDegreeChange = latDegreeChange / cos(Math.toRadians(latitude))
        val boundingBox = arrayOf(
            latitude - latDegreeChange, longitude - lonDegreeChange,
            latitude + latDegreeChange, longitude + lonDegreeChange
        )
        overpassQlBuilder.append("[out:json][timeout:10][bbox:" + boundingBox.joinToString(",") + "];")

        overpassQlBuilder.append("(")
        overpassQlBuilder.append("nw[name~$encodedQuery,i];")
        overpassQlBuilder.append("nw[brand~$encodedQuery,i];")
        for (tag in tagGroups) {
            val tags = tag.split(',')

            if (tags.isEmpty()) continue

            overpassQlBuilder.append("nw[${tags.joinToString("][")}];")
        }
        overpassQlBuilder.append(");")
        // center to add the center coordinate of a way to the result, if applicable
        overpassQlBuilder.append("out center;")

        return overpassQlBuilder.toString()
    }
}

internal data class OverpassIdQuery(
    val id: Long,
) : OverpassQuery {
    override fun toQueryString(): String {
        return """
            [out:json];
            nw($id);
            out center;
        """.trimIndent()
    }
}

@Serializable
internal data class OverpassResponse(
    val elements: List<OverpassResponseElement>,
)

@Serializable
internal data class OverpassResponseElementCenter(
    val lat: Double,
    val lon: Double,
)

@Serializable
internal data class OverpassResponseElement(
    val type: String,
    val id: Long,
    val lat: Double?,
    val lon: Double?,
    val center: OverpassResponseElementCenter?,
    val tags: Map<String, String>?,
)

internal class OverpassApi {

    private val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json.Lenient)
            }
        }
    }

    suspend fun interpreter(
        baseUrl: String,
        query: OverpassQuery,
    ): OverpassResponse {
        return httpClient.post {
            url {
                takeFrom(baseUrl)
                if (pathSegments.isEmpty()) {
                    path("api", "interpreter")
                }
            }
            setBody(query.toQueryString())
        }.body()
    }
}


