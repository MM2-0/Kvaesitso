package de.mm20.launcher2.search.data

import android.content.Intent
import android.net.Uri
import de.mm20.launcher2.database.entities.WebsearchEntity
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class Websearch(
    var urlTemplate: String,
    var label: String,
    var color: Int,
    var icon: String?,
    var id: Long? = null,
    var encoding: QueryEncoding = QueryEncoding.UrlEncode,
    val query: String? = null,
) {

    constructor(entity: WebsearchEntity, query: String? = null) : this(
        urlTemplate = entity.urlTemplate,
        label = entity.label,
        icon = entity.icon,
        color = entity.color,
        id = entity.id,
        query = query,
        encoding = QueryEncoding.fromInt(entity.encoding)
    )

    fun toDatabaseEntity(): WebsearchEntity {
        return WebsearchEntity(
            urlTemplate = urlTemplate,
            color = color,
            icon = icon,
            label = label,
            id = id,
            encoding = encoding.toInt()
        )
    }

    fun getLaunchIntent(): Intent? {
        if (query == null) return null
        val intent = Intent(Intent.ACTION_VIEW)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        val url = urlTemplate.replace("\${1}", encodeQuery(query, encoding))
        intent.data = Uri.parse(url)
        return intent
    }

    private fun encodeQuery(query: String, encoding: QueryEncoding): String {
        return when(encoding) {
            QueryEncoding.UrlEncode -> Uri.encode(query)
            QueryEncoding.FormData -> URLEncoder.encode(query, "UTF-8")
            QueryEncoding.None -> query
        }
    }

    enum class QueryEncoding {
        UrlEncode,
        FormData,
        None;

        fun toInt(): Int {
            return when (this) {
                UrlEncode -> 0
                FormData -> 1
                None -> 2
            }
        }

        companion object {
            fun fromInt(value: Int?): QueryEncoding {
                return when (value) {
                    1 -> FormData
                    2 -> None
                    else -> UrlEncode
                }
            }
        }
    }
}