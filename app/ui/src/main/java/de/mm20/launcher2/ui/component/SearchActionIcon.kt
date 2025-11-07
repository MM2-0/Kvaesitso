package de.mm20.launcher2.ui.component

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.mm20.launcher2.searchactions.actions.AppSearchAction
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.builders.AppSearchActionBuilder
import de.mm20.launcher2.searchactions.builders.CustomizableSearchActionBuilder
import de.mm20.launcher2.ui.R
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
    val tint = when (color) {
        0 -> MaterialTheme.colorScheme.primary
        1 -> Color.Unspecified
        else -> Color(color)
    }
    if (icon != SearchActionIcon.Custom || customIcon == null && componentName == null) {
        Icon(
            modifier = Modifier.size(size),
            painter = painterResource(getSearchActionIconVector(icon)),
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

fun getSearchActionIconVector(icon: SearchActionIcon): Int {
    return when (icon) {
        SearchActionIcon.Phone -> R.drawable.call_24px
        SearchActionIcon.Website -> R.drawable.language_24px
        SearchActionIcon.Alarm -> R.drawable.alarm_24px
        SearchActionIcon.Timer -> R.drawable.timer_24px
        SearchActionIcon.Contact -> R.drawable.person_add_24px
        SearchActionIcon.Email -> R.drawable.mail_24px
        SearchActionIcon.Message -> R.drawable.sms_24px
        SearchActionIcon.Calendar -> R.drawable.event_24px
        SearchActionIcon.Translate -> R.drawable.translate_24px
        SearchActionIcon.Search -> R.drawable.search_24px
        SearchActionIcon.Custom -> R.drawable.warning_24px
        SearchActionIcon.WebSearch -> R.drawable.travel_explore_24px
        SearchActionIcon.PersonSearch -> R.drawable.person_search_24px
        SearchActionIcon.StatsSearch -> R.drawable.query_stats_24px
        SearchActionIcon.SearchPage -> R.drawable.find_in_page_24px
        SearchActionIcon.SearchList -> R.drawable.manage_search_24px
        SearchActionIcon.ImageSearch -> R.drawable.image_search_24px
        SearchActionIcon.Location -> R.drawable.location_on_24px
        SearchActionIcon.Movie -> R.drawable.movie_24px
        SearchActionIcon.Music -> R.drawable.music_note_24px
        SearchActionIcon.Game -> R.drawable.sports_esports_24px
        SearchActionIcon.Note -> R.drawable.sticky_note_2_24px
        SearchActionIcon.Share -> R.drawable.share_24px
    }
}