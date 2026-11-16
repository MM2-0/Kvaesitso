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
package com.vitorpamplona.amethyst.commons.ui.navigation.host

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import com.vitorpamplona.amethyst.commons.model.navigation.NavStackEntry
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.lockScope
import com.vitorpamplona.amethyst.commons.ui.layouts.CappedScreenContent
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.LocalNavStackEntry
import com.vitorpamplona.amethyst.commons.ui.privacylock.PrivacyLockGate
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializerOrNull
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass

/**
 * The shell's current layout tier, mirrored for the transition specs below. Transition
 * lambdas run when a navigation starts — outside composition — so they can't read
 * LocalScreenLayout; AppNavigation mirrors the spec here instead.
 *
 * One navigation grammar, tier-scaled motion: phones keep full-width slides (a pushed
 * screen physically stacks on top), while large screens use short shared-axis moves —
 * content there swaps inside a persistent shell, and a full-pane slide from the right
 * reads as disconnected when the click came from the docked drawer on the left.
 */
object NavTransitionTier {
    @Volatile
    var isLargeScreen: Boolean = false
}

/**
 * Applies the wide-pane reading-column cap ([CappedScreenContent]) to a destination unless
 * it opted out with `capWidth = false`. Every builder below routes through this, so all
 * destinations — top bars included — share the centered column on large screens by default.
 */
@Composable
fun MaybeCappedScreen(
    capWidth: Boolean,
    content: @Composable () -> Unit,
) {
    if (capWidth) {
        CappedScreenContent(content)
    } else {
        content()
    }
}

/** How a destination moves in and out, picked by the builder it was declared with. */
enum class NavFamily {
    /** A drill-in: slides in from the end, and back out to it. */
    END,

    /** A modal: rises from the bottom, and drops back down. */
    BOTTOM,

    /** Fades, like a tab switch. */
    NONE,
}

/**
 * What a destination's content can reach: [nav], the navigation of the screen being drawn. It is
 * handed in when the screen draws, not when it is registered, so one table serves every host (the
 * main window, a deck column, a screen window) and each screen's requests carry it as their origin.
 */
@Stable
class DestinationScope(
    val nav: INav,
)

/** One destination: how it animates, whether it takes the reading-column cap, and what it draws. */
class NavDestination(
    private val route: KClass<out Route>,
    val family: NavFamily,
    val capWidth: Boolean,
    val content: @Composable DestinationScope.(Route) -> Unit,
) {
    /**
     * The route's serial name, which R8 leaves alone (unlike its class name). Resolved on first use:
     * this module has no serialization compiler plugin, so the lookup is reflective, and doing it for
     * every destination as the table is built would cost the first frame. Only screen time asks, and
     * only for screens the user opens. Falls back to the class name if the lookup ever fails.
     */
    @OptIn(InternalSerializationApi::class)
    val serialName: String by lazy {
        route.serializerOrNull()?.descriptor?.serialName ?: route.simpleName ?: ""
    }
}

/**
 * Every screen the app can show, keyed by route class. Built once by the front end with the
 * builders below, then read by the entry provider (what to draw) and the transition specs (how to
 * move). Navigation 3 hands both the route itself, so there is no graph to declare up front.
 *
 * A route the registry does not know is a programming error, surfaced the first time it is opened,
 * unless the front end set an [unavailable] screen: one that carries only part of the app (the
 * desktop has no Health Connect or QR camera) shows that instead, since a synced bottom-bar item,
 * a link or a shared menu can still lead to a screen it doesn't have.
 */
class NavDestinations {
    private val destinations = HashMap<KClass<out Route>, NavDestination>()
    private var unavailableScreen: NavDestination? = null

    fun register(
        klass: KClass<out Route>,
        family: NavFamily,
        capWidth: Boolean,
        content: @Composable DestinationScope.(Route) -> Unit,
    ) {
        destinations[klass] = NavDestination(klass, family, capWidth, content)
    }

    /** What a route nothing registered draws, instead of failing. Front ends that register every screen leave it unset. */
    fun unavailable(content: @Composable DestinationScope.(Route) -> Unit) {
        unavailableScreen = NavDestination(Route::class, NavFamily.END, capWidth = true, content)
    }

    /** Whether [route] has a screen of its own here, so entry points to it are worth showing. */
    fun has(route: Route): Boolean = route::class in destinations

    fun of(route: Route): NavDestination = destinations[route::class] ?: unavailableScreen ?: error("No destination registered for ${route::class.simpleName}")

    /** The [NavDestination.serialName] of [route], or null for a route nothing registered. */
    fun serialNameOf(route: Route): String? = destinations[route::class]?.serialName

    fun familyOf(entry: NavStackEntry?): NavFamily = entry?.let { (destinations[it.route::class] ?: unavailableScreen)?.family } ?: NavFamily.NONE

    /**
     * The Navigation 3 entry for [entry]: its screen, capped as declared, drawn with the navigation
     * [navFor] gives this entry (so its requests and back arrow answer for this screen, not for the
     * top), and with [LocalNavStackEntry] provided for shared UI around it.
     */
    internal fun entryFor(
        entry: NavStackEntry,
        navFor: (NavStackEntry) -> INav,
    ): NavEntry<NavStackEntry> =
        NavEntry(
            key = entry,
            contentKey = entry.contentKey,
            metadata = mapOf(NAV_STACK_ENTRY to entry),
        ) {
            val scope = remember(entry) { DestinationScope(navFor(entry)) }
            // Read as it is drawn, not when the entry was made: a screen can be handed a route of
            // another destination in place (the event redirect becomes the note it found, in a tab),
            // and the old destination's content cast the new route to its own and crashed.
            val route = entry.route
            val destination = of(route)
            CompositionLocalProvider(LocalNavStackEntry provides entry) {
                // A screen behind a privacy lock is not composed until it is unlocked.
                PrivacyLockGate(route.lockScope()) {
                    MaybeCappedScreen(destination.capWidth) { with(destination) { scope.content(route) } }
                }
            }
        }

    companion object {
        /** [NavEntry.metadata] key carrying the [NavStackEntry], which the transition specs read. */
        const val NAV_STACK_ENTRY = "amethyst.navStackEntry"
    }
}

/** Stock fade-transition destination, capped to the reading-column width on wide panes. */
inline fun <reified T : Route> NavDestinations.composableCapped(noinline content: @Composable DestinationScope.() -> Unit) {
    register(T::class, NavFamily.NONE, capWidth = true) { content() }
}

/** [composableCapped], for a route that carries arguments. */
inline fun <reified T : Route> NavDestinations.composableCappedArgs(noinline content: @Composable DestinationScope.(T) -> Unit) {
    register(T::class, NavFamily.NONE, capWidth = true) { content(it as T) }
}

/** Stock fade-transition destination at full width. */
inline fun <reified T : Route> NavDestinations.composable(noinline content: @Composable DestinationScope.() -> Unit) {
    register(T::class, NavFamily.NONE, capWidth = false) { content() }
}

inline fun <reified T : Route> NavDestinations.composableArgs(
    capWidth: Boolean = true,
    noinline content: @Composable DestinationScope.(T) -> Unit,
) {
    register(T::class, NavFamily.NONE, capWidth) { content(it as T) }
}

inline fun <reified T : Route> NavDestinations.composableFromEnd(
    capWidth: Boolean = true,
    noinline content: @Composable DestinationScope.() -> Unit,
) {
    register(T::class, NavFamily.END, capWidth) { content() }
}

inline fun <reified T : Route> NavDestinations.composableFromEndArgs(
    capWidth: Boolean = true,
    noinline content: @Composable DestinationScope.(T) -> Unit,
) {
    register(T::class, NavFamily.END, capWidth) { content(it as T) }
}

inline fun <reified T : Route> NavDestinations.composableFromBottom(
    capWidth: Boolean = true,
    noinline content: @Composable DestinationScope.() -> Unit,
) {
    register(T::class, NavFamily.BOTTOM, capWidth) { content() }
}

inline fun <reified T : Route> NavDestinations.composableFromBottomArgs(
    capWidth: Boolean = true,
    noinline content: @Composable DestinationScope.(T) -> Unit,
) {
    register(T::class, NavFamily.BOTTOM, capWidth) { content(it as T) }
}

val slideInVerticallyFromBottom = slideInVertically(animationSpec = tween(), initialOffsetY = { it })
val slideOutVerticallyToBottom = slideOutVertically(animationSpec = tween(), targetOffsetY = { it })

val slideInHorizontallyFromEnd = slideInHorizontally(animationSpec = tween(), initialOffsetX = { it })
val slideOutHorizontallyToEnd = slideOutHorizontally(animationSpec = tween(), targetOffsetX = { it })

val scaleIn = scaleIn(animationSpec = tween(), initialScale = 0.9f)
val scaleOut = scaleOut(animationSpec = tween(), targetScale = 0.9f)

/** Fraction of the pane a shared-axis move travels on large screens — a nudge, not a fly-in. */
private const val SHARED_AXIS_FRACTION = 10

val sharedAxisEnterFromEnd = slideInHorizontally(animationSpec = tween()) { it / SHARED_AXIS_FRACTION } + fadeIn(animationSpec = tween())
val sharedAxisExitToEnd = slideOutHorizontally(animationSpec = tween()) { it / SHARED_AXIS_FRACTION } + fadeOut(animationSpec = tween())
val sharedAxisEnterFromBottom = slideInVertically(animationSpec = tween()) { it / SHARED_AXIS_FRACTION } + fadeIn(animationSpec = tween())
val sharedAxisExitToBottom = slideOutVertically(animationSpec = tween()) { it / SHARED_AXIS_FRACTION } + fadeOut(animationSpec = tween())

// The outgoing/incoming screen *behind* a push: on phones the pushed screen covers it, so a
// slight scale is enough; on large screens the incoming screen fades, so the one behind must
// fade too or both stay visible mid-transition.
val fadeScaleOut = scaleOut + fadeOut(animationSpec = tween())
val fadeScaleIn = scaleIn + fadeIn(animationSpec = tween())

fun enterFromEnd() = if (NavTransitionTier.isLargeScreen) sharedAxisEnterFromEnd else slideInHorizontallyFromEnd

fun popExitToEnd() = if (NavTransitionTier.isLargeScreen) sharedAxisExitToEnd else slideOutHorizontallyToEnd

fun enterFromBottom() = if (NavTransitionTier.isLargeScreen) sharedAxisEnterFromBottom else slideInVerticallyFromBottom

fun popExitToBottom() = if (NavTransitionTier.isLargeScreen) sharedAxisExitToBottom else slideOutVerticallyToBottom

fun exitBehind() = if (NavTransitionTier.isLargeScreen) fadeScaleOut else scaleOut

fun popEnterFromBehind() = if (NavTransitionTier.isLargeScreen) fadeScaleIn else scaleIn

/** The fade every tab switch, and every destination declared without a family, falls back to. */
val navShellFadeIn = fadeIn(animationSpec = tween(200))
val navShellFadeOut = fadeOut(animationSpec = tween(200))

/** The screen a single-pane scene shows. */
private fun Scene<NavStackEntry>.navStackEntry(): NavStackEntry? = entries.lastOrNull()?.metadata?.get(NavDestinations.NAV_STACK_ENTRY) as? NavStackEntry

/**
 * A push: the new screen comes in the way its family enters, and the one it covers steps behind.
 * A tab root (a bottom-bar or rail tap) fades instead of sliding, whatever its family: the user
 * switched sections, they did not drill into one.
 */
internal fun NavDestinations.pushTransition(scope: AnimatedContentTransitionScope<Scene<NavStackEntry>>): ContentTransform {
    val leaving = scope.initialState.navStackEntry()
    val arriving = scope.targetState.navStackEntry()
    val switchesTab = arriving?.tabRoot == true

    val enter: EnterTransition =
        when (familyOf(arriving)) {
            NavFamily.END -> if (switchesTab) navShellFadeIn else enterFromEnd()
            NavFamily.BOTTOM -> enterFromBottom()
            NavFamily.NONE -> navShellFadeIn
        }
    val exit: ExitTransition =
        when (familyOf(leaving)) {
            NavFamily.END -> if (switchesTab) navShellFadeOut else exitBehind()
            NavFamily.BOTTOM -> exitBehind()
            NavFamily.NONE -> navShellFadeOut
        }
    return enter togetherWith exit
}

/**
 * A pop — a back press, or a predictive back gesture, which runs the same motion under the
 * finger: a modal drops down, a drill-in leaves toward the end, a tab root fades; whatever is
 * revealed grows back in from behind.
 */
internal fun NavDestinations.popTransition(scope: AnimatedContentTransitionScope<Scene<NavStackEntry>>): ContentTransform {
    val leaving = scope.initialState.navStackEntry()
    val revealed = scope.targetState.navStackEntry()
    val leavesTab = leaving?.tabRoot == true

    val enter: EnterTransition =
        when (familyOf(revealed)) {
            NavFamily.END -> if (leavesTab) navShellFadeIn else popEnterFromBehind()
            NavFamily.BOTTOM -> popEnterFromBehind()
            NavFamily.NONE -> navShellFadeIn
        }
    val exit: ExitTransition =
        when (familyOf(leaving)) {
            NavFamily.END -> if (leavesTab) navShellFadeOut else popExitToEnd()
            NavFamily.BOTTOM -> popExitToBottom()
            NavFamily.NONE -> navShellFadeOut
        }
    return enter togetherWith exit
}

/**
 * The app's [NavDestinations], for shared UI that offers a way into a screen (a drawer row, a
 * settings entry, a menu item) and should hide it where the front end has no such screen. Null
 * outside the logged-in shell, where everything counts as available.
 */
val LocalNavDestinations = staticCompositionLocalOf<NavDestinations?> { null }

/** Whether [route] has a screen in this front end; true when no table is in scope. */
@Composable
fun canOpen(route: Route): Boolean = LocalNavDestinations.current?.has(route) ?: true
