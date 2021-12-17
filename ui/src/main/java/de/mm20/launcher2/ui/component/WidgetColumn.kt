package de.mm20.launcher2.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.ui.ClockWidget
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalWindowSize
import de.mm20.launcher2.ui.widget.WidgetCard
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetViewModel
import org.koin.androidx.compose.getViewModel

@OptIn(ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class, ExperimentalAnimationGraphicsApi::class
)
@Composable
fun WidgetColumn(
    modifier: Modifier = Modifier,
    scrollState: ScrollState
) {
    val systemUiController = rememberSystemUiController()

    var widgets by remember { mutableStateOf(listOf<Widget>()) }

    val viewModel: WidgetViewModel = getViewModel()

    var editMode by remember { mutableStateOf(false) }

    LaunchedEffect(null) {
        widgets = viewModel.getWidgets()
    }

    val isLightTheme = androidx.compose.material.MaterialTheme.colors.isLight

    val windowHeight = LocalWindowSize.current.height

    val background = 1f - (scrollState.value * 2 / windowHeight).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    background to Color.Transparent,
                    background to MaterialTheme.colorScheme.background
                )
            )
    ) {
        Column(
            Modifier
                .padding(horizontal = 8.dp)
                .verticalScroll(scrollState)
                .navigationBarsPadding()
        ) {
            ClockWidget(transparentBackground = background > 0.75f)

            AnimatedVisibility(visible = scrollState.value == 0) {
                NavBarSpacer()
            }

            for (widget in widgets) {
                WidgetCard(widget = widget)
            }

            val icon = AnimatedImageVector.animatedVectorResource(id = R.drawable.anim_ic_edit_add)
            ExtendedFloatingActionButton(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.CenterHorizontally),
                text = {
                    Text(
                        modifier = Modifier.animateContentSize(),
                        text = stringResource(if (editMode) R.string.widget_add_widget else R.string.menu_edit_widgets)
                    )
                },
                icon = {
                    Icon(painter = rememberAnimatedVectorPainter(icon, atEnd = editMode), contentDescription = null)
                },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onClick = {
                    editMode = !editMode
                })
        }
    }
}