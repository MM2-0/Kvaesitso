package de.mm20.launcher2.ui.component

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.data.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FakeSplashScreen(
    modifier: Modifier = Modifier,
    searchable: SavableSearchable? = null
) {
    val splashScreenData = rememberSplashScreenData(searchable)

    val animatedBackgroundColor by animateColorAsState(splashScreenData.backgroundColor)

    Surface(
        modifier = modifier
            .fillMaxSize(),
        shadowElevation = 4.dp,
        color = animatedBackgroundColor,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (splashScreenData.iconBackground != null) {
                Surface(
                    modifier = Modifier.size(192.dp),
                    shape = CircleShape,
                    color = splashScreenData.iconBackground
                ) {
                }
            }
            AsyncImage(
                modifier = Modifier.size(splashScreenData.iconSize),
                model = splashScreenData.icon,
                contentDescription = null
            )
            AsyncImage(
                modifier = Modifier
                    .padding(bottom = 60.dp)
                    .width(200.dp)
                    .height(80.dp)
                    .align(Alignment.BottomCenter),
                model = splashScreenData.brandingIcon,
                contentDescription = null
            )
        }
    }
}

data class SplashScreenData(
    val backgroundColor: Color,
    val icon: Drawable? = null,
    val iconSize: Dp,
    val brandingIcon: Drawable? = null,
    val iconBackground: Color? = null,
)

@Composable
fun rememberSplashScreenData(searchable: SavableSearchable?): SplashScreenData {
    val context = LocalContext.current
    val defaultBackgroundColor = MaterialTheme.colorScheme.background
    val state = remember {
        mutableStateOf(SplashScreenData(backgroundColor = defaultBackgroundColor, iconSize = 288.dp))
    }

    LaunchedEffect(searchable) {
        withContext(Dispatchers.IO) {
            if (searchable is LauncherApp) {
                val activityInfo = if (isAtLeastApiLevel(31)) {
                    searchable.launcherActivityInfo.activityInfo
                } else {
                    try {
                        context.packageManager.getActivityInfo(
                            searchable.launcherActivityInfo.componentName,
                            0
                        )
                    } catch (e: PackageManager.NameNotFoundException) {
                        null
                    }
                } ?: return@withContext
                val themeRes = activityInfo.themeResource
                val ctx = context.createPackageContext(
                    searchable.`package`,
                    Context.CONTEXT_IGNORE_SECURITY
                )
                ctx.setTheme(themeRes)

                val theme = ctx.theme

                val typedValue = TypedValue()
                theme.resolveAttribute(
                    android.R.attr.windowSplashScreenBackground,
                    typedValue,
                    true
                )
                if (!typedValue.isColor || typedValue.data == 0) {
                    theme.resolveAttribute(
                        android.R.attr.windowBackground,
                        typedValue,
                        true
                    )
                }
                if (!typedValue.isColor || typedValue.data == 0) {
                    theme.resolveAttribute(
                        android.R.attr.colorBackground,
                        typedValue,
                        true
                    )
                }
                if (!typedValue.isColor || typedValue.data == 0) {
                    theme.resolveAttribute(
                        android.R.attr.background,
                        typedValue,
                        true
                    )
                }
                val backgroundColor = typedValue.takeIf { it.isColor && it.data != 0 }?.data

                theme.resolveAttribute(
                    android.R.attr.windowSplashScreenAnimatedIcon,
                    typedValue,
                    true
                )

                var icon = if (typedValue.resourceId != 0) {
                    try {
                        ContextCompat.getDrawable(ctx, typedValue.resourceId)
                    } catch (e: Resources.NotFoundException) {
                        null
                    }
                } else {
                    null
                }

                theme.resolveAttribute(
                    android.R.attr.windowSplashScreenBrandingImage,
                    typedValue,
                    true
                )

                val brandingIcon = if (typedValue.resourceId != 0) {
                    ContextCompat.getDrawable(ctx, typedValue.resourceId)
                } else {
                    null
                }

                theme.resolveAttribute(
                    android.R.attr.windowSplashScreenIconBackgroundColor,
                    typedValue,
                    true
                )

                var iconSize = 288.dp

                var iconBackground = if (typedValue.isColor && typedValue.data != 0) {
                    iconSize = 240.dp
                    Color(typedValue.data)
                } else {
                    null
                }

                if (icon == null) {
                    icon = activityInfo.loadIcon(context.packageManager)
                    iconSize = 160.dp
                }

                state.value = SplashScreenData(
                    backgroundColor = backgroundColor?.let { Color(it) } ?: defaultBackgroundColor,
                    icon = icon,
                    brandingIcon = brandingIcon,
                    iconBackground = iconBackground,
                    iconSize = iconSize,
                )
            }
        }
    }

    return state.value
}

internal val TypedValue.isColor
    get() = type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT