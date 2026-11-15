package de.mm20.launcher2.ui.launcher.widgets

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.ui.R

private val steps = listOf(
    I18nR.string.widgets_tutorial_add_title to I18nR.string.au3_launcher3_tutorial_add_text,
    I18nR.string.widgets_tutorial_move_title to I18nR.string.widgets_tutorial_move_text,
    I18nR.string.widgets_tutorial_resize_title to I18nR.string.widgets_tutorial_resize_text,
    I18nR.string.widgets_tutorial_stack_title to I18nR.string.widgets_tutorial_stack_text,
)

/**
 * One-time, sequential coach-mark shown the first time the widgets screen appears, explaining
 * add/move/resize/stack. Finishing the last step or tapping Skip both count as "seen" - there's
 * no separate "don't show again" checkbox since there'd be nothing else to do with this dialog
 * once dismissed anyway. Can be re-triggered from Settings > Homescreen > Widgets ("Reset
 * tutorial").
 */
@Composable
fun WidgetsTutorialOverlay(onFinished: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val (titleRes, textRes) = steps[step]

    AlertDialog(
        onDismissRequest = onFinished,
        title = { TelosCyberHeading {  Text(stringResource(titleRes))  } },
        text = { Text(stringResource(textRes)) },
        confirmButton = {
            TextButton(onClick = {
                if (step < steps.lastIndex) step++ else onFinished()
            }) {
                Text(
                    stringResource(
                        if (step < steps.lastIndex) I18nR.string.widgets_tutorial_next
                        else I18nR.string.widgets_tutorial_done
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onFinished) {
                Text(stringResource(I18nR.string.widgets_tutorial_skip))
            }
        }
    )
}
