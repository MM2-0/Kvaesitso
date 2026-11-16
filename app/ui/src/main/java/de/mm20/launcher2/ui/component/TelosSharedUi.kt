package de.mm20.launcher2.ui.component
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.packages.TelosIconCache
import de.mm20.launcher2.ui.R

/**
 * Reusable, high-performance Jetpack Compose UI kit shared across built-in Virtual Apps
 * (Telos ART Optimizer, Smart Freeze Manager, Telos Store, Telos System).
 *
 * All state models are marked with [@Immutable] / [@Stable] to guarantee zero unnecessary recompositions
 * and consistent 120Hz scrolling performance.
 */

@Immutable
data class TelosBadgeData(
    val text: String,
    val containerColor: Color? = null,
    val contentColor: Color? = null,
)

enum class SharedAppFilter(val labelRes: Int) {
    ALL(I18nR.string.art_optimizer_filter_all),
    USER(I18nR.string.store_tab_installed_apps),
    SYSTEM(I18nR.string.store_show_system_apps),
    FROZEN(I18nR.string.art_optimizer_filter_frozen),
    NEEDS_ATTENTION(I18nR.string.art_optimizer_filter_needed),
}

/**
 * Universal app list row with downsampled [TelosIconCache] integration, package name,
 * dynamic status badges (Frozen, ART filter, storage size), and custom trailing actions.
 */
@Composable
fun TelosAppListRow(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
    versionCode: Long = 0L,
    apkBytes: Long = 0L,
    isFrozen: Boolean = false,
    extraBadges: List<TelosBadgeData> = emptyList(),
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var iconBitmap by remember(packageName) {
        mutableStateOf(TelosIconCache.getCached(packageName))
    }

    LaunchedEffect(packageName, versionCode) {
        if (iconBitmap == null) {
            iconBitmap = TelosIconCache.getOrLoad(context, packageName, versionCode)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                } else Modifier
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                val currentIcon = iconBitmap
                if (currentIcon != null) {
                    Image(
                        bitmap = currentIcon,
                        contentDescription = label,
                        modifier = Modifier.size(40.dp),
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {}
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val sizeText = if (apkBytes > 0L) Formatter.formatFileSize(context, apkBytes) else null
                val subtitle = listOfNotNull(packageName, sizeText).joinToString(" • ")

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (isFrozen || extraBadges.isNotEmpty()) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isFrozen) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ) {
                                Text(
                                    text = "❄ " + stringResource(I18nR.string.art_optimizer_filter_frozen),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        for (badge in extraBadges) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = badge.containerColor ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = badge.contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                Text(
                                    text = badge.text,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (trailingContent != null) {
                Spacer(Modifier.width(8.dp))
                trailingContent()
            }
        }
    }
}

/**
 * Standardized Material 3 card for warnings, hardware advisories, or performance explanations.
 */
@Composable
fun TelosWarningCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    iconRes: Int = if (isError) BaseR.drawable.error_24px else BaseR.drawable.info_24px,
) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val iconTint = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * Compact HUD: search is an icon until opened; filters are 48dp icons with long-press labels.
 */
@Composable
fun TelosSearchAndFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    activeFilter: SharedAppFilter,
    onFilterChange: (SharedAppFilter) -> Unit,
    modifier: Modifier = Modifier,
    availableFilters: List<SharedAppFilter> = SharedAppFilter.entries,
    placeholder: String = stringResource(I18nR.string.art_optimizer_search_apps_hint),
) {
    val haptics = LocalHapticFeedback.current
    TelosCommandHud(
        modifier = modifier,
        searchQuery = query,
        onSearchQueryChange = onQueryChange,
        searchPlaceholder = placeholder,
        actions = availableFilters.map { filter ->
            TelosHudAction(
                id = filter.name,
                icon = when (filter) {
                    SharedAppFilter.ALL -> BaseR.drawable.apps_24px
                    SharedAppFilter.USER -> BaseR.drawable.check_circle_24px
                    SharedAppFilter.SYSTEM -> BaseR.drawable.filter_alt_24px
                    SharedAppFilter.FROZEN -> BaseR.drawable.visibility_off_24px
                    SharedAppFilter.NEEDS_ATTENTION -> BaseR.drawable.warning_24px
                },
                label = stringResource(filter.labelRes),
                selected = activeFilter == filter,
                accent = if (filter == SharedAppFilter.NEEDS_ATTENTION) TelosCyber.NeonOrange else TelosCyber.NeonCyan,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onFilterChange(filter)
                },
            )
        },
    )
}
