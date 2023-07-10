package de.mm20.launcher2.ui.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * Consumes all scrolling, so that the parent doesn't scroll.
 */
fun Modifier.consumeAllScrolling() = this.nestedScroll(ConsumeAllScrollConnection)

private object ConsumeAllScrollConnection:
    NestedScrollConnection {

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        return available
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        return available
    }
}