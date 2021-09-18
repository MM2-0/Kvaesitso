package de.mm20.rssparser

import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

class RssParser(
        var url: String
) {

    lateinit var articles: List<Article>
        private set

    lateinit var feedInfo: FeedInfo
        private set

    /**
     * Fetches the feed from server and parses it.
     * Do not call this in main thread.
     * After this you can access [feedInfo] and [articles]
     * @return the list of articles in that RSS feed
     * @throws InvalidFeedException if the url does not point to a valid RSS2.0 feed
     * @throws java.io.IOException if the feed url does not exist or there was an error downloading it
     */
    @Throws(InvalidFeedException::class, IOException::class)
    fun loadFeed() {
        val articles = mutableListOf<Article>()
        try {
            val request = Request.Builder().url(url).build()
            val client = OkHttpClient()
            val feedStream = client.newCall(request).execute().body?.byteStream()
                    ?: throw IOException()
            val document = DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(feedStream)

            val rootElement = document.documentElement

            if (rootElement.tagName != "rss" || rootElement.getAttribute("version") != "2.0") {
                throw InvalidFeedException("Document is not an RSS 2.0 feed")
            }
            val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ROOT)
            feedInfo = FeedInfo(
                    title = document.getElementsByTagName("title").item(0)?.textContent ?: url,
                    link = document.getElementsByTagName("link").item(0)?.textContent ?: url,
                    description = document.getElementsByTagName("description").item(0)?.textContent
                            ?: "",
                    language = document.getElementsByTagName("language").item(0)?.textContent,
                    copyright = document.getElementsByTagName("copyright").item(0)?.textContent,
                    managingEditor = document.getElementsByTagName("managingEditor").item(0)?.textContent,
                    webMaster = document.getElementsByTagName("webMaster").item(0)?.textContent,
                    pubDate = parseDate(document.getElementsByTagName("pubDate")
                            .item(0)?.textContent ?: "", dateFormat),
                    lastBuildDate = parseDate(document.getElementsByTagName("lastBuildDate")
                            .item(0)?.textContent ?: "", dateFormat),
                    category = document.getElementsByTagName("category").run {
                        (0 until length).mapNotNull { item(it).textContent }
                    },
                    generator = document.getElementsByTagName("generator").item(0)?.textContent,
                    docs = document.getElementsByTagName("docs").item(0)?.textContent,
                    ttl = document.getElementsByTagName("generator").item(0)?.textContent?.toIntOrNull(),
                    image = (document.getElementsByTagName("image")
                            .item(0)as? Element)
                            ?.getElementsByTagName("url")
                            ?.item(0)?.textContent,
                    skipHours = (document.getElementsByTagName("skipHours") as? Element)
                            ?.getElementsByTagName("hour")?.run {
                                (0 until length).mapNotNull { item(it)?.textContent?.toIntOrNull() }
                            } ?: emptyList(),
                    skipDays = (document.getElementsByTagName("skipDays") as? Element)
                            ?.getElementsByTagName("day")?.run {
                                (0 until length).mapNotNull { getWeekday(item(it)?.textContent) }
                            } ?: emptyList()
            )
            val items = document.getElementsByTagName("item")

            for (i in 0 until items.length) {
                val item = items.item(i) as? Element ?: continue
                val article = Article(
                        title = item.getElementsByTagName("title").item(0)?.textContent ?: continue,
                        link = item.getElementsByTagName("link").item(0)?.textContent ?: "",
                        description = item.getElementsByTagName("description").item(0)?.textContent
                                ?: "",
                        author = item.getElementsByTagName("author").item(0)?.textContent,
                        categories = document.getElementsByTagName("category").run {
                            (0 until length).mapNotNull { item(it).textContent }
                        },
                        comments = item.getElementsByTagName("comments").item(0)?.textContent,
                        enclosure = item.getElementsByTagName("enclosure").item(0)?.textContent,
                        guid = item.getElementsByTagName("guid").item(0)?.textContent,
                        pubDate = parseDate(item.getElementsByTagName("pubDate").item(0)?.textContent
                                ?: "", dateFormat),
                        source = item.getElementsByTagName("source").item(0)?.textContent
                )
                articles.add(article)
            }

        } catch (e: SAXException) {
            throw InvalidFeedException()
        } catch (e: IOException) {
            throw IOException(e)
        }
        this.articles = articles
    }

    private fun parseDate(date: String, dateFormat: SimpleDateFormat): Long? {
        return try {
            dateFormat.parse(date)?.time
        } catch (e: ParseException) {
            null
        }
    }

    private fun getWeekday(day: String?): DayOfWeek? {
        return when (day) {
            "Sunday" -> DayOfWeek.SUNDAY
            "Monday" -> DayOfWeek.MONDAY
            "Tuesday" -> DayOfWeek.TUESDAY
            "Wednesday" -> DayOfWeek.WEDNESDAY
            "Thursday" -> DayOfWeek.SATURDAY
            "Friday" -> DayOfWeek.FRIDAY
            "Saturday" -> DayOfWeek.SATURDAY
            else -> null
        }
    }
}