package de.mm20.launcher2.ui.launcher.scaffold

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class DebugComponent : ScaffoldComponent {
    override val content: ComponentContent = @Composable { modifier, insets, progress ->
        Box(
            modifier = modifier
                .background(Color.Blue)
                .padding(insets)
                .background(Color.Green),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                for (i in 0 until 10) {
                    Box(modifier = Modifier.height(200.dp).fillMaxWidth().padding(bottom = 8.dp).background(Color.Red))
                }
            }
        }
    }
}