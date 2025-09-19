package de.mm20.launcher2.searchactions

import de.mm20.launcher2.searchactions.builders.CustomWebsearchActionBuilder

fun knownWebsearchByHostname(hostname: String): CustomWebsearchActionBuilder? {
    // List of popular web search engines that do not implement the OpenSearch standard
    return when(hostname) {
        "google.com" -> CustomWebsearchActionBuilder(label = "Google", urlTemplate = "https://google.com/search?q=\${1}")
        "bing.com" -> CustomWebsearchActionBuilder(label = "Bing", urlTemplate = "https://bing.com/search?q=\${1}")
        "amazon.com" -> CustomWebsearchActionBuilder(label = "Amazon", urlTemplate = "https://www.amazon.com/s?k=\${1}")
        "amazon.de" -> CustomWebsearchActionBuilder(label = "Amazon DE", urlTemplate = "https://www.amazon.de/s?k=\${1}")
        "amazon.co.uk" -> CustomWebsearchActionBuilder(label = "Amazon UK", urlTemplate = "https://www.amazon.co.uk/s?k=\${1}")
        "amazon.fr" -> CustomWebsearchActionBuilder(label = "Amazon FR", urlTemplate = "https://www.amazon.fr/s?k=\${1}")
        "amazon.co.jp" -> CustomWebsearchActionBuilder(label = "Amazon JP", urlTemplate = "https://www.amazon.co.jp/s?k=\${1}")
        "amazon.ca" -> CustomWebsearchActionBuilder(label = "Amazon CA", urlTemplate = "https://www.amazon.ca/s?k=\${1}")
        "amazon.cn" -> CustomWebsearchActionBuilder(label = "Amazon CN", urlTemplate = "https://www.amazon.cn/s?k=\${1}")
        "duckduckgo.com" -> CustomWebsearchActionBuilder(label = "DuckDuckGo", urlTemplate = "https://duckduckgo.com/?q=\${1}")
        "yahoo.com" -> CustomWebsearchActionBuilder(label = "Yahoo", urlTemplate = "https://search.yahoo.com/search?p=\${1}")
        "ecosia.org" -> CustomWebsearchActionBuilder(label = "Ecosia", urlTemplate = "https://www.ecosia.org/search?q=\${1}")
        else -> null
    }
}
