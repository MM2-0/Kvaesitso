package de.mm20.launcher2.searchactions

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.Xml
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.builders.CallActionBuilder
import de.mm20.launcher2.searchactions.builders.SearchActionBuilder
import de.mm20.launcher2.searchactions.builders.WebsearchActionBuilder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.UUID

interface SearchActionService {
    fun search(query: String): Flow<ImmutableList<SearchAction>>

    fun getSearchActionBuilders(): Flow<List<SearchActionBuilder>>
    fun getDisabledActionBuilders(): Flow<List<SearchActionBuilder>>

    fun saveSearchActionBuilders(builders: List<SearchActionBuilder>)

    suspend fun importWebsearch(url: String, iconSize: Int): WebsearchActionBuilder?

    suspend fun getSearchActivities(): List<ResolveInfo>

    suspend fun createIcon(uri: Uri, size: Int): String?
}

internal class SearchActionServiceImpl(
    private val context: Context,
    private val repository: SearchActionRepository,
    private val textClassifier: TextClassifier,
) : SearchActionService {
    override fun search(
        query: String
    ): Flow<ImmutableList<SearchAction>> = flow {
        if (query.isBlank()) {
            emit(persistentListOf())
            return@flow
        }

        val classificationResult = textClassifier.classify(context, query)

        val builders = repository.getSearchActionBuilders()

        emitAll(
            builders.map {
                it.mapNotNull { it.build(context, classificationResult) }.toImmutableList()
            }
        )
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

    override suspend fun importWebsearch(url: String, iconSize: Int): WebsearchActionBuilder? =
        withContext(Dispatchers.IO) {
            try {
                val u = if (url.startsWith("http://") || url.startsWith("https://")) {
                    url
                } else {
                    "https://$url"
                }
                val document = Jsoup.parse(URL(u), 5000)
                val metaElements =
                    document.select("link[rel=\"search\"][href][type=\"application/opensearchdescription+xml\"]")
                val openSearchHref = metaElements
                    .getOrNull(0)
                    ?.absUrl("href")
                    ?.takeIf { it.isNotEmpty() }
                    ?: return@withContext run {
                        Log.d("MM20", "Specified URL does not implement the OpenSearch protocol")
                        null
                    }

                val httpClient = OkHttpClient()
                val request = Request.Builder()
                    .url(openSearchHref)
                    .build()
                val response = httpClient.newCall(request).execute()
                val inputStream = response.body?.byteStream() ?: return@withContext null

                var label: String? = null
                var urlTemplate: String? = null
                var icon: String? = null
                var iconSize: Int = 0
                var iconUrl: String? = null

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
                                    val size =
                                        parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                                    if (size > iconSize || iconUrl == null) {
                                        parser.next()
                                        if (parser.eventType == XmlPullParser.TEXT) {
                                            iconUrl = parser.text
                                            iconSize = size
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

                    val localIconUrl = iconUrl?.let {
                        val uri = Uri.parse(it)
                        createIcon(uri, iconSize)
                    }

                    return@withContext WebsearchActionBuilder(
                        label = label ?: "",
                        icon = if (localIconUrl == null) SearchActionIcon.Search else SearchActionIcon.Custom,
                        customIcon = localIconUrl,
                        urlTemplate = urlTemplate ?: ""
                    )
                }
            } catch (e: IOException) {
                CrashReporter.logException(e)
            } catch (e: XmlPullParserException) {
                CrashReporter.logException(e)
            }
            return@withContext null
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

    override suspend fun getSearchActivities(): List<ResolveInfo> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_SEARCH)
        return packageManager.queryIntentActivities(intent, 0)
    }
}