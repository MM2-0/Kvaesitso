package de.mm20.launcher2.ui.launcher.search.shortcut


import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.AppShortcut
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.*
import de.mm20.launcher2.ui.ktx.toDp
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.modifier.scale
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AppItem(
    modifier: Modifier = Modifier,
    shortcut: AppShortcut,
    onBack: () -> Unit
) {
    val viewModel = remember { ShortcutItemVM(shortcut) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier
    ) {
        Row {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Text(text = shortcut.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.shortcut_summary, shortcut.appName),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val badge by viewModel.badge.collectAsState(null)
            val iconSize = 84.dp.toPixels().toInt()
            val icon by remember(shortcut.key) { viewModel.getIcon(iconSize) }.collectAsState(null)
            ShapedLauncherIcon(
                size = 84.dp,
                modifier = Modifier
                    .padding(16.dp),
                badge = badge,
                icon = icon,
            )
        }

        val toolbarActions = mutableListOf<ToolbarAction>()

        if (LocalFavoritesEnabled.current) {
            val isPinned by viewModel.isPinned.collectAsState(false)
            val favAction = if (isPinned) {
                DefaultToolbarAction(
                    label = stringResource(R.string.favorites_menu_unpin),
                    icon = Icons.Rounded.Star,
                    action = {
                        viewModel.unpin()
                    }
                )
            } else {
                DefaultToolbarAction(
                    label = stringResource(R.string.favorites_menu_pin),
                    icon = Icons.Rounded.StarOutline,
                    action = {
                        viewModel.pin()
                    })
            }
            toolbarActions.add(favAction)
        }

        toolbarActions.add(
            DefaultToolbarAction(
                label = stringResource(R.string.menu_app_info),
                icon = Icons.Rounded.Info
            ) {
                viewModel.openAppInfo(context as AppCompatActivity)
            })

        Toolbar(
            leftActions = listOf(
                DefaultToolbarAction(
                    label = stringResource(id = R.string.menu_back),
                    icon = Icons.Rounded.ArrowBack
                ) {
                    onBack()
                }
            ),
            rightActions = toolbarActions
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ShortcutItemGridPopup(
    shortcut: AppShortcut,
    show: Boolean,
    animationProgress: Float,
    origin: Rect,
    onDismiss: () -> Unit
) {
    AnimatedContent(
        targetState = show,
        transitionSpec = {
            slideInHorizontally(
                tween(300),
                initialOffsetX = { -it + origin.width.roundToInt() }) with
                    slideOutHorizontally(
                        tween(300),
                        targetOffsetX = { -it + origin.width.roundToInt() }) + fadeOut(snap(400)) using
                    SizeTransform { _, _ ->
                        tween(300)
                    }
        }
    ) { targetState ->
        if (targetState) {
            AppItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(
                        1 - (1 - 48.dp / 84.dp) * (1 - animationProgress),
                        transformOrigin = TransformOrigin(1f, 0f)
                    )
                    .offset(
                        x = 16.dp * (1 - animationProgress).pow(10),
                        y = -16.dp * (1 - animationProgress),
                    ),
                shortcut = shortcut,
                onBack = onDismiss
            )
        } else {
            Box(
                modifier = Modifier
                    .requiredWidth(origin.width.toDp())
                    .requiredHeight(origin.height.toDp())
            )
        }
    }
}