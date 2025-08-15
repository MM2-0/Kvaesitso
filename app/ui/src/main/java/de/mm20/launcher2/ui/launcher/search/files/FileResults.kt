package de.mm20.launcher2.ui.launcher.search.files

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import de.mm20.launcher2.search.File
import de.mm20.launcher2.ui.launcher.search.common.list.ListItem
import de.mm20.launcher2.ui.launcher.search.common.list.ListResults

fun LazyListScope.FileResults(
    files: List<File>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    highlightedItem: File? = null,
    reverse: Boolean,
) {
    ListResults(
        items = files,
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
    )
}