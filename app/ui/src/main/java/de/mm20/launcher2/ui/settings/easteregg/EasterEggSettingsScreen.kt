package de.mm20.launcher2.ui.settings.easteregg

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable

@Serializable
data object EasterEggSettingsRoute: NavKey

@Composable
fun EasterEggSettingsScreen() {
    val viewModel: EasterEggSettingsScreenVM = viewModel()
    PreferenceScreen(title = stringResource(R.string.preference_screen_about)) {
        item {
            val easterEgg by viewModel.easterEgg.collectAsState()
            val bgAlpha by animateFloatAsState(if (easterEgg) 1f else 0f)
            val textColor by animateColorAsState(if (easterEgg) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onBackground)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = bgAlpha))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = stringResource(id = R.string.easter_egg_text),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = textColor
                    )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            viewModel.setEasterEgg(!easterEgg)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !easterEgg,
                        enter = scaleIn(),
                        exit = scaleOut()
                    ) {
                        Text(text = "\uD83C\uDF82", fontSize = 120.sp)
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = easterEgg,
                        enter = scaleIn(),
                        exit = scaleOut()
                    ) {
                        Text(text = "❤️", fontSize = 120.sp)
                    }
                }
            }
        }
    }
}