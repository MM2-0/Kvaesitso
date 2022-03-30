package de.mm20.launcher2.ui.launcher.modals

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideIn
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.mm20.launcher2.ui.MdcLauncherTheme
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.ProvideSettings
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.grid.SearchResultGrid
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.material.ExperimentalMaterialApi::class)
class HiddenItemsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val viewModel: HiddenItemsVM by (context as AppCompatActivity).viewModels()

    init {
        val composeView = ComposeView(context)

        composeView.setContent {
            MdcLauncherTheme {
                ProvideSettings {
                    Dialog(
                        properties = DialogProperties(usePlatformDefaultWidth = false),
                        onDismissRequest = { onDismiss() }) {
                        val animationState = remember {
                            MutableTransitionState(false).apply {
                                targetState = true
                            }
                        }

                        AnimatedVisibility(
                            animationState,
                            enter = slideIn { IntOffset(0, it.height) }
                        ) {
                            val swipeState =
                                rememberSwipeableState(initialValue = SwipeState.Default) {
                                    if (it == SwipeState.Dismiss) onDismiss()
                                    return@rememberSwipeableState true
                                }
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .swipeable(
                                        swipeState,
                                        mapOf(
                                            0f to SwipeState.Default,
                                            600.dp.toPixels() to SwipeState.Dismiss
                                        ),
                                        orientation = Orientation.Vertical,
                                        thresholds = { _, _ -> FractionalThreshold(0.5f) },
                                    )
                                    .offset { IntOffset(0, swipeState.offset.value.roundToInt()) }

                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                ) {
                                    Text(
                                        stringResource(R.string.menu_hidden_items),
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(24.dp)

                                    )
                                    val items by viewModel.hiddenItems.collectAsState(emptyList())
                                    SearchResultGrid(
                                        items,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(8.dp)
                                            .verticalScroll(rememberScrollState())
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        TextButton(onClick = { onDismiss() }) {
                                            Text(
                                                stringResource(id = R.string.close),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            }
        }

        addView(composeView)
    }

    var onDismiss: () -> Unit = {}
}

private enum class SwipeState {
    Default, Dismiss
}