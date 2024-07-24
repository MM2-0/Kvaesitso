package de.mm20.launcher2.ui.component.view

import android.widget.ListView
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.ktx.toDp

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
                modifier = Modifier
                    .padding(top = if (index != 0) view.dividerHeight.toDp() else 0.dp)
                    .layoutParams(itemView.layoutParams)
            )
        }
    }
}