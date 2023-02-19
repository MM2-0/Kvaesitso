package de.mm20.launcher2.ui.component

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.layout.BottomReversed
import de.mm20.launcher2.ui.locals.LocalCardStyle

@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    style: Settings.SearchBarSettings.SearchBarStyle,
    level: SearchBarLevel,
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocus: () -> Unit = {},
    onUnfocus: () -> Unit = {},
    reverse: Boolean = false,
    darkColors: Boolean = false,
    menu: @Composable RowScope.() -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit = {},
    onKeyboardActionGo: (KeyboardActionScope.() -> Unit)? = null
) {

    val transition = updateTransition(level, label = "Searchbar")

    val elevation by transition.animateDp(
        label = "elevation",
        transitionSpec = {
            when {
                initialState == SearchBarLevel.Resting -> tween(
                    durationMillis = 200,
                    delayMillis = 200
                )

                targetState == SearchBarLevel.Resting -> tween(durationMillis = 200)
                else -> tween(durationMillis = 500)
            }
        }
    ) {
        when {
            it == SearchBarLevel.Resting && style != Settings.SearchBarSettings.SearchBarStyle.Solid -> 0.dp
            it == SearchBarLevel.Raised -> 8.dp
            else -> 2.dp
        }
    }

    val backgroundOpacity by transition.animateFloat(label = "backgroundOpacity",
        transitionSpec = {
            when {
                initialState == SearchBarLevel.Resting -> tween(durationMillis = 200)
                targetState == SearchBarLevel.Resting -> tween(
                    durationMillis = 200,
                    delayMillis = 200
                )

                else -> tween(durationMillis = 200)
            }
        }) {
        when {
            it == SearchBarLevel.Active -> LocalCardStyle.current.opacity
            style != Settings.SearchBarSettings.SearchBarStyle.Transparent -> 1f
            it == SearchBarLevel.Resting -> 0f
            else -> 1f
        }
    }

    val contentColor by transition.animateColor(label = "textColor",
        transitionSpec = {
            when {
                initialState == SearchBarLevel.Resting -> tween(durationMillis = 200)
                targetState == SearchBarLevel.Resting -> tween(
                    durationMillis = 200,
                    delayMillis = 200
                )

                else -> tween(durationMillis = 500)
            }
        }) {
        when {
            style != Settings.SearchBarSettings.SearchBarStyle.Transparent -> MaterialTheme.colorScheme.onSurface
            it == SearchBarLevel.Resting -> if (darkColors) Color(0, 0, 0, 180) else Color.White
            else -> MaterialTheme.colorScheme.onSurface
        }
    }

    val opacity by transition.animateFloat(label = "opacity") {
        if (style == Settings.SearchBarSettings.SearchBarStyle.Hidden && it == SearchBarLevel.Resting) 0f
        else 1f
    }

    LauncherCard(
        modifier = modifier
            .alpha(opacity),
        backgroundOpacity = backgroundOpacity,
        elevation = elevation
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor
        ) {
            Column(
                verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top
            ) {
                Row(
                    modifier = Modifier.height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier.padding(12.dp),
                        imageVector = androidx.compose.material.icons.Icons.Rounded.Search,
                        contentDescription = null,
                        tint = contentColor
                    )
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_bar_placeholder),
                                style = MaterialTheme.typography.titleMedium,
                                color = contentColor
                            )
                        }
                        LaunchedEffect(level) {
                            if (level == SearchBarLevel.Resting) onUnfocus()
                        }
                        BasicTextField(
                            modifier = Modifier
                                .onFocusChanged {
                                    if (it.hasFocus) onFocus()
                                }
                                .focusRequester(focusRequester)
                                .fillMaxWidth(),
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = contentColor
                            ),
                            singleLine = true,
                            value = value,
                            onValueChange = onValueChange,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                imeAction = if (onKeyboardActionGo == null) {
                                    ImeAction.Search
                                } else {
                                    ImeAction.Go
                                }
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = onKeyboardActionGo
                            )
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        menu()
                    }
                }
                this.actions()
            }
        }
    }
}

enum class SearchBarLevel {
    /**
     * The default, "hidden" state, when the launcher is in its initial state (scroll position is 0
     * and search is closed)
     */
    Resting,

    /**
     * When the search is open but there is no content behind the search bar (scroll position is 0)
     */
    Active,

    /**
     * When there is content below the search bar which requires the search bar to be raised above
     * this content (scroll position is not 0)
     */
    Raised
}