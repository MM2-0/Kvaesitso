package de.mm20.launcher2.search

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.Xml
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.data.Websearch
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL

interface WebsearchRepository {
    fun search(query: String): Flow<List<Websearch>>

    fun getWebsearches(): Flow<List<Websearch>>

    fun insertWebsearch(websearch: Websearch)
    fun deleteWebsearch(websearch: Websearch)

    suspend fun importWebsearch(url: String, iconSize: Int): Websearch?
    suspend fun createIcon(uri: Uri, size: Int): String?
}

internal class WebsearchRepositoryImpl(
    private val context: Context,
    private val database: AppDatabase
) : WebsearchRepository, KoinComponent {

    private val dataStore: LauncherDataStore by inject()

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    override fun search(query: String): Flow<List<Websearch>> = channelFlow {
        if (query.isEmpty()) {
            send(emptyList())
            return@channelFlow
        }
        dataStore.data.map { it.webSearch.enabled }.collectLatest {
            if (it) {
                withContext(Dispatchers.IO) {
                    database.searchDao().getWebSearches().map {
                        it.map { Websearch(it, query) }
                    }
                }.collectLatest {
                    send(it)
                }
            } else {
                send(emptyList())
            }
        }
    }

    override fun getWebsearches(): Flow<List<Websearch>> =
        database.searchDao().getWebSearches().map {
            it.map { Websearch(it) }
        }

    override fun insertWebsearch(websearch: Websearch) {
        scope.launch {
            withContext(Dispatchers.IO) {
                database.searchDao().insertWebsearch(websearch.toDatabaseEntity())
            }
        }
    }

    override fun deleteWebsearch(websearch: Websearch) {
        scope.launch {
            withContext(Dispatchers.IO) {
                database.searchDao().deleteWebsearch(websearch.toDatabaseEntity())
            }
        }
    }

    override suspend fun importWebsearch(url: String, iconSize: Int): Websearch? =
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

                    return@withContext Websearch(
                        urlTemplate = urlTemplate ?: "",
                        label = label ?: "",
                        icon = localIconUrl,
                        color = 0,
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
        val file = File(context.dataDir, System.currentTimeMillis().toString())
        val imageRequest = ImageRequest.Builder(context)
            .data(uri)
            .size(size)
            .scale(Scale.FIT)
            .build()
        val drawable = context.imageLoader.execute(imageRequest).drawable ?: return@withContext null
        val scaledIcon = drawable.toBitmap()
        val out = FileOutputStream(file)
        scaledIcon.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.close()
        return@withContext file.absolutePath
    }
}