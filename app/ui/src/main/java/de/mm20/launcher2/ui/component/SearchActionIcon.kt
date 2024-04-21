package de.mm20.launcher2.ui.component

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.Games
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.mm20.launcher2.searchactions.actions.AppSearchAction
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.builders.AppSearchActionBuilder
import de.mm20.launcher2.searchactions.builders.CustomizableSearchActionBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SearchActionIcon(
    icon: SearchActionIcon,
    color: Int = 0,
    customIcon: String? = null,
    componentName: ComponentName? = null,
    size: Dp = 20.dp
) {
    val tint = when(color) {
        0 -> MaterialTheme.colorScheme.primary
        1 -> Color.Unspecified
        else -> Color(color)
    }
    if (icon != SearchActionIcon.Custom || customIcon == null && componentName == null) {
        Icon(
            modifier = Modifier.size(size),
            imageVector = getSearchActionIconVector(icon),
            contentDescription = null,
            tint = tint,
        )
    } else if (customIcon == null && componentName != null) {
        val context = LocalContext.current
        var drawable by remember(componentName) { mutableStateOf<Drawable?>(null) }

        LaunchedEffect(componentName) {
            drawable = withContext(Dispatchers.IO) {
                try {
                    context.packageManager.getActivityIcon(componentName)
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            }
        }

        AsyncImage(
            model = drawable,
            contentDescription = null,
            modifier = Modifier.size(size),
            colorFilter = if (tint.isSpecified) ColorFilter.tint(tint) else null
        )
    } else {
        AsyncImage(
            model = customIcon,
            contentDescription = null,
            modifier = Modifier.size(size),
            colorFilter = if (tint.isSpecified) ColorFilter.tint(tint) else null
        )
    }
}

@Composable
fun SearchActionIcon(action: SearchAction, size: Dp = 20.dp) {
    SearchActionIcon(
        icon = action.icon,
        color = action.iconColor,
        customIcon = action.customIcon,
        componentName = (action as? AppSearchAction)?.baseIntent?.component,
        size = size,
    )
}

@Composable
fun SearchActionIcon(builder: CustomizableSearchActionBuilder, size: Dp = 20.dp) {
   SearchActionIcon(
       icon = builder.icon,
       color = builder.iconColor,
       customIcon = builder.customIcon,
       componentName = (builder as? AppSearchActionBuilder)?.baseIntent?.component,
       size = size,
   )
}

fun getSearchActionIconVector(icon: SearchActionIcon): ImageVector {
    return when (icon) {
        SearchActionIcon.Phone -> Icons.Rounded.Call
        SearchActionIcon.Website -> Icons.Rounded.Language
        SearchActionIcon.Alarm -> Icons.Rounded.Alarm
        SearchActionIcon.Timer -> Icons.Rounded.Timer
        SearchActionIcon.Contact -> Icons.Rounded.PersonAdd
        SearchActionIcon.Email -> Icons.Rounded.Email
        SearchActionIcon.Message -> Icons.Rounded.Sms
        SearchActionIcon.Calendar -> Icons.Rounded.Event
        SearchActionIcon.Translate -> Icons.Rounded.Translate
        SearchActionIcon.Search -> Icons.Rounded.Search
        SearchActionIcon.Custom -> Icons.Rounded.Warning
        SearchActionIcon.WebSearch -> Icons.Rounded.TravelExplore
        SearchActionIcon.PersonSearch -> Icons.Rounded.PersonSearch
        SearchActionIcon.StatsSearch -> Icons.Rounded.QueryStats
        SearchActionIcon.SearchPage -> Icons.Rounded.FindInPage
        SearchActionIcon.SearchList -> Icons.Rounded.ManageSearch
        SearchActionIcon.ImageSearch -> Icons.Rounded.ImageSearch
        SearchActionIcon.Location -> Icons.Rounded.Place
        SearchActionIcon.Movie -> Icons.Rounded.Movie
        SearchActionIcon.Music -> Icons.Rounded.MusicNote
        SearchActionIcon.Game -> Icons.Rounded.SportsEsports
        SearchActionIcon.Note -> Icons.AutoMirrored.Rounded.StickyNote2
        SearchActionIcon.Share -> Icons.Rounded.Share
    }
}