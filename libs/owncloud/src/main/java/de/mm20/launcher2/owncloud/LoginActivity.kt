package de.mm20.launcher2.owncloud

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val owncloudClient = OwncloudClient(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) owncloudDark else owncloudLight
            ) {
                var owncloudUrl by rememberSaveable { mutableStateOf("") }
                var serverUrlConfirmed by rememberSaveable { mutableStateOf(false) }
                var username by rememberSaveable { mutableStateOf("") }
                var password by rememberSaveable { mutableStateOf("") }
                var error by rememberSaveable { mutableStateOf<String?>(null) }
                var loading by rememberSaveable { mutableStateOf(false) }

                val dark = isSystemInDarkTheme()

                if (serverUrlConfirmed) {
                    BackHandler {
                        serverUrlConfirmed = false
                        error = null
                    }
                }

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
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painterResource(R.drawable.ic_owncloud_logo),
                        contentDescription = "Owncloud Logo",
                        colorFilter = ColorFilter.tint(
                            MaterialTheme.colorScheme.primary,
                        )
                    )
                    AnimatedContent(!serverUrlConfirmed) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (it) {
                                OutlinedTextField(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    label = {
                                        Text(stringResource(R.string.owncloud_server_url))
                                    },
                                    value = owncloudUrl,
                                    onValueChange = { owncloudUrl = it },
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
                                    enabled = owncloudUrl.isNotBlank() && !loading && !serverUrlConfirmed,
                                    onClick = {
                                        lifecycleScope.launch {
                                            loading = true
                                            error = null
                                            var url = owncloudUrl
                                            if (!(url.startsWith("http://") || url.startsWith("https://"))) {
                                                url = "https://$url"
                                            }
                                            if (owncloudClient.checkOwncloudInstallation(url)) {
                                                owncloudUrl = url
                                                serverUrlConfirmed = true
                                            } else {
                                                error =
                                                    getString(R.string.owncloud_server_invalid_url)
                                            }
                                            loading = false
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.login_flow_continue))
                                }
                            } else {
                                Text(
                                    owncloudUrl,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 24.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                )
                                OutlinedTextField(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    label = {
                                        Text(stringResource(R.string.owncloud_username))
                                    },
                                    value = username,
                                    onValueChange = { username = it },
                                    enabled = !loading,
                                    isError = error != null,
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 32.dp),
                                    label = {
                                        Text(stringResource(R.string.owncloud_password))
                                    },
                                    value = password,
                                    onValueChange = { password = it },
                                    enabled = !loading,
                                    isError = error != null,
                                    supportingText = {
                                        Text(error ?: stringResource(R.string.owncloud_login_2fa_hint))
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                    ),
                                    visualTransformation = PasswordVisualTransformation(),
                                )
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = username.isNotBlank() && password.isNotBlank() && !loading,
                                    onClick = {
                                        lifecycleScope.launch {
                                            loading = true
                                            val valid = owncloudClient.checkOwncloudCredentials(
                                                server = owncloudUrl,
                                                username = username,
                                                password = password
                                            )
                                            loading = false
                                            if (valid) {
                                                owncloudClient.setServer(owncloudUrl, username, password)
                                                finish()
                                            } else {
                                                error = getString(R.string.owncloud_login_failed)
                                            }
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.login_flow_login))
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    private val owncloudLight: ColorScheme
        get() = lightColorScheme(
            primary = Color(0xFF4A5E87),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD7E2FF),
            onPrimaryContainer = Color(0xFF011A3F),
            inversePrimary = Color(0xFFB2C7F5),
            secondary = Color(0xFF565E71),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFDAE2F9),
            onSecondaryContainer = Color(0xFF131B2C),
            tertiary = Color(0xFF705574),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFAD7FD),
            onTertiaryContainer = Color(0xFF29132E),
            surface = Color(0xFFFBF8FC),
            surfaceBright = Color(0xFFFBF8FC),
            surfaceDim = Color(0xFFDCD9DD),
            surfaceContainer = Color(0xFFF0EDF1),
            surfaceContainerHighest = Color(0xFFE4E1E5),
            surfaceContainerHigh = Color(0xFFEAE7EB),
            surfaceContainerLow = Color(0xFFF5F3F7),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1B1B1E),
            onSurfaceVariant = Color(0xFF43474F),
            inverseSurface = Color(0xFF303033),
            inverseOnSurface = Color(0xFFF3F0F4),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFDAD5),
            onErrorContainer = Color(0xFF410002),
            outline = Color(0xFF747780),
            outlineVariant = Color(0xFFC4C6D0),
            scrim = Color(0xFF000000),
        )

    private val owncloudDark: ColorScheme
        get() = darkColorScheme(
            primary = Color(0xFFB2C7F5),
            onPrimary = Color(0xFF1A3055),
            primaryContainer = Color(0xFF32476D),
            onPrimaryContainer = Color(0xFFD7E2FF),
            inversePrimary = Color(0xFF4A5E87),
            secondary = Color(0xFFBEC6DC),
            onSecondary = Color(0xFF283042),
            secondaryContainer = Color(0xFF3F4759),
            onSecondaryContainer = Color(0xFFDAE2F9),
            tertiary = Color(0xFFDDBCE0),
            onTertiary = Color(0xFF3F2844),
            tertiaryContainer = Color(0xFF573E5C),
            onTertiaryContainer = Color(0xFFFAD7FD),
            surface = Color(0xFF1B1B1E),
            surfaceBright = Color(0xFF39393C),
            surfaceDim = Color(0xFF131316),
            surfaceContainer = Color(0xFF1F1F22),
            surfaceContainerHighest = Color(0xFF353438),
            surfaceContainerHigh = Color(0xFF2A2A2D),
            surfaceContainerLow = Color(0xFF1B1B1E),
            surfaceContainerLowest = Color(0xFF0E0E11),
            onSurface = Color(0xFFE4E1E5),
            onSurfaceVariant = Color(0xFFC4C6D0),
            inverseSurface = Color(0xFFE4E1E5),
            inverseOnSurface = Color(0xFF303033),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690004),
            errorContainer = Color(0xFF930009),
            onErrorContainer = Color(0xFFFFB4AB),
            outline = Color(0xFF8E909A),
            outlineVariant = Color(0xFF43474F),
            scrim = Color(0xFF000000),
        )
}