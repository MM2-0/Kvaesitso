package de.mm20.launcher2.ui.launcher.scaffold.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityOptionsCompat
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.ktx.toIntOffset
import de.mm20.launcher2.ui.launcher.scaffold.LauncherScaffoldState
import de.mm20.launcher2.ui.settings.SettingsActivity
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

internal class LauncherSettingsComponent(
    private val activity: Activity,
) : ScaffoldComponent() {
    override val showSearchBar = false

    override val isAtTop: State<Boolean?> = mutableStateOf(true)
    override val isAtBottom: State<Boolean?> = mutableStateOf(true)

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {

        Scaffold(
            modifier = modifier
                .zIndex(10f)
                .alpha((state.currentProgress * 2f).coerceAtMost(1f)),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(stringResource(R.string.settings))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back_24px),
                                contentDescription = "Back"
                            )
                        }
                    },
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState(), enabled = false)
                    .padding(it)
                    .padding(12.dp),
            ) {
                PreferenceCategory {
                    for (i in 0 until 11) {
                        Preference(
                            title = "",
                            summary = "",
                        )
                    }
                }
            }
        }
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

        val intent = Intent(activity, SettingsActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent, options.toBundle())

        delay(500.milliseconds)
        state.reset()
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
        return defaultModifier
            .offset { state.currentOffset.toIntOffset() }
            .alpha(1f - state.currentProgress)
    }

}