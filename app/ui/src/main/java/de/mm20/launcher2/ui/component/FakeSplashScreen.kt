package de.mm20.launcher2.ui.component

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
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
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SavableSearchable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

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
        mutableStateOf(
            SplashScreenData(
                backgroundColor = defaultBackgroundColor,
                iconSize = 288.dp
            )
        )
    }

    LaunchedEffect(searchable) {
        withContext(Dispatchers.IO) {
            if (searchable is Application) {
                val activityInfo = searchable.getActivityInfo(context) ?: return@withContext
                val themeRes = activityInfo.themeResource
                val ctx = try {
                    context.createPackageContext(
                        searchable.componentName.packageName,
                        Context.CONTEXT_IGNORE_SECURITY
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    return@withContext
                }

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
                    try {
                        ContextCompat.getDrawable(ctx, typedValue.resourceId)
                    } catch (e: Resources.NotFoundException) {
                        null
                    }
                } else {
                    null
                }

                theme.resolveAttribute(
                    android.R.attr.windowSplashScreenIconBackgroundColor,
                    typedValue,
                    true
                )

                var iconSize = 288.dp

                val iconBackground = if (typedValue.isColor && typedValue.data != 0) {
                    iconSize = 240.dp
                    Color(typedValue.data)
                } else {
                    null
                }

                if (icon == null) {
                    icon = activityInfo.loadIcon(context.packageManager)
                    iconSize = 240.dp
                }

                if (icon is AdaptiveIconDrawable) {
                    val bg = icon.background
                    if (bg is ColorDrawable && backgroundColor != null && iconBackground == null &&
                        isRgbSimilarInHsv(bg.color, backgroundColor)
                    ) {
                        icon = icon.foreground
                        iconSize = 288.dp
                    } else {
                        iconSize = 160.dp
                    }
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


/*
 * From: https://android.googlesource.com/platform/frameworks/base/+/f05f9b960832b6272b6740721c0a4bbd1ce632c1/libs/WindowManager/Shell/src/com/android/wm/shell/startingsurface/SplashscreenContentDrawer.java#550
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
internal fun isRgbSimilarInHsv(a: Int, b: Int): Boolean {
    if (a == b) {
        return true
    }
    val lumA: Float = android.graphics.Color.luminance(a)
    val lumB: Float = android.graphics.Color.luminance(b)
    val contrastRatio =
        if (lumA > lumB) (lumA + 0.05f) / (lumB + 0.05f) else (lumB + 0.05f) / (lumA + 0.05f)
    if (contrastRatio < 2) {
        return true
    }
    val aHsv = FloatArray(3)
    val bHsv = FloatArray(3)
    android.graphics.Color.colorToHSV(a, aHsv)
    android.graphics.Color.colorToHSV(b, bHsv)
    // Minimum degree of the hue between two colors, the result range is 0-180.
    var minAngle = abs(aHsv[0] - bHsv[0]).toInt()
    minAngle = (minAngle + 180) % 360 - 180

    // Calculate the difference between two colors based on the HSV dimensions.
    val normalizeH = minAngle / 180f
    val squareH = normalizeH.toDouble().pow(2.0)
    val squareS = (aHsv[1] - bHsv[1]).toDouble().pow(2.0)
    val squareV = (aHsv[2] - bHsv[2]).toDouble().pow(2.0)
    val square = squareH + squareS + squareV
    val mean = square / 3
    val root = sqrt(mean)
    return root < 0.1
}
