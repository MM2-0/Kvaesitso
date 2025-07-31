package de.mm20.launcher2.ui.launcher.search.files

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import de.mm20.launcher2.search.File
import de.mm20.launcher2.ui.launcher.search.common.ShowAllButton
import de.mm20.launcher2.ui.launcher.search.common.list.ListItem
import de.mm20.launcher2.ui.launcher.search.common.list.ListResults
import kotlin.math.min

fun LazyListScope.FileResults(
    files: List<File>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    highlightedItem: File? = null,
    reverse: Boolean,
    truncate: Boolean,
    onShowAll: () -> Unit,
) {
    ListResults(
        items = files.subList(0, if (truncate) min(5, files.size) else files.size),
        key = "file",
        reverse = reverse,
        selectedIndex = selectedIndex,
        itemContent = { file, showDetails, index ->
            ListItem(
                modifier = Modifier
                    .fillMaxWidth(),
                item = file,
                showDetails = showDetails,
                onShowDetails = { onSelect(if (it) index else -1) },
                highlight = highlightedItem?.key == file.key
            )
        },
        after = if (truncate && files.size > 5) {
            {
                ShowAllButton(onShowAll = onShowAll)
            }
        } else null
    )
}