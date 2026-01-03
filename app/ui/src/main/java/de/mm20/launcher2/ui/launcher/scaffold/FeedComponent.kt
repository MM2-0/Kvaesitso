package de.mm20.launcher2.ui.launcher.scaffold

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.mm20.launcher2.feed.FeedConnection
import de.mm20.launcher2.feed.FeedService
import de.mm20.launcher2.preferences.feed.FeedSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.ktx.toIntOffset
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class FeedComponent(
    private val context: Context,
) : ScaffoldComponent(), KoinComponent {

    private val feedSettings: FeedSettings by inject()
    private val feedService: FeedService by inject()

    override val permanent: Boolean = true

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        val activity = LocalActivity.current
        val lifecycleOwner = LocalLifecycleOwner.current

        val progress = state.currentProgress
        val feedProgress = remember { mutableFloatStateOf(0f) }

        val feedProviderPackage by remember { feedSettings.providerPackage }.collectAsState(null)

        var feedConnection by remember { mutableStateOf<FeedConnection?>(null) }
        val feedReady = feedConnection?.ready?.collectAsState(false)
        val feedAvailable = feedConnection?.available?.collectAsState(null)


        DisposableEffect(feedProviderPackage) {
            val conn = feedProviderPackage?.let {
                feedService.createFeedInstance(activity as AppCompatActivity, it) { p ->
                    feedProgress.floatValue = p
                }
            }
            feedConnection = conn

            onDispose {
                conn?.destroy()
            }
        }

        val enableScroll = state.currentComponent == this && progress > 0f && progress < 1f

        LaunchedEffect(enableScroll) {
            if (enableScroll) {
                feedConnection?.startScroll()
            } else {
                feedConnection?.endScroll()
            }
        }

        LaunchedEffect(progress) {
            feedConnection?.onScroll(progress)
        }

        LaunchedEffect(feedProgress.floatValue) {
            if (isActive) {
                state.setProgress(feedProgress.floatValue)
                if (feedProgress.floatValue <= 0f) {
                    state.onPredictiveBackEnd()
                }
            }
        }

        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (feedAvailable?.value == false) {
                Icon(
                    painterResource(R.drawable.error_48px),
                    null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    "Feed could not be loaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    @SuppressLint("ModifierFactoryExtensionFunction")
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