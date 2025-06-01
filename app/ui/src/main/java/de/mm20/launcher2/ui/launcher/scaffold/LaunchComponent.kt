package de.mm20.launcher2.ui.launcher.scaffold

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityOptionsCompat
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.component.FakeSplashScreen
import de.mm20.launcher2.ui.ktx.toIntOffset

internal class LaunchComponent(
    private val activity: Activity,
    private val searchable: SavableSearchable,
): ScaffoldComponent() {
    override val permanent: Boolean = false
    override val resetDelay: Long = 500L
    override val showSearchBar = false

    override val isAtTop: State<Boolean?> = mutableStateOf(true)
    override val isAtBottom: State<Boolean?> = mutableStateOf(true)

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        FakeSplashScreen(
            modifier = modifier.zIndex(10f).alpha((state.currentProgress * 2f).coerceAtMost(1f)),
            searchable = searchable,
        )
    }

    override suspend fun onActivate(state: LauncherScaffoldState) {
        super.onActivate(state)
        val view = activity.window.decorView
        val options = ActivityOptionsCompat.makeClipRevealAnimation(
            view,
            0,
            0,
            view.width,
            view.height
        )

        searchable.launch(activity, options.toBundle())
    }

    override fun homePageModifier(
        state: LauncherScaffoldState,
        defaultModifier: Modifier
    ): Modifier {
        return defaultModifier.alpha(1f - state.currentProgress)
    }

    @SuppressLint("ModifierFactoryExtensionFunction")
    override fun searchBarModifier(
        state: LauncherScaffoldState,
        defaultModifier: Modifier
    ): Modifier {
        return defaultModifier.offset{ state.currentOffset.toIntOffset() }.alpha(1f - state.currentProgress)
    }

}