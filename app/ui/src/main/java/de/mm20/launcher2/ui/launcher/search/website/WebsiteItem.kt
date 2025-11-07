package de.mm20.launcher2.ui.launcher.search.website

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import de.mm20.launcher2.search.Website
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.component.ToolbarAction
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalGridSettings

@Composable
fun WebsiteItem(
    modifier: Modifier = Modifier,
    website: Website,
    showDetails: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val viewModel: SearchableItemVM = listItemViewModel(key = "search-${website.key}")
    val iconSize = LocalGridSettings.current.iconSize.dp.toPixels()

    LaunchedEffect(website, iconSize) {
        viewModel.init(website, iconSize.toInt())
    }

    SharedTransitionScope {
        AnimatedContent(
            showDetails,
            modifier = it then modifier,
        ) { showDetails ->
            Column {
                if (!showDetails) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = if (website.imageUrl == null && website.description == null) Alignment.CenterVertically
                        else Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = website.labelOverride ?: website.label,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .sharedBounds(
                                        rememberSharedContentState("title"),
                                        this@AnimatedContent,
                                    ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Text(
                                modifier = Modifier
                                    .padding(top = 4.dp, bottom = 4.dp)
                                    .sharedBounds(
                                        rememberSharedContentState("summary"),
                                        this@AnimatedContent,
                                    ),
                                text = website.description ?: website.url,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (!website.imageUrl.isNullOrEmpty()) {
                            AsyncImage(
                                modifier = Modifier
                                    .padding(end = 12.dp, top = 12.dp, bottom = 12.dp)
                                    .size(72.dp)
                                    .sharedBounds(
                                        rememberSharedContentState("image"),
                                        this@AnimatedContent,
                                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
                                    )
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        MaterialTheme.shapes.small
                                    )
                                    .clip(MaterialTheme.shapes.small),
                                model = website.imageUrl,
                                contentScale = ContentScale.Crop,
                                contentDescription = null
                            )
                        } else if (website.faviconUrl != null) {
                            AsyncImage(
                                modifier = Modifier
                                    .padding(end = 16.dp, top = 12.dp, bottom = 12.dp)
                                    .sharedElement(
                                        rememberSharedContentState("favicon"),
                                        this@AnimatedContent,
                                    )
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        MaterialTheme.shapes.small
                                    )
                                    .size(48.dp)
                                    .padding(8.dp)
                                    .clip(MaterialTheme.shapes.small),
                                model = website.faviconUrl,
                                contentScale = ContentScale.Crop,
                                contentDescription = null
                            )
                        }
                    }
                } else {
                    if (!website.imageUrl.isNullOrEmpty()) {
                        AsyncImage(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .sharedBounds(
                                    rememberSharedContentState("image"),
                                    this@AnimatedContent,
                                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
                                )
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            model = ImageRequest.Builder(context).data(website.imageUrl)
                                .crossfade(false).build(),
                            contentScale = ContentScale.Crop,
                            contentDescription = null
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                )
                        ) {
                            Text(
                                text = website.labelOverride ?: website.label,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.sharedBounds(
                                    rememberSharedContentState("title"),
                                    this@AnimatedContent,
                                ),
                            )
                        }
                        if (website.imageUrl == null && website.faviconUrl != null) {
                            AsyncImage(
                                modifier = Modifier
                                    .padding(end = 12.dp, top = 12.dp, bottom = 12.dp)
                                    .sharedElement(
                                        rememberSharedContentState("favicon"),
                                        this@AnimatedContent,
                                    )
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        MaterialTheme.shapes.small
                                    )
                                    .size(48.dp)
                                    .padding(8.dp)
                                    .clip(MaterialTheme.shapes.small),
                                model = website.faviconUrl,
                                contentScale = ContentScale.Crop,
                                contentDescription = null
                            )
                        }
                    }

                    Text(
                        modifier = Modifier
                            .padding(16.dp)
                            .sharedBounds(
                                rememberSharedContentState("summary"),
                                this@AnimatedContent,
                            ),
                        text = website.description ?: website.url,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    val toolbarActions = mutableListOf<ToolbarAction>()

                    if (LocalFavoritesEnabled.current) {
                        val isPinned by viewModel.isPinned.collectAsState(false)
                        val favAction = if (isPinned) {
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_favorites_unpin),
                                icon = R.drawable.star_24px_filled,
                                action = {
                                    viewModel.unpin()
                                    onBack?.invoke()
                                }
                            )
                        } else {
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_favorites_pin),
                                icon = R.drawable.star_24px,
                                action = {
                                    viewModel.pin()
                                    onBack?.invoke()
                                })
                        }
                        toolbarActions.add(favAction)
                    }

                    toolbarActions.add(
                        DefaultToolbarAction(
                            label = stringResource(R.string.menu_share),
                            icon = R.drawable.share_24px,
                            action = {
                                website.share(context)
                            }
                        )
                    )

                    val sheetManager = LocalBottomSheetManager.current
                    toolbarActions.add(
                        DefaultToolbarAction(
                        label = stringResource(R.string.menu_customize),
                        icon = R.drawable.tune_24px,
                        action = { sheetManager.showCustomizeSearchableModal(website) }
                    ))

                    Toolbar(
                        leftActions = if (onBack != null) listOf(
                            DefaultToolbarAction(
                                stringResource(id = R.string.menu_back),
                                icon = R.drawable.arrow_back_24px,
                                action = onBack
                            )
                        ) else emptyList(),
                        rightActions = toolbarActions
                    )
                }
            }
        }
    }
}

@Composable
fun WebsiteItemGridPopup(
    website: Website,
    show: MutableTransitionState<Boolean>,
    animationProgress: Float,
    origin: IntRect,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        show,
        enter = expandIn(
            animationSpec = tween(300),
            expandFrom = Alignment.Center,
        ) { origin.size },
        exit = shrinkOut(
            animationSpec = tween(300),
            shrinkTowards = Alignment.Center,
        ) { origin.size },
    ) {
        WebsiteItem(
            modifier = Modifier
                .fillMaxWidth(),
            website = website,
            onBack = onDismiss,
            showDetails = true,
        )
    }
}