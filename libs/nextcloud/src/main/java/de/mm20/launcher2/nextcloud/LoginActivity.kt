package de.mm20.launcher2.nextcloud

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class LoginActivity : AppCompatActivity() {

    private val nextcloudClient = NextcloudApiHelper(this)

    private val permissionsManager: PermissionsManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) nextcloudDark else nextcloudLight
            ) {
                var nextcloudUrl by rememberSaveable { mutableStateOf("") }
                var error by rememberSaveable { mutableStateOf<String?>(null) }
                var loading by rememberSaveable { mutableStateOf(false) }

                val hasLocalNetworkPermission by permissionsManager.hasPermission(PermissionGroup.LocalNetwork)
                    .collectAsStateWithLifecycle(null)

                val dark = isSystemInDarkTheme()

                LaunchedEffect(dark) {
                    enableEdgeToEdge(
                        statusBarStyle = if (dark) SystemBarStyle.dark(0) else SystemBarStyle.light(
                            0,
                            0x33000000.toInt()
                        ),
                        navigationBarStyle = if (dark) SystemBarStyle.dark(0) else SystemBarStyle.light(
                            0,
                            0x33000000.toInt()
                        ),
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(32.dp)
                        .systemBarsPadding()
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painterResource(R.drawable.ic_nextcloud_logo),
                        contentDescription = "Nextcloud Logo",
                        colorFilter = ColorFilter.tint(
                            MaterialTheme.colorScheme.primary,
                        )
                    )

                    if (isAtLeastApiLevel(37)) {
                        AnimatedVisibility(hasLocalNetworkPermission == false && error != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainer,
                                        MaterialTheme.shapes.medium
                                    )
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(
                                        16.dp,
                                        Alignment.Start
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painterResource(R.drawable.warning_24px), null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        stringResource(
                                            R.string.missing_permission_local_network,
                                            stringResource(R.string.app_name)
                                        ),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                TextButton(
                                    modifier = Modifier.align(Alignment.End),
                                    onClick = {
                                        permissionsManager.requestPermission(
                                            this@LoginActivity,
                                            PermissionGroup.LocalNetwork
                                        )
                                    }) {
                                    Text(stringResource(R.string.action_fix))
                                }

                            }
                        }
                    }

                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth(),
                        label = {
                            Text(stringResource(R.string.nextcloud_server_url))
                        },
                        value = nextcloudUrl,
                        onValueChange = { nextcloudUrl = it },
                        enabled = !loading,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                        )
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = nextcloudUrl.isNotBlank() && !loading,
                        onClick = {
                            lifecycleScope.launch {
                                loading = true
                                error = null
                                var url = nextcloudUrl
                                if (!(url.startsWith("http://") || url.startsWith("https://"))) {
                                    url = "https://$url"
                                }
                                val flow = nextcloudClient.startLoginFlow(url)
                                if (flow != null) {
                                    openLoginPage(flow)
                                } else {
                                    error = getString(R.string.nextcloud_server_invalid_url)
                                }
                                loading = false
                            }
                        }
                    ) {
                        Text(stringResource(R.string.login_flow_continue))
                    }
                }

            }
        }
    }

    private var currentLoginFlow: LoginFlowResponse? = null
    private fun openLoginPage(flow: LoginFlowResponse) {
        currentLoginFlow = flow
        val customTabIntent = CustomTabsIntent.Builder().build()
        customTabIntent.launchUrl(this, flow.login.toUri())
    }

    override fun onResume() {
        super.onResume()
        permissionsManager.onResume()

        val flow = currentLoginFlow ?: return
        lifecycleScope.launch {
            val result = nextcloudClient.pollLoginFlow(flow)
            if (result != null) {
                nextcloudClient.setServer(result.server, result.loginName, result.appPassword)
                currentLoginFlow = null
                finish()
            }
        }
    }

    private val nextcloudLight: ColorScheme
        get() = lightColorScheme(
            primary = Color(0xFF00639B),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFCEE5FF),
            onPrimaryContainer = Color(0xFF001D33),
            inversePrimary = Color(0xFF96CBFF),
            secondary = Color(0xFF51606F),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFD4E4F6),
            onSecondaryContainer = Color(0xFF0D1D2A),
            tertiary = Color(0xFF68587A),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFEEDBFF),
            onTertiaryContainer = Color(0xFF231533),
            surface = Color(0xFFF9F9FC),
            surfaceBright = Color(0xFFF9F9FC),
            surfaceDim = Color(0xFFD9DADD),
            surfaceContainer = Color(0xFFEDEEF1),
            surfaceContainerHighest = Color(0xFFE2E2E5),
            surfaceContainerHigh = Color(0xFFE8E8EB),
            surfaceContainerLow = Color(0xFFF3F3F6),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1A1C1E),
            onSurfaceVariant = Color(0xFF42474E),
            inverseSurface = Color(0xFF2F3133),
            inverseOnSurface = Color(0xFFF0F0F3),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFDAD5),
            onErrorContainer = Color(0xFF410002),
            outline = Color(0xFF72787F),
            outlineVariant = Color(0xFFC2C7CF),
            scrim = Color(0xFF000000),
        )

    private val nextcloudDark: ColorScheme
        get() = darkColorScheme(
            primary = Color(0xFF96CBFF),
            onPrimary = Color(0xFF003353),
            primaryContainer = Color(0xFF004A76),
            onPrimaryContainer = Color(0xFFCEE5FF),
            inversePrimary = Color(0xFF00639B),
            secondary = Color(0xFFB9C8DA),
            onSecondary = Color(0xFF23323F),
            secondaryContainer = Color(0xFF394857),
            onSecondaryContainer = Color(0xFFD4E4F6),
            tertiary = Color(0xFFD3BFE6),
            onTertiary = Color(0xFF382A49),
            tertiaryContainer = Color(0xFF4F4061),
            onTertiaryContainer = Color(0xFFEEDBFF),
            surface = Color(0xFF1A1C1E),
            surfaceBright = Color(0xFF37393C),
            surfaceDim = Color(0xFF111416),
            surfaceContainer = Color(0xFF1E2022),
            surfaceContainerHighest = Color(0xFF333537),
            surfaceContainerHigh = Color(0xFF282A2D),
            surfaceContainerLow = Color(0xFF1A1C1E),
            surfaceContainerLowest = Color(0xFF0C0E11),
            onSurface = Color(0xFFE2E2E5),
            onSurfaceVariant = Color(0xFFC2C7CF),
            inverseSurface = Color(0xFFE2E2E5),
            inverseOnSurface = Color(0xFF2F3133),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690004),
            errorContainer = Color(0xFF930009),
            onErrorContainer = Color(0xFFFFB4AB),
            outline = Color(0xFF8C9198),
            outlineVariant = Color(0xFF42474E),
            scrim = Color(0xFF000000),
        )

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

}