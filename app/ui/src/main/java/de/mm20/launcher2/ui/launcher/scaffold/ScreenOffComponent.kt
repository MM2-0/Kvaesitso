package de.mm20.launcher2.ui.launcher.scaffold

import android.annotation.SuppressLint
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.globalactions.GlobalActionsService
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal object ScreenOffComponent : ScaffoldComponent(), KoinComponent {

    private val permissionsManager: PermissionsManager by inject()
    private val globalActionService: GlobalActionsService by inject()

    override val permanent: Boolean
        get() = !permissionsManager.checkPermissionOnce(PermissionGroup.Accessibility)

    override val showSearchBar: Boolean = false

    override val resetDelay: Long = 1000L

    override val drawBackground: Boolean = false

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        if (mounted) {
            val bottomSheetManager = LocalBottomSheetManager.current
            LaunchedEffect(Unit) {
                val gesture = state.currentGesture ?: return@LaunchedEffect
                if (!permissionsManager.checkPermissionOnce(PermissionGroup.Accessibility)) {
                    bottomSheetManager.showFailedGestureSheet(
                        gesture = gesture,
                        action = GestureAction.ScreenLock,
                    )
                }
            }
        }
        Box(
            modifier = modifier
                .zIndex(10f)
                .pointerInput(Unit) {},
            contentAlignment = Alignment.Center
        ) {
        }
    }

    @SuppressLint("ModifierFactoryExtensionFunction")
    override fun homePageModifier(
        state: LauncherScaffoldState,
        defaultModifier: Modifier
    ): Modifier {
        return Modifier
            .scale(1f - (state.currentProgress * 0.1f))
            .blur(12.dp * state.currentProgress)
            .alpha(1f - (state.currentProgress * 0.1f))
    }

    @SuppressLint("ModifierFactoryExtensionFunction")
    override fun searchBarModifier(
        state: LauncherScaffoldState,
        defaultModifier: Modifier
    ): Modifier {
        return Modifier
            .drawWithContent {
                drawContent()
                drawRect(Color.Black, alpha = state.currentProgress)
            }
            .scale(1f - (state.currentProgress * 0.1f))
            .blur(12.dp * state.currentProgress)
            .alpha(1f - (state.currentProgress * 0.1f)) then defaultModifier
    }

    override suspend fun onMount(state: LauncherScaffoldState) {
        super.onMount(state)
        if (permissionsManager.checkPermissionOnce(PermissionGroup.Accessibility)) {
            globalActionService.lockScreen()
        } else {
            state.onPredictiveBackEnd()
        }
    }
}