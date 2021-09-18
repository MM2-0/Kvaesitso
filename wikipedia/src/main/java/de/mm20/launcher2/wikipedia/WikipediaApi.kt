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
)

data class WikipediaGetPageImageResult(
    val query: WikipediaGetPageImageResultQuery?,
)

data class WikipediaGetPageImageResultQuery(
    val pages: Map<String, WikipediaGetPageImageResultQueryPage>
)

data class WikipediaGetPageImageResultQueryPage(
    val thumbnail: WikipediaGetPageImageResultQueryPageThumnail?
)

data class WikipediaGetPageImageResultQueryPageThumnail(
    val source: String
)

interface WikipediaApi {
    @GET("w/api.php?action=query&generator=search&redirects=true&gsrlimit=1&explaintext=true&exchars=500&prop=extracts&exintro=true&format=json")
    suspend fun search(@Query("gsrsearch") query: String): WikipediaSearchResult

    @GET("w/api.php?action=query&prop=pageimages&format=json")
    suspend fun getPageImage(@Query("pageids") pageId: Long, @Query("pithumbsize") thumbnailSize: Int): WikipediaGetPageImageResult
}