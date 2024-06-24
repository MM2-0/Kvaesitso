package de.mm20.launcher2.ui.component.view

import android.widget.ListView
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ComposeListView(
    view: ListView,
    modifier: Modifier,
) {
    val adapter = view.adapter ?: return
    LazyColumn(
        modifier = modifier,
    ) {
        items(
            adapter.count,
            contentType = { adapter.getItemViewType(it) },
        ) { index ->
            val itemView = adapter.getView(index, null, view)
            ComposeAndroidView(
                itemView,
                modifier = Modifier.layoutParams(itemView.layoutParams)
            )
        }
    }
}