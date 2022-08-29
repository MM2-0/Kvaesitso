package de.mm20.launcher2.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.flowlayout.FlowCrossAxisAlignment
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.flowlayout.MainAxisAlignment

@Composable
fun Banner(
    modifier: Modifier = Modifier,
    text: String,
    icon: ImageVector,
    primaryAction: @Composable () -> Unit,
    secondaryAction: @Composable () -> Unit = {}
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier.padding(16.dp),
                    imageVector = icon,
                    contentDescription = null
                )
                Text(
                    text = text,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 16.dp)
                        .padding(end = 16.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            FlowRow(
                Modifier
                    .align(Alignment.End)
                    .padding(8.dp),
                crossAxisSpacing = 8.dp,
                crossAxisAlignment = FlowCrossAxisAlignment.End,
                mainAxisAlignment = MainAxisAlignment.End
            ) {
                Box {
                    secondaryAction()
                }
                Box(modifier = Modifier.padding(start = 8.dp)) {
                    primaryAction()
                }

            }

        }
    }
}