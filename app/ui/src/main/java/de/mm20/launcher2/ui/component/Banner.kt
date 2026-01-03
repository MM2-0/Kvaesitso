package de.mm20.launcher2.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun Banner(
    modifier: Modifier = Modifier,
    text: String,
    @DrawableRes icon: Int,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    primaryAction: (@Composable () -> Unit)? = null,
    secondaryAction: (@Composable () -> Unit)? = null,
) {
    OutlinedCard(
        modifier = modifier,
        colors = CardDefaults.outlinedCardColors(
            containerColor = color,
            contentColor = MaterialTheme.colorScheme.secondary,
        ),
        shape = MaterialTheme.shapes.small,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier.padding(end = 16.dp).size(24.dp),
                    painter = painterResource(icon),
                    contentDescription = null
                )
                Text(
                    text = text,
                    modifier = Modifier
                        .weight(1f),
                    style = MaterialTheme.typography.bodySmallEmphasized
                )
            }
            if (secondaryAction != null || primaryAction != null) {
                FlowRow(
                    Modifier
                        .align(Alignment.End)
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalArrangement = Arrangement.Bottom,
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        secondaryAction?.invoke()
                    }
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        primaryAction?.invoke()
                    }

                }
            }

        }
    }
}