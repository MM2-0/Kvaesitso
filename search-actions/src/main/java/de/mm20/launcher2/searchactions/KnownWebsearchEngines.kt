package de.mm20.launcher2.searchactions

import de.mm20.launcher2.searchactions.builders.WebsearchActionBuilder

fun knownWebsearchByHostname(hostname: String): WebsearchActionBuilder? {
    // List of popular web search engines that do not implement the OpenSearch standard
    return when(hostname) {
        "google.com" -> WebsearchActionBuilder(label = "Google", urlTemplate = "https://google.com/search?q=\${1}")
        "bing.com" -> WebsearchActionBuilder(label = "Google", urlTemplate = "https://bing.com/search?q=\${1}")
        "amazon.com" -> WebsearchActionBuilder(label = "Amazon", urlTemplate = "https://www.amazon.com/s?k=\${1}")
        "amazon.de" -> WebsearchActionBuilder(label = "Amazon DE", urlTemplate = "https://www.amazon.de/s?k=\${1}")
        "amazon.co.uk" -> WebsearchActionBuilder(label = "Amazon UK", urlTemplate = "https://www.amazon.co.uk/s?k=\${1}")
        "amazon.fr" -> WebsearchActionBuilder(label = "Amazon FR", urlTemplate = "https://www.amazon.fr/s?k=\${1}")
        "amazon.co.jp" -> WebsearchActionBuilder(label = "Amazon JP", urlTemplate = "https://www.amazon.co.jp/s?k=\${1}")
        "amazon.ca" -> WebsearchActionBuilder(label = "Amazon CA", urlTemplate = "https://www.amazon.ca/s?k=\${1}")
        "amazon.cn" -> WebsearchActionBuilder(label = "Amazon CN", urlTemplate = "https://www.amazon.cn/s?k=\${1}")
        "duckduckgo.com" -> WebsearchActionBuilder(label = "DuckDuckGo", urlTemplate = "https://duckduckgo.com/?q=\${1}")
        "yahoo.com" -> WebsearchActionBuilder(label = "DuckDuckGo", urlTemplate = "https://search.yahoo.com/search?p=\${1}")
        else -> null
    }
}