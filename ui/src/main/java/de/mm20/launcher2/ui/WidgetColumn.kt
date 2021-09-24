package de.mm20.launcher2.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
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
import de.mm20.launcher2.ui.locals.LocalWindowSize
import de.mm20.launcher2.ui.component.NavBarSpacer
import de.mm20.launcher2.ui.widget.WidgetCard
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetViewModel

@OptIn(ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class, ExperimentalAnimationGraphicsApi::class
)
@Composable
fun WidgetColumn(
    modifier: Modifier = Modifier,
    scrollState: ScrollState
) {
    val systemUiController = rememberSystemUiController()

    var widgets by remember { mutableStateOf(listOf<Widget>()) }

    val viewModel: WidgetViewModel = viewModel()

    var editMode by remember { mutableStateOf(false) }

    LaunchedEffect(null) {
        widgets = viewModel.getWidgets()
    }

    val isLightTheme = MaterialTheme.colors.isLight

    val windowHeight = LocalWindowSize.current.height

    val background = 1f - (scrollState.value * 2 / windowHeight).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    background to Color.Transparent,
                    background to MaterialTheme.colors.background
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

            val icon = animatedVectorResource(id = R.drawable.anim_ic_edit_add)
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
                    Icon(painter = icon.painterFor(atEnd = editMode), contentDescription = null)
                },
                backgroundColor = MaterialTheme.colors.surface,
                onClick = {
                    editMode = !editMode
                })
        }
    }
}