package de.mm20.launcher2.searchactions.builders

import android.content.Context
import android.net.Uri
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.TextType
import de.mm20.launcher2.searchactions.actions.OpenUrlAction
import java.net.URLEncoder

class WebsearchActionBuilder(
    val label: String,
    val urlTemplate: String,
    val filter: TextType? = null,
    val encoding: QueryEncoding,
) : SearchActionBuilder {

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        if (filter == null || classifiedQuery.type == filter) {
            val url = urlTemplate.replace("\${1}", encodeQuery(classifiedQuery.text, encoding))
            return OpenUrlAction(
                label = label,
                url = url,
            )
        }
        return null
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