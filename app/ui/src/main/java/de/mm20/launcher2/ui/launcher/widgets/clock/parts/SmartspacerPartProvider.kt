package de.mm20.launcher2.ui.launcher.widgets.clock.parts

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kieronquinn.app.smartspacer.sdk.client.SmartspacerClient
import com.kieronquinn.app.smartspacer.sdk.client.views.BcSmartspaceView
import com.kieronquinn.app.smartspacer.sdk.client.views.popup.Popup
import com.kieronquinn.app.smartspacer.sdk.client.views.popup.PopupFactory
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.flow.flow

@RequiresApi(29)
class SmartspacerPartProvider : PartProvider {
    override fun getRanking(context: Context) = flow {
        emit(Int.MAX_VALUE)
    }

    @Composable
    override fun Component(compactLayout: Boolean) {

        val contentColor = LocalContentColor.current

        val popupFactory = remember {
            ComposePopupFactory()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                popupFactory.Popup()
            }
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = if (compactLayout) 0.dp else 12.dp)
                    .height(104.dp),
                factory = {
                    val view = LayoutInflater
                        .from(it)
                        .inflate(R.layout.smartspacer, null, false) as BcSmartspaceView

                    view.setApplyShadowIfRequired(false)
                    view.setTintColour(contentColor.toArgb())
                    view.popupFactory = popupFactory

                    view
                },
                update = { view ->
                    view.setTintColour(contentColor.toArgb())
                },
                onRelease = {
                    SmartspacerClient.close()
                }
            )
        }

    }

    private data class PopupState(
        val target: SmartspaceTarget,
        val launchIntent: (Intent?) -> Unit,
        val dismissAction: ((SmartspaceTarget) -> Unit)?,
        val aboutIntent: Intent?,
        val feedbackIntent: Intent?,
        val settingsIntent: Intent?
    )

    private class ComposePopupFactory : PopupFactory {
        var state by mutableStateOf<PopupState?>(null)

        override fun createPopup(
            context: Context,
            anchorView: View,
            target: SmartspaceTarget,
            backgroundColor: Int,
            textColour: Int,
            launchIntent: (Intent?) -> Unit,
            dismissAction: ((SmartspaceTarget) -> Unit)?,
            aboutIntent: Intent?,
            feedbackIntent: Intent?,
            settingsIntent: Intent?
        ): Popup {
            state = PopupState(
                target = target,
                launchIntent = launchIntent,
                dismissAction = dismissAction,
                aboutIntent = aboutIntent,
                feedbackIntent = feedbackIntent,
                settingsIntent = settingsIntent
            )
            return object : Popup {
                override fun dismiss() {
                    state = null
                }

            }
        }

        @Composable
        fun Popup() {
            val state = state
            if (state != null) {
                DropdownMenuPopup(
                    expanded = true,
                    onDismissRequest = {
                        this.state = null
                    }
                ) {

                    val itemCount = (if (state.settingsIntent != null) 1 else 0) +
                            (if (state.feedbackIntent != null) 1 else 0) +
                            (if (state.aboutIntent != null) 1 else 0) +
                            (if (state.dismissAction != null) 1 else 0)

                    var item = 0


                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShapes(),
                    ) {
                        if (state.settingsIntent != null) {
                            DropdownMenuItem(
                                shape = when {
                                    item == 0 && itemCount - 1 == item -> MenuDefaults.standaloneItemShape
                                    item == 0 -> MenuDefaults.leadingItemShape
                                    item == itemCount - 1 -> MenuDefaults.trailingItemShape
                                    else -> MenuDefaults.middleItemShape
                                },
                                text = {
                                    Text(stringResource(R.string.smartspace_long_press_popup_settings))
                                },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.settings_24px),
                                        null,
                                    )
                                },
                                onClick = {
                                    state.launchIntent(state.settingsIntent)
                                }
                            )

                            item++
                        }

                        if (state.feedbackIntent != null) {
                            DropdownMenuItem(
                                shape = when {
                                    item == 0 && itemCount - 1 == item -> MenuDefaults.standaloneItemShape
                                    item == 0 -> MenuDefaults.leadingItemShape
                                    item == itemCount - 1 -> MenuDefaults.trailingItemShape
                                    else -> MenuDefaults.middleItemShape
                                },
                                text = {
                                    Text(stringResource(R.string.smartspace_long_press_popup_feedback))
                                },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.feedback_24px),
                                        null,
                                    )
                                },
                                onClick = {
                                    state.launchIntent(state.feedbackIntent)
                                }
                            )

                            item++
                        }

                        if (state.aboutIntent != null) {
                            DropdownMenuItem(
                                shape = when {
                                    item == 0 && itemCount - 1 == item -> MenuDefaults.standaloneItemShape
                                    item == 0 -> MenuDefaults.leadingItemShape
                                    item == itemCount - 1 -> MenuDefaults.trailingItemShape
                                    else -> MenuDefaults.middleItemShape
                                },
                                text = {
                                    Text(stringResource(R.string.smartspace_long_press_popup_about))
                                },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.info_24px),
                                        null,
                                    )
                                },
                                onClick = {
                                    state.launchIntent(state.aboutIntent)
                                }
                            )

                            item++
                        }

                        if (state.dismissAction != null) {
                            DropdownMenuItem(
                                shape = when {
                                    item == 0 && itemCount - 1 == item -> MenuDefaults.standaloneItemShape
                                    item == 0 -> MenuDefaults.leadingItemShape
                                    item == itemCount - 1 -> MenuDefaults.trailingItemShape
                                    else -> MenuDefaults.middleItemShape
                                },
                                text = {
                                    Text(stringResource(R.string.smartspace_long_press_popup_dismiss))
                                },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.close_24px),
                                        null,
                                    )
                                },
                                onClick = {
                                    state.dismissAction(state.target)
                                }
                            )

                            item++
                        }
                    }
                }
            }
        }
    }
}