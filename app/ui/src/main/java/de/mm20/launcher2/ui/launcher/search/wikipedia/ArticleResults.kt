package de.mm20.launcher2.ui.launcher.search.wikipedia

import androidx.compose.foundation.lazy.LazyListScope
import de.mm20.launcher2.search.Article
import de.mm20.launcher2.ui.launcher.search.common.list.ListItem
import de.mm20.launcher2.ui.launcher.search.common.list.ListResults

fun LazyListScope.ArticleResults(
    articles: List<Article>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    highlightedItem: Article?,
    reverse: Boolean,
) {
    ListResults(
        key = "article",
        items = articles,
        itemContent = { article, showDetails, index ->
            ListItem(
                item = article,
                showDetails = showDetails,
                onShowDetails = { onSelect(if(it) index else -1) },
                highlight = article.key == highlightedItem?.key,
            )
        },
        selectedIndex = selectedIndex,
        reverse = reverse,
    )
}