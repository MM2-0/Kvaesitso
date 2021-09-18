package de.mm20.launcher2.ui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.accompanist.insets.navigationBarsHeight
import com.google.accompanist.insets.statusBarsHeight

@Composable
fun NavBarSpacer() = Spacer(modifier = Modifier.navigationBarsHeight())

@Composable
fun StatusBarSpacer() = Spacer(modifier = Modifier.statusBarsHeight())