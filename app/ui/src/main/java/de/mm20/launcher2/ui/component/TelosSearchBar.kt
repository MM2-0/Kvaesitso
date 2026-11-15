package de.mm20.launcher2.ui.component
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R

/**
 * Modern pill shaped search field used by all Telos apps.
 *
 * @param leading replaces the search icon
 * @param trailing extra actions (filter, sort, mic ...) shown after the clear button
 * @param filters filter chips below the pill. Emit chips directly: this component
 * owns the scrolling row; a nested horizontal scroller receives unbounded width.
 */
@Composable
fun TelosSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    onSearch: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    filters: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SearchPill(
            value, onValueChange, placeholder, Modifier.fillMaxWidth(), 56.dp,
            autoFocus, onSearch, leading, trailing, enabled, onFocusChanged,
        )
        if (filters != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { filters() }
        }
    }
}

/**
 * Compact variant (44dp, no horizontal padding) for the title slot of a TopAppBar.
 */
@Composable
fun TelosSearchTopBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    onSearch: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    filters: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        SearchPill(
            value, onValueChange, placeholder, Modifier.fillMaxWidth(), 44.dp,
            autoFocus, onSearch, leading, trailing, enabled, onFocusChanged,
        )
        if (filters != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { filters() }
        }
    }
}

@Composable
private fun SearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier,
    height: Dp,
    autoFocus: Boolean,
    onSearch: (() -> Unit)?,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable RowScope.() -> Unit)?,
    enabled: Boolean,
    onFocusChanged: (Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val ringColor by animateColorAsState(
        if (focused) TelosCyber.NeonCyan.copy(alpha = 0.8f) else TelosCyber.NeonCyan.copy(alpha = 0.25f),
        label = "searchRing",
    )

    if (autoFocus) {
        LaunchedEffect(Unit) {
            try {
                focusRequester.requestFocus()
            } catch (e: IllegalStateException) {
                // not attached yet
            }
        }
    }

    Surface(
        modifier = modifier.heightIn(min = maxOf(height, 48.dp)),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 2.dp,
        border = BorderStroke(2.dp, ringColor),
    ) {
        Row(
            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(if (leading != null) de.mm20.launcher2.ui.layout.UiSpacing.TouchTarget else 40.dp), contentAlignment = Alignment.Center) {
                if (leading != null) leading() else Icon(
                    painterResource(BaseR.drawable.search_24px),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
            Box(
                Modifier.weight(1f).defaultMinSize(minHeight = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder.isNotBlank()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { onFocusChanged(it.isFocused) }
                        .semantics { contentDescription = placeholder },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Start,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                        onSearch?.invoke()
                    }),
                    interactionSource = interaction,
                )
            }
            AnimatedVisibility(
                visible = value.isNotEmpty() && enabled,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(de.mm20.launcher2.ui.layout.UiSpacing.TouchTarget)) {
                    Icon(
                        painterResource(BaseR.drawable.close_24px),
                        contentDescription = stringResource(I18nR.string.ts_clear),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (trailing != null) trailing()
        }
    }
}

/** Icon and "No results for “x”" message for an empty search result. */
@Composable
fun SearchEmptyState(query: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painterResource(BaseR.drawable.search_24px),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Text(
            stringResource(I18nR.string.ts_no_results, query),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
