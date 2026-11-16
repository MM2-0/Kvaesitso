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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.cordn.ui.SettingsFormBlock
import com.vitorpamplona.amethyst.commons.model.cordn.CordnRuntime
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cordn_coordinators_none
import com.vitorpamplona.amethyst.commons.resources.cordn_group_unavailable
import com.vitorpamplona.amethyst.commons.resources.cordn_group_unavailable_detail
import com.vitorpamplona.amethyst.commons.resources.cordn_keypackages_disclosure
import com.vitorpamplona.amethyst.commons.resources.cordn_keypackages_explainer
import com.vitorpamplona.amethyst.commons.resources.cordn_keypackages_failed
import com.vitorpamplona.amethyst.commons.resources.cordn_keypackages_last_resort_no
import com.vitorpamplona.amethyst.commons.resources.cordn_keypackages_last_resort_yes
import com.vitorpamplona.amethyst.commons.resources.cordn_keypackages_orphans
import com.vitorpamplona.amethyst.commons.resources.cordn_keypackages_orphans_body
import com.vitorpamplona.amethyst.commons.resources.cordn_keypackages_publish
import com.vitorpamplona.amethyst.commons.resources.cordn_keypackages_publish_last_resort
import com.vitorpamplona.amethyst.commons.resources.cordn_keypackages_section
import com.vitorpamplona.amethyst.commons.resources.cordn_keypackages_summary
import com.vitorpamplona.amethyst.commons.resources.cordn_keypackages_title
import com.vitorpamplona.amethyst.commons.resources.cordn_keypackages_topup_note
import com.vitorpamplona.amethyst.commons.resources.cordn_keypackages_withdraw_all
import com.vitorpamplona.amethyst.commons.resources.cordn_keypackages_withdraw_orphans
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn.CoordinatorIdentityRow
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.cordn.client.CoordinatorConfig
import com.vitorpamplona.cordn.client.CordnKeyPackageRow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * The KeyPackages this account has published, per coordinator.
 *
 * ## Why this screen has to exist at all
 *
 * `spec/00.md` §4.2 gives cordn **no KeyPackage event kind**. There is no
 * relay to fall back on: the coordinator is the only place an inviter can
 * find one, so an account with none published simply cannot be added to a
 * group, and nothing anywhere would say why. Marmot's equivalent is invisible
 * because its packages live on relays and the app republishes them for you;
 * here the publish is a call to one named party, which makes it a decision
 * rather than a housekeeping detail.
 *
 * ## Publishing is attributable, and that is the whole cost
 *
 * §8.4: a KeyPackage is published under the account key, with a signed payload
 * the coordinator stores and serves. It tells that coordinator this account
 * exists and is invitable — permanently, and whether or not anyone ever
 * invites anyone. That is stated above the button, not discovered after it.
 *
 * ## Two different truths, shown separately
 *
 * The coordinator's listing says what an inviter can take. The local store
 * says whether the Welcome that results can be opened. A package in the first
 * without the second belongs to another device of this account — or to an
 * install that is gone, in which case anyone who uses it sends a Welcome
 * nobody will ever read. Collapsing the two would hide exactly that case.
 */
@Composable
fun CordnKeyPackagesScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val runtime = accountViewModel.account.cordnRuntime

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.cordn_keypackages_title), nav) },
    ) { padding ->
        if (runtime == null) {
            EmptyState(
                title = stringRes(Res.string.cordn_group_unavailable),
                description = stringRes(Res.string.cordn_group_unavailable_detail),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        val coordinators by runtime.coordinators.collectAsStateWithLifecycle()

        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringRes(Res.string.cordn_keypackages_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // One section holding every coordinator rather than one each:
            // SettingsSection titles come from a StringResource and a
            // coordinator's label is runtime text, so a section per
            // coordinator is not something this primitive can say. The
            // dividers stay, inside, where they now separate peers within a
            // box instead of floating in open page.
            SettingsSection(Res.string.cordn_keypackages_section) {
                SettingsFormBlock {
                    if (coordinators.isEmpty()) {
                        Text(
                            text = stringRes(Res.string.cordn_coordinators_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    coordinators.forEachIndexed { index, config ->
                        // Between, not after: a rule under the last one drew a
                        // line to nothing.
                        if (index > 0) HorizontalDivider()
                        CoordinatorKeyPackages(config, runtime, accountViewModel, nav)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoordinatorKeyPackages(
    config: CoordinatorConfig,
    runtime: CordnRuntime,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scope = rememberCoroutineScope()
    var rows by remember(config.pubKey) { mutableStateOf<List<CordnKeyPackageRow>?>(null) }
    var busy by remember(config.pubKey) { mutableStateOf(false) }
    var error by remember(config.pubKey) { mutableStateOf<String?>(null) }
    val failed = stringRes(Res.string.cordn_keypackages_failed)

    suspend fun reload() {
        busy = true
        error = null
        try {
            rows = runtime.keyPackages(config.pubKey)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            error = e.message ?: failed
        } finally {
            busy = false
        }
    }

    LaunchedEffect(config.pubKey) { reload() }

    CoordinatorIdentityRow(
        pubKey = config.pubKey,
        label = config.label,
        accountViewModel = accountViewModel,
        nav = nav,
        size = 24.dp,
    ) { name ->
        Text(text = name, style = MaterialTheme.typography.titleSmall)
    }

    if (busy) CircularProgressIndicator()
    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }

    val loaded = rows
    if (loaded != null && !busy) {
        val single = loaded.count { !it.lastResort }
        val hasLastResort = loaded.any { it.lastResort }

        Text(
            text = pluralStringRes(Res.plurals.cordn_keypackages_summary, single, single),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text =
                if (hasLastResort) {
                    stringRes(Res.string.cordn_keypackages_last_resort_yes)
                } else {
                    stringRes(Res.string.cordn_keypackages_last_resort_no)
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        loaded.filterNot { it.openableHere }.takeIf { it.isNotEmpty() }?.let { orphans ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = pluralStringRes(Res.plurals.cordn_keypackages_orphans, orphans.size, orphans.size),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringRes(Res.string.cordn_keypackages_orphans_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                runtime.withdrawKeyPackages(config.pubKey, orphans.map { it.keyPackageRef })
                            } catch (e: Exception) {
                                currentCoroutineContext().ensureActive()
                                error = e.message ?: failed
                            }
                            reload()
                        }
                    }) {
                        Text(stringRes(Res.string.cordn_keypackages_withdraw_orphans))
                    }
                }
            }
        }

        Text(
            // Above the buttons, because it is the part that cannot be taken
            // back: §8.4 makes publishing an attributable, permanent record.
            text = stringRes(Res.string.cordn_keypackages_disclosure),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    scope.launch {
                        try {
                            runtime.publishKeyPackage(config.pubKey)
                        } catch (e: Exception) {
                            currentCoroutineContext().ensureActive()
                            error = e.message ?: failed
                        }
                        reload()
                    }
                },
                enabled = !busy,
            ) {
                Text(stringRes(Res.string.cordn_keypackages_publish))
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        try {
                            runtime.publishKeyPackage(config.pubKey, lastResort = true)
                        } catch (e: Exception) {
                            currentCoroutineContext().ensureActive()
                            error = e.message ?: failed
                        }
                        reload()
                    }
                },
                enabled = !busy && !hasLastResort,
            ) {
                Text(stringRes(Res.string.cordn_keypackages_publish_last_resort))
            }
            if (loaded.isNotEmpty()) {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                runtime.withdrawKeyPackages(config.pubKey, loaded.map { it.keyPackageRef })
                            } catch (e: Exception) {
                                currentCoroutineContext().ensureActive()
                                error = e.message ?: failed
                            }
                            reload()
                        }
                    },
                    enabled = !busy,
                ) {
                    Text(stringRes(Res.string.cordn_keypackages_withdraw_all), color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Text(
            // The rule CordnRuntime.maintainKeyPackages implements, said where
            // someone can see that it applies to them.
            text = stringRes(Res.string.cordn_keypackages_topup_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
