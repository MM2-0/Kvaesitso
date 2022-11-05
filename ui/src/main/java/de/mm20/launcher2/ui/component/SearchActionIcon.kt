package de.mm20.launcher2.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import de.mm20.launcher2.searchactions.actions.SearchActionIcon

@Composable
fun SearchActionIcon(
    icon: SearchActionIcon,
    color: Int,
    customIcon: String? = null
) {
    val tint = when(color) {
        0 -> MaterialTheme.colorScheme.primary
        1 -> Color.Unspecified
        else -> Color(color)
    }
    if (icon != SearchActionIcon.Custom) {
        Icon(
            imageVector = getSearchActionIconVector(icon),
            contentDescription = null,
            tint = tint,
        )
    }
}

fun getSearchActionIconVector(icon: SearchActionIcon): ImageVector {
    return when (icon) {
        SearchActionIcon.Phone -> Icons.Rounded.Call
        SearchActionIcon.Website -> Icons.Rounded.Language
        SearchActionIcon.Alarm -> Icons.Rounded.Alarm
        SearchActionIcon.Timer -> Icons.Rounded.Timer
        SearchActionIcon.Contact -> Icons.Rounded.Person
        SearchActionIcon.Email -> Icons.Rounded.Email
        SearchActionIcon.Message -> Icons.Rounded.Sms
        SearchActionIcon.Calendar -> Icons.Rounded.Event
        SearchActionIcon.Translate -> Icons.Rounded.Translate
        else -> Icons.Rounded.Search
    }
}