package de.mm20.launcher2.searchactions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Xml
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.builders.SearchActionBuilder
import de.mm20.launcher2.searchactions.builders.CustomWebsearchActionBuilder
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.UUID

interface SearchActionService {
    suspend fun search(query: String): Flow<ImmutableList<SearchAction>>

    fun getSearchActionBuilders(): Flow<List<SearchActionBuilder>>
    fun getDisabledActionBuilders(): Flow<List<SearchActionBuilder>>

    fun saveSearchActionBuilders(builders: List<SearchActionBuilder>)

    suspend fun importWebsearch(url: String, iconSize: Int): CustomWebsearchActionBuilder?

    suspend fun getSearchActivities(): List<ComponentName>

    suspend fun createIcon(uri: Uri, size: Int): String?
}

internal class SearchActionServiceImpl(
    private val context: Context,
    private val repository: SearchActionRepository,
    private val textClassifier: TextClassifier,
) : SearchActionService {
    override suspend fun search(
        query: String
    ): Flow<ImmutableList<SearchAction>> {

        if (query.isBlank()) {
            return flowOf(persistentListOf())
        }

        val classificationResult = textClassifier.classify(context, query)

        val builders = repository.getSearchActionBuilders()

        return builders.map {
            it.mapNotNull { it.build(context, classificationResult) }.toImmutableList()
        }
    }

    override fun getSearchActionBuilders(): Flow<List<SearchActionBuilder>> {
        return repository.getSearchActionBuilders()
    }

    override fun getDisabledActionBuilders(): Flow<List<SearchActionBuilder>> {
        val allActions = repository.getBuiltinSearchActionBuilders()

        return getSearchActionBuilders().map { enabled ->
            allActions.filter { action -> !enabled.any { it.key == action.key } }
        }
    }

    override fun saveSearchActionBuilders(builders: List<SearchActionBuilder>) {
        repository.saveSearchActionBuilders(builders)
    }

    override suspend fun importWebsearch(url: String, iconSize: Int): CustomWebsearchActionBuilder? =
        withContext(Dispatchers.IO) {
            try {
                val u = if (url.startsWith("http://") || url.startsWith("https://")) {
                    url
                } else {
                    "https://$url"
                }

                if (u.contains("${1}")) {
                    return@withContext CustomWebsearchActionBuilder(
                        urlTemplate = u,
                        label = "",
                        iconColor = 0,
                        icon = SearchActionIcon.Search,
                    )
                }

                val document = Jsoup.parse(URL(u), 5000)
                val metaElements =
                    document.select("link[rel=\"search\"][href][type=\"application/opensearchdescription+xml\"]")
                val openSearchHref = metaElements
                    .getOrNull(0)
                    ?.absUrl("href")
                    ?.takeIf { it.isNotEmpty() }

                val action = openSearchHref?.let {
                    importOpenSearch(it, iconSize)
                }

                if (action != null) {
                    return@withContext action
                }

                val host = URL(u).host ?: return@withContext null
                return@withContext knownWebsearchByHostname(host)

            } catch (e: IOException) {
                CrashReporter.logException(e)
            } catch (e: XmlPullParserException) {
                CrashReporter.logException(e)
            }
            return@withContext null
        }

    private suspend fun importOpenSearch(
        openSearchHref: String,
        iconSize: Int
    ): CustomWebsearchActionBuilder? {
        try {
            val httpClient = HttpClient()
            val response = httpClient.get {
                url(openSearchHref)
            }
            val inputStream = response.bodyAsChannel().toInputStream()

            var label: String? = null
            var urlTemplate: String? = null
            var largestIconSize: Int = 0
            var largestIcon: String? = null

            inputStream.use {
                val parser = Xml.newPullParser()
                parser.setInput(inputStream.reader())
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "ShortName" -> {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    label = parser.text
                                }
                            }

                            "LongName" -> {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    if (label != null) label = parser.text
                                }
                            }

                            "Image" -> {
                                val width =
                                    parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                                if (width > largestIconSize || largestIcon == null) {
                                    parser.next()
                                    if (parser.eventType == XmlPullParser.TEXT) {
                                        largestIcon = parser.text
                                        largestIconSize = width
                                    }
                                }
                            }

                            "Url" -> {
                                if (parser.getAttributeValue(null, "type") == "text/html") {
                                    val rel = parser.getAttributeValue(null, "rel")
                                    if (rel == null || rel == "results") {
                                        val template =
                                            parser.getAttributeValue(null, "template")
                                                ?.takeIf { it.isNotEmpty() } ?: continue
                                        urlTemplate = template
                                            .replace("{searchTerms}", "\${1}")
                                            .replace("{startPage?}", "1")
                                    }
                                }
                            }

                            else -> continue
                        }
                    }
                }

                val localIconUrl = largestIcon?.let {
                    val uri = Uri.parse(it)
                    createIcon(uri, iconSize)
                }

                return CustomWebsearchActionBuilder(
                    label = label ?: "",
                    icon = if (localIconUrl == null) SearchActionIcon.Search else SearchActionIcon.Custom,
                    customIcon = localIconUrl,
                    iconColor = if (localIconUrl == null) 0 else 1,
                    urlTemplate = urlTemplate ?: ""
                )
            }
        } catch (e: IOException) {

        } catch (e: XmlPullParserException) {
        }
        return null
    }

    override suspend fun createIcon(uri: Uri, size: Int): String? = withContext(
        Dispatchers.IO
    ) {
        val file = File(context.filesDir, UUID.randomUUID().toString())
        val imageRequest = ImageRequest.Builder(context)
            .data(uri)
            .size(size)
            .scale(Scale.FIT)
            .build()
        val drawable =
            context.imageLoader.execute(imageRequest).drawable ?: return@withContext null
        val scaledIcon = drawable.toBitmap()
        val out = FileOutputStream(file)
        scaledIcon.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.close()
        return@withContext file.absolutePath
    }

    override suspend fun getSearchActivities(): List<ComponentName> {
        return withContext(Dispatchers.Default) {
            val resolveInfos = context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_SEARCH).addCategory(Intent.CATEGORY_DEFAULT),
                PackageManager.GET_META_DATA,
            )
            resolveInfos.mapNotNull {
                if (!it.activityInfo.exported || !it.activityInfo.enabled) return@mapNotNull null
                if (it.activityInfo.permission != null && context.checkSelfPermission(it.activityInfo.permission) != PackageManager.PERMISSION_GRANTED) {
                    return@mapNotNull null
                }
                val componentName = ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                componentName
            }
        }
    }
}