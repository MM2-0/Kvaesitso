package de.mm20.launcher2.ui.launcher.scaffold

import android.annotation.SuppressLint
import android.view.RoundedCorner
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import de.mm20.launcher2.globalactions.GlobalActionsService
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import kotlinx.coroutines.delay
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal object RecentsComponent : ScaffoldComponent(), KoinComponent {

    private val permissionsManager: PermissionsManager by inject()
    private val globalActionService: GlobalActionsService by inject()

    override val permanent: Boolean
        get() = !permissionsManager.checkPermissionOnce(PermissionGroup.Accessibility)

    override val showSearchBar: Boolean = false

    override val drawBackground: Boolean = false

    override val isAtTop: State<Boolean?> = mutableStateOf(true)
    override val isAtBottom: State<Boolean?> = mutableStateOf(true)

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        if (isActive) {
            val bottomSheetManager = LocalBottomSheetManager.current
            LaunchedEffect(Unit) {
                val gesture = state.currentGesture ?: return@LaunchedEffect
                if (!permissionsManager.checkPermissionOnce(PermissionGroup.Accessibility)) {
                    bottomSheetManager.showFailedGestureSheet(
                        gesture = gesture,
                        action = GestureAction.Recents,
                    )
                }
            }
        }
    }

    override suspend fun onPreActivate(state: LauncherScaffoldState) {
        super.onPreActivate(state)
        if (permissionsManager.checkPermissionOnce(PermissionGroup.Accessibility)) {
            globalActionService.openRecents()
        }
    }

    override suspend fun onActivate(state: LauncherScaffoldState) {
        super.onActivate(state)
        state.navigateBack(true)
    }

    @SuppressLint("ModifierFactoryExtensionFunction")
    override fun homePageModifier(
        state: LauncherScaffoldState,
        defaultModifier: Modifier
    ): Modifier = Modifier.composed {
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val insets = LocalView.current.rootWindowInsets
        val shape = if (isAtLeastApiLevel(31) && insets != null) {
            RoundedCornerShape(
                topStart = insets.getRoundedCorner(if (rtl) RoundedCorner.POSITION_TOP_RIGHT else RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat()
                    ?: 0f,
                topEnd = insets.getRoundedCorner(if (rtl) RoundedCorner.POSITION_TOP_LEFT else RoundedCorner.POSITION_TOP_RIGHT)?.radius?.toFloat()
                    ?: 0f,
                bottomStart = insets.getRoundedCorner(if (rtl) RoundedCorner.POSITION_BOTTOM_RIGHT else RoundedCorner.POSITION_BOTTOM_LEFT)?.radius?.toFloat()
                    ?: 0f,
                bottomEnd = insets.getRoundedCorner(if (rtl) RoundedCorner.POSITION_BOTTOM_LEFT else RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius?.toFloat()
                    ?: 0f,
            )
        } else {
            RectangleShape
        }

        val surfaceColor = MaterialTheme.colorScheme.surfaceContainer

        Modifier
            .drawWithContent() {
                drawRect(color = surfaceColor.copy(alpha = state.currentProgress * 0.5f))
                drawIntoCanvas {
                    withTransform({
                        this.scale(1f - state.currentProgress * 0.3f)
                    }) {
                        drawOutline(
                            shape.createOutline(size, layoutDirection, Density(density, fontScale)),
                            color = Color.Black,
                            blendMode = BlendMode.Clear
                        )
                        this@drawWithContent.drawContent()
                    }
                }
            }
    }

    @SuppressLint("ModifierFactoryExtensionFunction")
    override fun searchBarModifier(
        state: LauncherScaffoldState,
        defaultModifier: Modifier
    ): Modifier {
        return defaultModifier.scale(1f - state.currentProgress * 0.3f)
    }
}