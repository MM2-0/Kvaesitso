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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.preferences.NamecoinSettingsStore
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.namecoin_settings
import com.vitorpamplona.amethyst.commons.service.namecoin.NamecoinClients
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NamecoinSettingsScreen(nav: INav) {
    val services = LocalAppServices.current
    NamecoinSettingsScreen(services.namecoinSettings, services.namecoinClients, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NamecoinSettingsScreen(
    namecoinPrefs: NamecoinSettingsStore,
    clients: NamecoinClients,
    nav: INav,
) {
    val namecoinSettings by namecoinPrefs.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = Res.string.namecoin_settings), nav)
        },
    ) {
        Column(
            Modifier
                .padding(it)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
        ) {
            NamecoinSettingsSection(
                settings = namecoinSettings,
                onToggleEnabled = { enabled ->
                    scope.launch { namecoinPrefs.setEnabled(enabled) }
                },
                onAddServer = { server ->
                    scope.launch { namecoinPrefs.addServer(server) }
                },
                onRemoveServer = { server ->
                    scope.launch { namecoinPrefs.removeServer(server) }
                },
                onReset = {
                    scope.launch { namecoinPrefs.reset() }
                },
                onTestServer = { server -> clients.testElectrumxServer(server) },
                onPinCert = { pem ->
                    scope.launch {
                        namecoinPrefs.addPinnedCert(pem)
                        // Share the pinned PEM across both backends so the
                        // user only has to confirm a self-signed cert once.
                        clients.addPinnedCert(pem)
                    }
                },
                onSetBackend = { backend ->
                    scope.launch { namecoinPrefs.setBackend(backend) }
                },
                onSetCoreRpcConfig = { cfg ->
                    scope.launch {
                        namecoinPrefs.setCoreRpcConfig(cfg)
                        clients.setCoreRpcConfig(cfg)
                    }
                },
                onSetFallbackToCustomElectrumx = { enabled ->
                    scope.launch { namecoinPrefs.setFallbackToCustomElectrumx(enabled) }
                },
                onSetFallbackToDefaultElectrumx = { enabled ->
                    scope.launch { namecoinPrefs.setFallbackToDefaultElectrumx(enabled) }
                },
                onTestCoreRpc = { cfg -> clients.probeCoreRpc(cfg) },
            )
        }
    }
}
