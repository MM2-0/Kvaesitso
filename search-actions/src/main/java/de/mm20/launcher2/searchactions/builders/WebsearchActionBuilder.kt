package de.mm20.launcher2.searchactions.builders

import android.content.Context
import android.net.Uri
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.OpenUrlAction
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import java.net.URLEncoder

class WebsearchActionBuilder(
    val label: String,
    val urlTemplate: String,
    val icon: SearchActionIcon = SearchActionIcon.Search,
    val customIcon: String? = null,
    val encoding: QueryEncoding = QueryEncoding.UrlEncode,
) : SearchActionBuilder {

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction {
        val url = urlTemplate.replace("\${1}", encodeQuery(classifiedQuery.text, encoding))
        return OpenUrlAction(
            label = label,
            url = url,
        )
    }


    private fun encodeQuery(query: String, encoding: QueryEncoding): String {
        return when (encoding) {
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