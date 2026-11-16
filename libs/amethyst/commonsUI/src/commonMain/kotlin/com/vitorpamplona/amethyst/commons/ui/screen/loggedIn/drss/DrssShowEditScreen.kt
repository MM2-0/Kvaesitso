/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.drss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.drss.DrssShowSettings
import com.vitorpamplona.amethyst.commons.drss.DrssShowSlot
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.drss.DrssState
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drss_save
import com.vitorpamplona.amethyst.commons.resources.drss_show_add_slot
import com.vitorpamplona.amethyst.commons.resources.drss_show_edit_title
import com.vitorpamplona.amethyst.commons.resources.drss_show_field_language
import com.vitorpamplona.amethyst.commons.resources.drss_show_field_name
import com.vitorpamplona.amethyst.commons.resources.drss_show_field_products
import com.vitorpamplona.amethyst.commons.resources.drss_show_field_retention
import com.vitorpamplona.amethyst.commons.resources.drss_show_field_tags
import com.vitorpamplona.amethyst.commons.resources.drss_show_field_voice
import com.vitorpamplona.amethyst.commons.resources.drss_show_on_air
import com.vitorpamplona.amethyst.commons.resources.drss_show_runs_on_drss
import com.vitorpamplona.amethyst.commons.resources.drss_show_schedule
import com.vitorpamplona.amethyst.commons.resources.drss_show_slot_part
import com.vitorpamplona.amethyst.commons.resources.drss_show_slot_time
import com.vitorpamplona.amethyst.commons.resources.drss_show_slot_zone
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.drss.show.DrssShowEvent
import com.vitorpamplona.quartz.nip01Core.core.Address

/**
 * Edits the user's drss.io show: its name, language, voice, tags, what each episode makes, when it
 * publishes, how long it keeps episodes, and whether it is on air. The rest of the settings (the
 * generator's prompts and sources) stay as they are. Making a show and running one on demand need
 * drss's server, which holds the show's key.
 */
@Composable
fun DrssShowEditScreen(
    showAddress: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val address = remember(showAddress) { Address.parse(showAddress) } ?: return
    val showNote = remember(address) { LocalCache.getOrCreateAddressableNote(address) }
    val loaded by observeNoteEvent<DrssShowEvent>(showNote, accountViewModel)

    DrssScreenFrame(stringRes(Res.string.drss_show_edit_title), nav) {
        val show = loaded ?: return@DrssScreenFrame
        DrssShowForm(show, accountViewModel, nav)
    }
}

@Composable
private fun DrssShowForm(
    show: DrssShowEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val settings = remember(show) { DrssShowSettings.of(show) }
    var name by remember(show) { mutableStateOf(settings.name ?: show.showName() ?: show.dTag()) }
    var language by remember(show) { mutableStateOf(settings.language.orEmpty()) }
    var voice by remember(show) { mutableStateOf(settings.voice.orEmpty()) }
    var tags by remember(show) { mutableStateOf(settings.tags.joinToString(", ")) }
    var products by remember(show) { mutableStateOf(settings.products.joinToString(", ")) }
    var retention by remember(show) { mutableStateOf(settings.retentionHours?.toString().orEmpty()) }
    var onAir by remember(show) { mutableStateOf(show.isActive()) }
    val slots = remember(show) { mutableStateListOf(*settings.schedule.toTypedArray()) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringRes(Res.string.drss_show_on_air), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = onAir, onCheckedChange = { onAir = it })
        }
        Field(name, { name = it }, stringRes(Res.string.drss_show_field_name))
        Field(language, { language = it }, stringRes(Res.string.drss_show_field_language))
        Field(voice, { voice = it }, stringRes(Res.string.drss_show_field_voice))
        Field(tags, { tags = it }, stringRes(Res.string.drss_show_field_tags))
        Field(products, { products = it }, stringRes(Res.string.drss_show_field_products))
        Field(retention, { retention = it.filter(Char::isDigit) }, stringRes(Res.string.drss_show_field_retention), KeyboardType.Number)

        Text(stringRes(Res.string.drss_show_schedule), style = MaterialTheme.typography.titleMedium)
        slots.forEachIndexed { index, slot ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SlotField(slot.part.orEmpty(), { slots[index] = slot.copy(part = it) }, stringRes(Res.string.drss_show_slot_part), Modifier.weight(1f))
                SlotField(slot.time, { slots[index] = slot.copy(time = it) }, stringRes(Res.string.drss_show_slot_time), Modifier.weight(0.8f))
                SlotField(slot.zone.orEmpty(), { slots[index] = slot.copy(zone = it) }, stringRes(Res.string.drss_show_slot_zone), Modifier.weight(1.4f))
                IconButton(onClick = { slots.removeAt(index) }) {
                    Icon(symbol = MaterialSymbols.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.grayText)
                }
            }
        }
        TextButton(onClick = { slots.add(DrssShowSlot(time = "", zone = slots.lastOrNull()?.zone)) }) {
            Text(stringRes(Res.string.drss_show_add_slot))
        }

        Text(stringRes(Res.string.drss_show_runs_on_drss), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)

        Button(
            onClick = {
                val content =
                    DrssShowSettings.edit(
                        content = show.content,
                        name = name.trim(),
                        language = language,
                        voice = voice,
                        tags = tags.split(','),
                        products = products.split(','),
                        schedule = slots.filter { it.time.isNotBlank() },
                        retentionHours = retention.toIntOrNull(),
                    )
                val account = accountViewModel.account
                accountViewModel.launchSigner {
                    account.drss.updateShow(show, content, name.trim(), if (onAir) DrssShowEvent.ACTIVE else DrssState.SHOW_RETIRED, account)
                }
                nav.popBack()
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringRes(Res.string.drss_save))
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    SpellCheckedOutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SlotField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
) {
    SpellCheckedOutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}
