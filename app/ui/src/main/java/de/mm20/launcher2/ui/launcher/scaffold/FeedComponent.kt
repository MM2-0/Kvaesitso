package de.mm20.launcher2.ui.launcher.scaffold

import android.annotation.SuppressLint
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.feed.FeedConnection
import de.mm20.launcher2.feed.FeedService
import de.mm20.launcher2.preferences.feed.FeedSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.ktx.toIntOffset
import kotlinx.coroutines.flow.flowOf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal object FeedComponent : ScaffoldComponent(), KoinComponent {

    private val feedSettings: FeedSettings by inject()
    private val feedService: FeedService by inject()

    override val permanent: Boolean = true

    override val survivesPause: Boolean = true

    private var state = mutableIntStateOf(0)

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        val activity = LocalActivity.current

        val feedProviderPackage by remember { feedSettings.providerPackage }.collectAsState(null)

        var feedConnection by remember { mutableStateOf<FeedConnection?>(null) }

        val feedReady by remember(feedConnection) {
            feedConnection?.ready ?: flowOf(false)
        }.collectAsState(false)

        val feedAvailable by remember(feedConnection) {
            feedConnection?.available ?: flowOf(false)
        }.collectAsState(false)


        val feedHasContent by remember(feedConnection) {
            feedConnection?.hasContent ?: flowOf(false)
        }.collectAsState(false)

        val feedProgress by remember(feedConnection) {
            feedConnection?.scrollProgress ?: flowOf(0f)
        }.collectAsState(0f)

        val progress = state.currentProgress

        DisposableEffect(feedProviderPackage) {
            val conn = feedProviderPackage?.let {
                feedService.createFeedInstance(activity as AppCompatActivity, it)
            }
            feedConnection = conn

            onDispose {
                conn?.destroy()
            }
        }

        if (feedHasContent && feedReady && feedAvailable) {
            if (state.currentComponent == this) {
                LaunchedEffect(Unit) {
                    feedConnection?.startScroll()
                }


                DisposableEffect(Unit) {
                    onDispose {
                        feedConnection?.onScroll(0f)
                        feedConnection?.endScroll()
                    }
                }

                if (isActive) {
                    SideEffect {
                        state.setProgress(feedProgress)
                    }

                    if (feedProgress <= 0) {
                        LaunchedEffect(Unit) {
                            feedConnection?.endScroll()
                            state.onPredictiveBackEnd()
                        }
                    }
                } else {
                    SideEffect {
                        if (feedProgress != progress) {
                            feedConnection?.onScroll(progress)
                        }
                    }
                }

                if (state.isSettledOnSecondaryPage && feedProgress >= 0.8f) {
                    LaunchedEffect(Unit) {
                        feedConnection?.endScroll()
                    }
                }

            }
        }

        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        )
        {
            if (!feedAvailable) {
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
            } else if (!feedHasContent) {
                Icon(
                    painterResource(R.drawable.news_48px),
                    null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    "Feed has no content.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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

    override suspend fun onPreActivate(state: LauncherScaffoldState) {
        super.onPreActivate(state)
        this.state.intValue = 2
    }

    override suspend fun onActivate(state: LauncherScaffoldState) {
        super.onActivate(state)
        this.state.intValue = 3
    }

    override suspend fun onDismiss(state: LauncherScaffoldState) {
        super.onDismiss(state)
        this.state.intValue = 0
    }

    override suspend fun onPreDismiss(state: LauncherScaffoldState) {
        super.onPreDismiss(state)
        this.state.intValue = 1
    }


}