package de.mm20.launcher2.ui.launcher.widgets.smartspacer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.view.LayoutInflater
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.viewinterop.AndroidView
import com.kieronquinn.app.smartspacer.sdk.client.SmartspacerClient
import de.mm20.launcher2.ui.R

@Composable
fun SmartspacerWidget() {
    val context = LocalContext.current
    val smartspacerClient = remember { SmartspacerClient.getInstance(context) }

    // TODO: fix hardcoded size in layout xml
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        factory = { LayoutInflater.from(context).inflate(R.layout.smartspace_view, null) }
    )

    DisposableEffect(smartspacerClient) {
        onDispose {
            smartspacerClient.close()
        }
    }
}