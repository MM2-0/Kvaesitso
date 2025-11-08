package de.mm20.launcher2.ui.settings.buildinfo

import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.BuildConfig
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
data object BuildInfoSettingsRoute: NavKey

@Composable
fun BuildInfoSettingsScreen() {
    val viewModel: BuildInfoSettingsScreenVM = viewModel()
    val context = LocalContext.current
    val buildFeatures by viewModel.buildFeatures.collectAsState(emptyMap())
    PreferenceScreen(title = stringResource(R.string.preference_screen_buildinfo)) {
        item {
            PreferenceCategory {
                Preference(title = "Build type", summary = BuildConfig.BUILD_TYPE)
                var buildSignature by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(null) {
                    val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val pi = context.packageManager.getPackageInfo(
                            context.packageName,
                            PackageManager.GET_SIGNING_CERTIFICATES
                        )
                        pi.signingInfo?.apkContentsSigners?.firstOrNull()
                    } else {
                        val pi = context.packageManager.getPackageInfo(
                            context.packageName,
                            PackageManager.GET_SIGNATURES
                        )
                        pi.signatures?.firstOrNull()
                    }
                    val signatureHash = if (signature != null) {
                        val digest = MessageDigest.getInstance("SHA")
                        digest.update(signature.toByteArray())
                        Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
                    } else "null"
                    buildSignature = signatureHash
                }
                Preference(title = "Signature hash", summary = buildSignature)
            }
        }
        item {
            PreferenceCategory(title = "Features") {
                for (feature in buildFeatures) {
                    Preference(
                        title = feature.key,
                        summary = if (feature.value) "YES" else "NO"
                    )
                }
            }
        }
    }
}