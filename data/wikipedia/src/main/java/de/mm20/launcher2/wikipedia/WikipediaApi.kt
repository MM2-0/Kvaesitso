package de.mm20.launcher2.wikipedia

import retrofit2.http.GET
import retrofit2.http.Query

data class WikipediaSearchResult(
    val query: WikipediaSearchResultQuery?,
)

data class WikipediaSearchResultQuery(
    val pages: Map<String, WikipediaSearchResultQueryPage>,
)

data class WikipediaSearchResultQueryPage(
    val pageid: Long,
    val title: String,
    val extract: String,
    val thumbnail: WikipediaSearchResultQueryPageThumnail?,
    val fullurl: String,
    val canonicalurl: String,
)

data class WikipediaSearchResultQueryPageThumnail(
    val source: String
)

interface WikipediaApi {
    @GET("w/api.php?action=query&generator=search&redirects=true&gsrlimit=1&prop=extracts|info|pageimages&exchars=500&exintro=true&inprop=url&format=json")
    suspend fun search(@Query("gsrsearch") query: String, @Query("pithumbsize") thumbnailSize: Int): WikipediaSearchResult
}