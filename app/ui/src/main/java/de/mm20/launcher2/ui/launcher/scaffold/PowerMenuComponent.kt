package de.mm20.launcher2.ui.launcher.scaffold

import android.annotation.SuppressLint
import android.view.Surface
import android.view.animation.PathInterpolator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex
import de.mm20.launcher2.globalactions.GlobalActionsService
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal object PowerMenuComponent : ScaffoldComponent(), KoinComponent {

    private val permissionsManager: PermissionsManager by inject()
    private val globalActionService: GlobalActionsService by inject()

    override val permanent: Boolean
        get() = !permissionsManager.checkPermissionOnce(PermissionGroup.Accessibility)

    override val showSearchBar: Boolean = false

    override val drawBackground: Boolean = false

    private val interpolator = PathInterpolator(0f, 0f, 0f, 1f)

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        val context = LocalContext.current
        val view = LocalView.current
        val rotation = view.display.rotation

        val powerButtonY = remember {
            val resources =
                context.packageManager.getResourcesForApplication("com.android.systemui")
            val resId = resources.getIdentifier(
                "physical_power_button_center_screen_location_y",
                "dimen",
                "com.android.systemui"
            )

            if (resId != 0) {
                resources.getDimensionPixelSize(resId)
            } else {
                (view.height * 0.33f).toInt()
            }
        }

        if (isActive) {
            val bottomSheetManager = LocalBottomSheetManager.current
            LaunchedEffect(Unit) {
                val gesture = state.currentGesture ?: return@LaunchedEffect
                if (!permissionsManager.checkPermissionOnce(PermissionGroup.Accessibility)) {
                    bottomSheetManager.showFailedGestureSheet(
                        gesture = gesture,
                        action = GestureAction.PowerMenu,
                    )
                }
            }
        }

        val scale by animateFloatAsState(
            if (state.currentProgress >= 0.5f) 1.2f else 1f
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(99f)
                .pointerInput(Unit) {},
            contentAlignment = when (rotation) {
                Surface.ROTATION_0 -> AbsoluteAlignment.TopRight
                Surface.ROTATION_90 -> AbsoluteAlignment.TopLeft
                Surface.ROTATION_180 -> AbsoluteAlignment.BottomLeft
                Surface.ROTATION_270 -> AbsoluteAlignment.BottomRight
                else -> AbsoluteAlignment.TopRight
            }
        ) {
            Box(
                modifier = Modifier
                    .offset {
                        when (rotation) {
                            Surface.ROTATION_0 -> IntOffset(0, powerButtonY)
                            Surface.ROTATION_90 -> IntOffset(powerButtonY, 0)
                            Surface.ROTATION_180 -> IntOffset(0, -powerButtonY)
                            Surface.ROTATION_270 -> IntOffset(-powerButtonY, 0)
                            else -> IntOffset(0, powerButtonY)
                        }
                    }
                    .offset(
                        x = when (rotation) {
                            Surface.ROTATION_90 -> -48.dp
                            Surface.ROTATION_270 -> 48.dp
                            else -> 0.dp
                        },
                        y =
                            when (rotation) {
                                Surface.ROTATION_0 -> -48.dp
                                Surface.ROTATION_180 -> 48.dp
                                else -> 0.dp
                            },
                    )
                    .padding(16.dp)
                    .size(64.dp)
                    .offset(
                        x = when (rotation) {
                            Surface.ROTATION_0 -> 1
                            Surface.ROTATION_180 -> -1
                            else -> 0
                        } * 128.dp * interpolator.getInterpolation(1f - state.currentProgress * 2f)
                            .coerceAtLeast(0f),
                        y = when (rotation) {
                            Surface.ROTATION_90 -> -1
                            Surface.ROTATION_270 -> 1
                            else -> 0
                        } * 128.dp * interpolator.getInterpolation(1f - state.currentProgress * 2f)
                            .coerceAtLeast(0f),
                    )
                    .scale(scale)
                    .shadow(4.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.power_settings_new_24px), null,
                    modifier = Modifier.padding(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }

    override suspend fun onPreActivate(state: LauncherScaffoldState) {
        super.onPreActivate(state)
        if (permissionsManager.checkPermissionOnce(PermissionGroup.Accessibility)) {
            globalActionService.openPowerDialog()
        }
    }

    override suspend fun onActivate(state: LauncherScaffoldState) {
        super.onActivate(state)
        if (!permissionsManager.checkPermissionOnce(PermissionGroup.Accessibility)) {
            state.navigateBack(true)
        }
    }

    @SuppressLint("ModifierFactoryExtensionFunction")
    override fun homePageModifier(
        state: LauncherScaffoldState,
        defaultModifier: Modifier
    ): Modifier {
        return Modifier
    }

    @SuppressLint("ModifierFactoryExtensionFunction")
    override fun searchBarModifier(
        state: LauncherScaffoldState,
        defaultModifier: Modifier
    ): Modifier {
        return defaultModifier.composed {
            val color = MaterialTheme.colorScheme.scrim
            Modifier.drawWithContent {
                drawContent()
                drawRect(
                    color = color.copy(alpha = 0.5f * (state.currentProgress * 2f).coerceAtMost(1f))
                )
            }
        }
    }
}