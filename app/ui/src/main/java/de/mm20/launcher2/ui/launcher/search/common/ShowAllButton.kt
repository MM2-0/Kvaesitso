package de.mm20.launcher2.ui.launcher.search.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.ktx.TextButtonWithTrailingIconContentPadding

@Composable
fun ColumnScope.ShowAllButton(
    onShowAll: () -> Unit,
) {
    TextButton(
        modifier = Modifier
            .align(Alignment.End)
            .padding(4.dp),
        onClick = onShowAll,
        contentPadding = ButtonDefaults.TextButtonWithTrailingIconContentPadding,
    ) {
        Text(stringResource(R.string.show_all))
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            null,
            modifier = Modifier
                .padding(start = ButtonDefaults.IconSpacing)
                .size(ButtonDefaults.IconSize)
        )
    }
}