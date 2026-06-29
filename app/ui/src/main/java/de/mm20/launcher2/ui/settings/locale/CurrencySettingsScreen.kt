package de.mm20.launcher2.ui.settings.locale

import android.icu.util.Currency
import android.icu.util.ULocale
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.preferences.ui.LocaleSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.dragndrop.DraggableItem
import de.mm20.launcher2.ui.component.dragndrop.rememberLazyDragAndDropListState
import de.mm20.launcher2.ui.component.preferences.DragAndDropPreferenceScreen
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.ktx.animateShapeAsState
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.util.Date

@Serializable
data object CurrencySettingsRoute : NavKey

@Composable
fun CurrencySettingsScreen() {
    val localeSettings = koinInject<LocaleSettings>()
    val currencies by localeSettings.currencies.collectAsState(null)

    val dragAndDropListState = rememberLazyDragAndDropListState(
        onDragStart = {
            it.key.toString().startsWith("currency_")
        },
        onItemMove = { from, to ->
            val currencies = currencies?.toMutableList() ?: return@rememberLazyDragAndDropListState

            val fromIndex = from.index - 2
            val toIndex = to.index - 2

            if (fromIndex > currencies.lastIndex || fromIndex < 0) return@rememberLazyDragAndDropListState
            if (toIndex > currencies.lastIndex || toIndex < 0) return@rememberLazyDragAndDropListState

            val item = currencies.removeAt(fromIndex)
            currencies.add(toIndex, item)
            localeSettings.setCurrencies(currencies)
        }
    )

    val locale = LocalLocale.current
    val defaultCurrency = remember(locale) {
        Currency.getInstance(locale.platformLocale)
    }

    DragAndDropPreferenceScreen(
        title = {
            Text(stringResource(R.string.preference_currencies))
        },
        lazyColumnState = dragAndDropListState,
    ) {

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                PreferenceCategory {
                    SwitchPreference(
                        title = stringResource(R.string.preference_use_system_default),
                        value = currencies?.isEmpty() == true,
                        onValueChanged = {
                            if (it) {
                                localeSettings.setCurrencies(emptyList())
                            } else {
                                localeSettings.setCurrencies(listOf(defaultCurrency.currencyCode))
                            }
                        },
                    )
                }
            }
        }
        if (currencies != null) {
            if (currencies!!.isEmpty()) {
                item {
                    PreferenceCategory {
                        Preference(
                            title = defaultCurrency.displayName,
                            icon = R.drawable.euro_24px,
                            enabled = false
                        )
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.info_20px), null,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            modifier = Modifier,
                            text = stringResource(R.string.hint_drag_and_drop_reorder),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                itemsIndexed(
                    items = currencies!!,
                    key = { _, it -> "currency_$it" }
                ) { index, currency ->
                    DraggableItem(
                        state = dragAndDropListState,
                        key = "currency_$currency"
                    ) {
                        val shape = getShape(index, currencies!!.size)
                        val draggedShape = MaterialTheme.shapes.medium

                        val elevation by animateDpAsState(if (it) 4.dp else 0.dp)

                        val currencyName = try {
                            Currency.getInstance(currency).displayName
                        } catch (e: IllegalArgumentException) {
                            currency
                        }

                        Surface(
                            shadowElevation = elevation,
                            tonalElevation = elevation,
                            modifier = Modifier
                                .zIndex(if (it) 1f else 0f),
                            shape = animateShapeAsState(
                                if (it) draggedShape else shape
                            ).value
                        ) {
                            Preference(
                                title = currencyName,
                                icon = getCurrencySymbol(currency),
                                iconPadding = true,
                                enabled = true,
                                controls = {
                                    var showMenu by remember { mutableStateOf(false) }
                                    IconButton(
                                        onClick = { showMenu = true }
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.more_vert_24px),
                                            stringResource(R.string.action_more_actions),
                                        )
                                    }
                                    DropdownMenuPopup(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false },
                                    ) {
                                        DropdownMenuGroup(
                                            shapes = MenuDefaults.groupShapes()
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.menu_remove)) },
                                                leadingIcon = {
                                                    Icon(painterResource(R.drawable.delete_24px), null)
                                                },
                                                onClick = {
                                                    val newCurrencies = currencies!!.toMutableList()
                                                    newCurrencies.removeAt(index)
                                                    localeSettings.setCurrencies(newCurrencies)
                                                    showMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                item {
                    var showModal by remember { mutableStateOf(false) }
                    FilledTonalButton(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .navigationBarsPadding(),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        onClick = {
                            showModal = true
                        }) {
                        Icon(
                            painterResource(R.drawable.add_20px),
                            null,
                            modifier = Modifier
                                .padding(end = ButtonDefaults.IconSpacing)
                                .size(ButtonDefaults.IconSize)
                        )
                        Text(stringResource(R.string.preference_add_currency))
                    }

                    CurrencyPickerSheet(
                        selectedCurrencies = currencies!!,
                        onCurrencySelected = { currency ->
                            val newCurrencies = currencies!!.toMutableList()
                            newCurrencies.add(currency)
                            localeSettings.setCurrencies(newCurrencies)
                            showModal = false
                        },
                        expanded = showModal,
                        onDismissRequest = { showModal = false }
                    )
                }
            }
        }
    }
}

private fun getCurrencySymbol(currencyCode: String): Int? {
    return when (currencyCode) {
        "AUD", "BBD", "BMD", "BND", "BSD", "BZD", "CAD", "FJD", "GYD", "HKD", "JMD", "KID", "KYD",
        "LRD", "NAD", "NZD", "SBD", "SGD", "SRD", "TTD", "TVD", "TWD", "USD", "XCD", "ARS", "CLP",
        "COP", "CUP", "DOP", "MXN", "UYU" -> R.drawable.attach_money_24px

        "EUR" -> R.drawable.euro_24px
        "EGP", "FKP", "GBP", "GIP", "SHP", "SDG", "SYP" -> R.drawable.currency_pound_24px
        "JPY" -> R.drawable.currency_yen_24px
        "CNY" -> R.drawable.currency_yuan_24px
        "INR" -> R.drawable.currency_rupee_24px
        "RUB" -> R.drawable.currency_ruble_24px
        else -> R.drawable.toll_24px
    }
}

@Composable
private fun getShape(index: Int, total: Int): Shape {
    if (total == 1) {
        return MaterialTheme.shapes.medium
    }

    if (total > 1 && index > 0 && index < total - 1) {
        return MaterialTheme.shapes.extraSmall
    }

    val xs = MaterialTheme.shapes.extraSmall
    val md = MaterialTheme.shapes.medium

    if (index == 0) {
        return xs.copy(
            topStart = md.topStart,
            topEnd = md.topEnd
        )
    } else {
        return xs.copy(
            bottomStart = md.bottomStart,
            bottomEnd = md.bottomEnd
        )
    }
}

@Composable
private fun CurrencyPickerSheet(
    selectedCurrencies: List<String>,
    onCurrencySelected: (String) -> Unit,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    val availableCurrencies = remember {
        val locales = ULocale.getAvailableLocales()
        val currencyCodes = mutableSetOf<String>()

        for (locale in locales) {
            val currencies = Currency.getAvailableCurrencyCodes(locale, Date()) ?: continue
            currencyCodes.addAll(currencies)
        }

        val currencies = mutableListOf<Currency>()

        for (currencyCode in currencyCodes) {
            try {
                currencies += Currency.getInstance(currencyCode)

            } catch (e: IllegalArgumentException) {
                // Ignore invalid currency codes
            }
        }

        currencies.sortBy { it.displayName }

        currencies
    }

    DismissableBottomSheet(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding() + 12.dp,
                start = 12.dp,
                end = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(availableCurrencies) { i, currency ->
                Box(
                    modifier = Modifier.clip(
                        shape = getShape(i, availableCurrencies.size)
                    )
                ) {
                    Preference(
                        title = currency.displayName,
                        onClick = {
                            onCurrencySelected(currency.currencyCode)
                        }
                    )
                }
            }
        }
    }
}