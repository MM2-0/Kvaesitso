package de.mm20.launcher2.ui.launcher.scaffold

import android.view.animation.PathInterpolator
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.SearchBarStyle
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.component.SearchBarLevel
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.helper.WallpaperBlur
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.launcher.searchbar.LauncherSearchBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.roundToInt

sealed interface ScaffoldAction {
    data object Search : ScaffoldAction
    data object Widgets : ScaffoldAction
    data class Shortcut(val searchable: SavableSearchable) : ScaffoldAction
    data object ScreenOff : ScaffoldAction
    data object Notifications : ScaffoldAction
    data object QuickSettings : ScaffoldAction
    data object Recents : ScaffoldAction
}

enum class ScaffoldAnimation {
    Rubberband,
    Push,
    ZoomIn,
}

internal data class ScaffoldGesture(
    val component: ScaffoldComponent,
    val animation: ScaffoldAnimation,
)

enum class SearchBarPosition {
    Top,
    Bottom,
}

internal data class ScaffoldConfiguration(
    /**
     * The main component
     */
    val homeComponent: ScaffoldComponent,
    /**
     * Search component that is activated when the search bar is tapped.
     */
    val searchComponent: SearchComponent,
    val swipeUp: ScaffoldGesture? = null,
    val swipeDown: ScaffoldGesture? = null,
    val swipeLeft: ScaffoldGesture? = null,
    val swipeRight: ScaffoldGesture? = null,
    val doubleTap: ScaffoldGesture? = null,
    val longPress: ScaffoldGesture? = null,
    /**
     * Position of the search bar
     */
    val searchBarPosition: SearchBarPosition = SearchBarPosition.Top,
    val searchBarStyle: SearchBarStyle = SearchBarStyle.Hidden,
    /**
     * If true, the search bar does not scroll out of view
     */
    val fixedSearchBar: Boolean = false,
    /**
     * Wallpaper blur radius. 0 to disable.
     */
    val wallpaperBlurRadius: Dp = 32.dp,
    /**
     * Show the navigation bar
     */
    val showNavBar: Boolean = true,
    val darkNavBarIcons: Boolean = false,
    /**
     * Show the status bar
     */
    val showStatusBar: Boolean = true,
    val darkStatusBarIcons: Boolean = false,
    /**
     * Finishes the activity when back is pressed while on home component.
     * Used for assistant mode.
     */
    val finishOnBack: Boolean = false,
    /**
     * If true, the home page is drawn with a background (and blur, if enabled)
     */
    val showBackgroundOnHome: Boolean = false,
) {
    val searchBarTap = ScaffoldGesture(
        component = searchComponent,
        animation = ScaffoldAnimation.ZoomIn,
    )

    /**
     * Returns true if the given config prevents the user from accessing search or settings,
     * so that the user is locked out and the launcher is soft-bricked
     */
    fun isUseless(): Boolean {
        return searchBarStyle == SearchBarStyle.Hidden &&
                listOfNotNull(
                    swipeUp,
                    swipeDown,
                    swipeLeft,
                    swipeRight,
                    doubleTap,
                    longPress,
                ).none { it.component.showSearchBar }
    }
}

private operator fun ScaffoldConfiguration.get(gesture: Gesture): ScaffoldGesture? {
    return when (gesture) {
        Gesture.SwipeUp -> swipeUp
        Gesture.SwipeDown -> swipeDown
        Gesture.SwipeLeft -> swipeLeft
        Gesture.SwipeRight -> swipeRight
        Gesture.DoubleTap -> doubleTap
        Gesture.LongPress -> longPress
        Gesture.TapSearchBar -> searchBarTap
    }
}

enum class Gesture(val orientation: Orientation?) {
    SwipeUp(Orientation.Vertical),
    SwipeDown(Orientation.Vertical),
    SwipeLeft(Orientation.Horizontal),
    SwipeRight(Orientation.Horizontal),
    DoubleTap(null),
    LongPress(null),
    TapSearchBar(null),
}

internal class LauncherScaffoldState(
    private val config: ScaffoldConfiguration,
    val size: Size,
    private val touchSlop: Float,
    /**
     * The threshold (in px) where a rubberband gesture is considered to be over the threshold
     * (releasing it would snap to the next page)
     */
    private val rubberbandThreshold: Float,
    /**
     * The minimum velocity (in px/s) where a fling will snap to the next page, regardless of the
     * current offset.
     */
    private val velocityThreshold: Float,
    private val maxSearchBarOffset: Float,
    private val onHapticFeedback: (HapticFeedbackType) -> Unit,
) {
    var currentOffset by mutableStateOf(Offset.Zero)
        private set
    var currentZOffset by mutableFloatStateOf(0f)
        private set
    var currentGesture by mutableStateOf<Gesture?>(null)
        private set

    val currentSearchBarOffset by derivedStateOf {
        if (config.fixedSearchBar) return@derivedStateOf 0f
        if (currentComponent?.showSearchBar == false) return@derivedStateOf homePageSearchBarOffset
        homePageSearchBarOffset * (1 - currentProgress) + secondaryPageSearchBarOffset * currentProgress
    }
    private var homePageSearchBarOffset by mutableFloatStateOf(0f)
    private var secondaryPageSearchBarOffset by mutableFloatStateOf(0f)

    var isSearchBarFocused by mutableStateOf(config.homeComponent is SearchComponent)

    val darkStatusBarIcons by derivedStateOf {
        config.darkStatusBarIcons && !(currentProgress == 1f && currentComponent?.drawBackground)
    }

    /**
     * True if any page is open, false if on home page.
     */
    var isSettledOnSecondaryPage by mutableStateOf(false)
        private set

    /**
     * 0..1 current progress
     * 0: home page
     * 1: any other page
     */
    val currentProgress by derivedStateOf {
        val dir = currentGesture ?: return@derivedStateOf 0f
        val gesture = config[dir] ?: return@derivedStateOf 0f

        if (dir.orientation == null) {
            return@derivedStateOf currentZOffset
        }

        if (gesture.animation == ScaffoldAnimation.Rubberband) {
            val offset =
                (currentOffset.x + currentOffset.y).absoluteValue.coerceAtMost(rubberbandThreshold)
            if (isSettledOnSecondaryPage) {
                1f - offset / (rubberbandThreshold * 2f)
            } else {
                offset / (rubberbandThreshold * 2f)
            }
        } else {
            if (dir.orientation == Orientation.Horizontal) {
                (currentOffset.x.absoluteValue / size.width).coerceIn(0f, 1f)
            } else {
                (currentOffset.y.absoluteValue / size.height).coerceIn(0f, 1f)
            }
        }
    }

    val currentAnimation by derivedStateOf {
        val dir = currentGesture ?: return@derivedStateOf null
        config[dir]?.animation
    }

    val currentComponent by derivedStateOf {
        val dir = currentGesture ?: return@derivedStateOf null
        config[dir]?.component
    }

    private val vectorAnimatable =
        Animatable(Offset.Zero, Offset.VectorConverter)

    private val floatAnimatable =
        Animatable(0f, Float.VectorConverter)

    suspend fun onDragStarted() {
        if (isLocked) return
        vectorAnimatable.stop()
        floatAnimatable.stop()
    }

    fun onDrag(offset: Offset) {
        if (isLocked) return
        if (currentGesture == null || (!isSettledOnSecondaryPage && currentOffset.x.absoluteValue <= touchSlop && currentOffset.y.absoluteValue <= touchSlop)) {
            currentGesture = getSwipeDirection(config, offset)
        }

        val direction = currentGesture ?: return

        val gesture = config[direction] ?: return

        if (gesture.animation == ScaffoldAnimation.Rubberband) {
            performRubberbandDrag(direction, currentOffset, offset)
        } else if (gesture.animation == ScaffoldAnimation.Push) {
            performPushDrag(direction, currentOffset, offset)
        }
    }

    private fun performRubberbandDrag(direction: Gesture, offset: Offset, delta: Offset) {
        val wasOverThreshold = currentOffset.x.absoluteValue > rubberbandThreshold ||
                currentOffset.y.absoluteValue > rubberbandThreshold

        val threshold = rubberbandThreshold * 1.5f
        currentOffset = when (direction) {
            Gesture.SwipeUp -> Offset(
                0f,
                (offset.y + delta.y).coerceIn(-threshold, threshold)
            )

            Gesture.SwipeDown -> Offset(
                0f,
                (offset.y + delta.y).coerceIn(-threshold, threshold)
            )

            Gesture.SwipeLeft -> Offset(
                (offset.x + delta.x).coerceIn(-threshold, threshold),
                0f
            )

            Gesture.SwipeRight -> Offset(
                (offset.x + delta.x).coerceIn(-threshold, threshold),
                0f
            )

            else -> Offset.Zero
        }

        val isOverThreshold = currentOffset.x.absoluteValue > rubberbandThreshold ||
                currentOffset.y.absoluteValue > rubberbandThreshold

        if (wasOverThreshold != isOverThreshold) {
            onHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    }

    /**
     * @param direction The direction of the drag (currentDragDirection)
     * @param offset The total offset of the drag (currentOffset)
     * @param delta The delta of the drag (offset)
     */
    private fun performPushDrag(direction: Gesture, offset: Offset, delta: Offset) {
        val wasOverThreshold =
            currentOffset.x.absoluteValue > size.width * 0.5f ||
                    currentOffset.y.absoluteValue > size.height * 0.5f

        currentOffset = when (direction) {
            Gesture.SwipeUp -> Offset(
                0f,
                (offset.y + delta.y).coerceIn(-size.height, 0f)
            )

            Gesture.SwipeDown -> Offset(
                0f,
                (offset.y + delta.y).coerceIn(0f, size.height)
            )

            Gesture.SwipeLeft -> Offset(
                (offset.x + delta.x).coerceIn(-size.width, 0f),
                0f
            )

            Gesture.SwipeRight -> Offset(
                (offset.x + delta.x).coerceIn(0f, size.width),
                0f
            )

            else -> Offset.Zero

        }

        val isOverThreshold =
            currentOffset.x.absoluteValue > size.width * 0.5f ||
                    currentOffset.y.absoluteValue > size.height * 0.5f

        if (wasOverThreshold != isOverThreshold) {
            onHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    }

    private fun getSwipeDirection(config: ScaffoldConfiguration, offset: Offset): Gesture? {
        when {
            (offset.x >= 0 && offset.y >= 0) -> {
                return if (offset.x > offset.y && config.swipeRight != null) {
                    Gesture.SwipeRight
                } else if (offset.x < offset.y && config.swipeDown != null) {
                    Gesture.SwipeDown
                } else {
                    null
                }
            }

            (offset.x < 0 && offset.y < 0) -> {
                return if (offset.x < offset.y && config.swipeLeft != null) {
                    Gesture.SwipeLeft
                } else if (offset.x > offset.y && config.swipeUp != null) {
                    Gesture.SwipeUp
                } else {
                    null
                }
            }

            (offset.x >= 0 && offset.y < 0) -> {
                return if (offset.x > -offset.y && config.swipeRight != null) {
                    Gesture.SwipeRight
                } else if (offset.x < -offset.y && config.swipeUp != null) {
                    Gesture.SwipeUp
                } else {
                    null
                }
            }

            (offset.x < 0 && offset.y >= 0) -> {
                return if (offset.x < -offset.y && config.swipeLeft != null) {
                    Gesture.SwipeLeft
                } else if (offset.x > -offset.y && config.swipeDown != null) {
                    Gesture.SwipeDown
                } else {
                    null
                }
            }
        }
        return null
    }

    suspend fun onDragStopped(velocity: Velocity) {
        if (isLocked) return
        if (currentGesture == null) {
            currentOffset = Offset.Zero
        }
        val direction = currentGesture ?: return
        val offset = currentOffset
        val wasPageOpen = isSettledOnSecondaryPage

        val gesture = config[direction] ?: return

        if (gesture.animation == ScaffoldAnimation.Rubberband) {
            performRubberbandFling(direction, offset, velocity)
        } else if (gesture.animation == ScaffoldAnimation.Push) {
            performPushFling(direction, offset, velocity)
        }

        if (isSettledOnSecondaryPage != wasPageOpen) {
            if (wasPageOpen) {
                deactivateSecondaryPage(gesture.component)
            } else {
                activateSecondaryPage(gesture.component)
            }
        }
    }

    private suspend fun performRubberbandFling(
        direction: Gesture,
        offset: Offset,
        velocity: Velocity
    ) {
        val wasSettledOnSecondaryPage = isSettledOnSecondaryPage

        if (offset.x <= -rubberbandThreshold || offset.x < 0f && velocity.x < -velocityThreshold) {
            isSettledOnSecondaryPage = !isSettledOnSecondaryPage
        } else if (offset.x >= rubberbandThreshold || offset.x > 0f && velocity.x > velocityThreshold) {
            isSettledOnSecondaryPage = !isSettledOnSecondaryPage
        } else if (offset.y <= -rubberbandThreshold || offset.y < 0f && velocity.y < -velocityThreshold) {
            isSettledOnSecondaryPage = !isSettledOnSecondaryPage
        } else if (offset.y >= rubberbandThreshold || offset.y > 0f && velocity.y > velocityThreshold) {
            isSettledOnSecondaryPage = !isSettledOnSecondaryPage
        }

        if (wasSettledOnSecondaryPage != isSettledOnSecondaryPage) {
            onHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }

        vectorAnimatable.snapTo(currentOffset)
        vectorAnimatable.animateTo(
            Offset.Zero,
            initialVelocity = Offset(velocity.x, velocity.y),
        ) {
            currentOffset = this.value
        }
    }

    private suspend fun performPushFling(
        direction: Gesture,
        offset: Offset,
        velocity: Velocity
    ) {
        val wasOverThreshold =
            currentOffset.x.absoluteValue > size.width * 0.5f ||
                    currentOffset.y.absoluteValue > size.height * 0.5f

        val lowerPage = when (direction) {
            Gesture.SwipeUp -> -size.height
            Gesture.SwipeDown -> 0f
            Gesture.SwipeLeft -> -size.width
            Gesture.SwipeRight -> 0f
            else -> return
        }

        val upperPage = if (direction.orientation == Orientation.Vertical) {
            lowerPage + size.height
        } else {
            lowerPage + size.width
        }

        val threshold = (upperPage + lowerPage) / 2f

        val targetOffset = if (direction.orientation == Orientation.Vertical) {
            if (offset.y > threshold && velocity.y > -velocityThreshold || velocity.y > velocityThreshold) Offset(
                0f,
                upperPage
            )
            else Offset(0f, lowerPage)
        } else {
            if (offset.x > threshold && velocity.x > -velocityThreshold || velocity.x > velocityThreshold) Offset(
                upperPage,
                0f
            )
            else Offset(lowerPage, 0f)
        }

        isSettledOnSecondaryPage = targetOffset != Offset.Zero

        val isOverThreshold =
            targetOffset.x.absoluteValue > size.width * 0.5f ||
                    targetOffset.y.absoluteValue > size.height * 0.5f

        if (wasOverThreshold != isOverThreshold) {
            onHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }

        vectorAnimatable.snapTo(currentOffset)
        vectorAnimatable.animateTo(
            targetOffset,
            initialVelocity = Offset(velocity.x, velocity.y),
        ) {
            currentOffset = this.value
        }
    }

    suspend fun onDoubleTap() {
        performTapGesture(Gesture.DoubleTap)
    }

    suspend fun onLongPress() {
        performTapGesture(Gesture.LongPress)
    }

    suspend fun onSearchBarTap() {
        if (currentComponent is SearchComponent) return
        openSearch()
    }

    /**
     * Called by components to notify the scaffold that a child component has been scrolled vertically.
     * Used to update the search bar position and visibility.
     * Note that we aren't interested in horizontal scroll events.
     *
     * @param delta The y delta of the scroll event, in pixels.
     * @param isAtTop True if scrollable component is at the top of the scrollable area and cannot be scrolled further up.
     * @param isAtBottom True if scrollable component is at the bottom of the scrollable area and cannot be scrolled further down.
     */
    fun onComponentScroll(delta: Float, isAtTop: Boolean, isAtBottom: Boolean) {
        if (isSettledOnSecondaryPage) {
            secondaryPageSearchBarOffset = if (config.searchBarPosition == SearchBarPosition.Top) {
                (secondaryPageSearchBarOffset - delta).coerceIn(-maxSearchBarOffset, 0f)
            } else {
                (secondaryPageSearchBarOffset + delta).coerceIn(0f, maxSearchBarOffset)
            }
        } else {
            homePageSearchBarOffset = if (config.searchBarPosition == SearchBarPosition.Top) {
                (homePageSearchBarOffset - delta).coerceIn(-maxSearchBarOffset, 0f)
            } else {
                (homePageSearchBarOffset + delta).coerceIn(0f, maxSearchBarOffset)
            }
        }
    }

    private val backInterpolation = PathInterpolator(0f, 0f, 0f, 1f)
    fun onPredictiveBack(progress: Float) {
        val gesture = currentGesture ?: return
        val anim = currentAnimation ?: return

        val progress = backInterpolation.getInterpolation(progress)

        when (gesture) {
            Gesture.TapSearchBar, Gesture.DoubleTap, Gesture.LongPress -> {
                currentZOffset = 1f - progress
            }

            else -> {
                val x = when (gesture) {
                    Gesture.SwipeLeft -> -1f
                    Gesture.SwipeRight -> 1f
                    else -> 0f
                }
                val y = when (gesture) {
                    Gesture.SwipeUp -> -1f
                    Gesture.SwipeDown -> 1f
                    else -> 0f
                }

                currentOffset = if (anim == ScaffoldAnimation.Push) {
                    Offset(
                        x * size.width * (1f - progress * 0.1f),
                        y * size.height * (1f - progress * 0.1f)
                    )
                } else {
                    Offset(
                        x * rubberbandThreshold * progress,
                        y * rubberbandThreshold * progress
                    )
                }

            }
        }
    }

    suspend fun onPredictiveBackCancel() {
        val gesture = currentGesture ?: return
        val anim = currentAnimation ?: return

        if (gesture.orientation == null) {
            floatAnimatable.snapTo(currentZOffset)
            floatAnimatable.animateTo(1f, animationSpec = tween(100)) {
                currentZOffset = this.value
            }
        } else {
            val targetOffset = if (anim == ScaffoldAnimation.Rubberband) {
                Offset.Zero
            } else {
                when (gesture) {
                    Gesture.SwipeLeft -> Offset(-size.width, 0f)
                    Gesture.SwipeRight -> Offset(size.width, 0f)
                    Gesture.SwipeUp -> Offset(0f, -size.height)
                    Gesture.SwipeDown -> Offset(0f, size.height)
                    else -> Offset.Zero
                }
            }

            vectorAnimatable.snapTo(currentOffset)
            vectorAnimatable.animateTo(targetOffset) {
                currentOffset = this.value
            }
        }
    }

    /**
     * End the predictive back gesture and return to the home page.
     * @param fast use a faster animation
     */
    suspend fun onPredictiveBackEnd(fast: Boolean = false) {
        val gesture = currentGesture ?: return
        val component = currentComponent ?: return

        isLocked = false
        isSettledOnSecondaryPage = false

        if (gesture.orientation == null) {
            floatAnimatable.snapTo(currentZOffset)
            floatAnimatable.animateTo(0f, animationSpec = tween(100)) {
                currentZOffset = this.value
            }
        } else {
            vectorAnimatable.snapTo(currentOffset)
            vectorAnimatable.animateTo(
                Offset.Zero,
                animationSpec = if (fast) tween(150) else spring()
            ) {
                currentOffset = this.value
            }
        }
        deactivateSecondaryPage(component)
    }

    private suspend fun performTapGesture(gesture: Gesture) {
        val component = config[gesture]?.component ?: return

        if (component.hapticFeedback) onHapticFeedback(HapticFeedbackType.LongPress)

        if (component is SearchComponent && gesture != Gesture.TapSearchBar) {
            openSearch()
            return
        }
        isLocked = true
        currentGesture = gesture

        floatAnimatable.snapTo(0f)
        floatAnimatable.animateTo(1f, animationSpec = tween(300)) {
            currentZOffset = this.value
        }

        isSettledOnSecondaryPage = true

        activateSecondaryPage(component)
    }

    /**
     * Unmounts the secondary page component and mounts the home page component.
     * Must be called after the animation is completed.
     */
    private suspend fun deactivateSecondaryPage(component: ScaffoldComponent) {
        config.homeComponent.onMount(this)
        component.onUnmount(this)
        currentGesture = null
        secondaryPageSearchBarOffset = 0f
        isSearchBarFocused = false
    }

    /**
     * Mount the secondary page component and dismiss it again if not permanent.
     * Must be called after the animation is completed.
     */
    private suspend fun activateSecondaryPage(component: ScaffoldComponent) {
        component.onMount(this)

        if (!component.permanent) {
            delay(component.resetDelay)
            isSettledOnSecondaryPage = false
            currentOffset = Offset.Zero
            component.onUnmount(this)
            currentGesture = null
            isLocked = false
        } else {
            config.homeComponent.onUnmount(this)
            homePageSearchBarOffset = 0f
        }
    }

    private suspend fun openSearch() {
        if (currentComponent != null && currentComponent !is SearchComponent) {
            onPredictiveBackEnd(fast = true)
        }

        if (config.homeComponent is SearchComponent) {
            return
        }

        // If there is any swipe gesture that is a SearchComponent, this takes precedence over the tap
        // This allows to close the search with a reversed swipe
        val gestures =
            listOf(Gesture.SwipeDown, Gesture.SwipeUp, Gesture.SwipeLeft, Gesture.SwipeRight)
        val gesture = gestures.find { config[it]?.component is SearchComponent }

        if (gesture == null) {
            performTapGesture(Gesture.TapSearchBar)
            return
        }
        currentGesture = gesture
        val anim = config[gesture]?.animation ?: return

        if (anim == ScaffoldAnimation.Rubberband) {
            val targetOffset = when (gesture) {
                Gesture.SwipeLeft -> Offset(-rubberbandThreshold, 0f)
                Gesture.SwipeRight -> Offset(rubberbandThreshold, 0f)
                Gesture.SwipeUp -> Offset(0f, -rubberbandThreshold)
                Gesture.SwipeDown -> Offset(0f, rubberbandThreshold)
                else -> Offset.Zero
            }

            vectorAnimatable.snapTo(currentOffset)
            vectorAnimatable.animateTo(targetOffset) {
                currentOffset = this.value
            }
            isSettledOnSecondaryPage = true
            vectorAnimatable.animateTo(Offset.Zero) {
                currentOffset = this.value
            }
            activateSecondaryPage(config[gesture]!!.component)
        } else {
            val targetOffset = when (gesture) {
                Gesture.SwipeLeft -> Offset(-size.width, 0f)
                Gesture.SwipeRight -> Offset(size.width, 0f)
                Gesture.SwipeUp -> Offset(0f, -size.height)
                Gesture.SwipeDown -> Offset(0f, size.height)
                else -> Offset.Zero
            }
            vectorAnimatable.snapTo(currentOffset)
            vectorAnimatable.animateTo(targetOffset) {
                currentOffset = this.value
            }
            isSettledOnSecondaryPage = true
            activateSecondaryPage(config[gesture]!!.component)
        }
    }

    suspend fun reset() {
        isSearchBarFocused = false
        currentOffset = Offset.Zero
        isLocked = false
        isSettledOnSecondaryPage = false

        currentComponent?.let {
            deactivateSecondaryPage(it)
        }
    }

    /**
     * If true, all gestures are ignored.
     */
    var isLocked = false
}

@Composable
internal fun LauncherScaffold(
    modifier: Modifier,
    config: ScaffoldConfiguration,
) {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val density = LocalDensity.current
    val windowInsets = WindowInsets.safeDrawing.asPaddingValues()
    val systemBarInsets = WindowInsets.systemBars

    val searchVM = viewModel<SearchVM>()
    val searchActions = searchVM.searchActionResults

    val searchBarHeight by remember {
        derivedStateOf {
            if (searchActions.isEmpty()) 64.dp else 112.dp
        }
    }

    BoxWithConstraints(
        modifier = modifier,
    ) {
        val width = this.maxWidth
        val height = this.maxHeight
        val widthPx = width.toPixels()
        val heightPx = height.toPixels()

        val touchSlop = LocalViewConfiguration.current.touchSlop
        val minFlingVelocity = 125.dp.toPixels()
        val rubberbandThreshold = 64.dp.toPixels()
        val maxSearchBarOffset = (
                if (config.searchBarPosition == SearchBarPosition.Top) systemBarInsets.getTop(
                    density
                )
                else systemBarInsets.getBottom(density)
                ) + 128.dp.toPixels()

        val hapticFeedback = LocalHapticFeedback.current

        val state =
            remember(widthPx, heightPx, touchSlop, rubberbandThreshold, minFlingVelocity, config) {
                LauncherScaffoldState(
                    config = config,
                    size = Size(widthPx, heightPx),
                    touchSlop = touchSlop,
                    rubberbandThreshold = rubberbandThreshold,
                    velocityThreshold = minFlingVelocity,
                    maxSearchBarOffset = maxSearchBarOffset,
                    onHapticFeedback = {
                        hapticFeedback.performHapticFeedback(it)
                    }
                )
            }

        LaunchedEffect(state) {
            var pauseTime = 0L
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    if (pauseTime > 0L && System.currentTimeMillis() - pauseTime > 5000L) {
                        state.reset()
                        searchVM.reset()
                    }
                } finally {
                    pauseTime = System.currentTimeMillis()
                }
            }
        }

        if (config.wallpaperBlurRadius > 0.dp) {
            val wallpaperBlur by animateIntAsState(
                if (state.currentProgress >= 0.5f || config.showBackgroundOnHome) 8.dp.toPixels().toInt() else 0
            )
            WallpaperBlur { wallpaperBlur }
        }

        if (!config.finishOnBack || state.currentProgress > 0) {
            PredictiveBackHandler {
                try {
                    it.collect {
                        state.onPredictiveBack(it.progress)
                    }
                    scope.launch { state.onPredictiveBackEnd() }
                } catch (_: CancellationException) {
                    scope.launch { state.onPredictiveBackCancel() }
                }
            }
        }

        val draggableState = rememberDraggable2DState {
            state.onDrag(it)
        }

        val nestedScrollConnection = remember(state) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero

                    if (state.currentProgress != 0f && state.currentProgress != 1f) {
                        draggableState.dispatchRawDelta(available)
                        return available
                    }

                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (source == NestedScrollSource.UserInput) {
                        draggableState.dispatchRawDelta(available)
                        return available
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (state.currentProgress != 0f && state.currentProgress != 1f) {
                        state.onDragStopped(available)
                        return available
                    }
                    return super.onPreFling(available)
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity
                ): Velocity {
                    if (available == Velocity.Zero) {
                        return available
                    }

                    // Threshold for nested scroll flings is 15 times higher to avoid accidental
                    // page changes
                    if (available.x.absoluteValue > minFlingVelocity * 15f || available.y.absoluteValue > minFlingVelocity * 15f) {
                        state.onDragStopped(available)
                    } else {
                        state.onDragStopped(Velocity.Zero)
                    }
                    return available
                }
            }
        }


        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .draggable2D(
                    state = draggableState,
                    onDragStarted = {
                        scope.launch {
                            state.onDragStarted()
                        }
                    },
                    onDragStopped = { velocity ->
                        scope.launch {
                            state.onDragStopped(velocity)
                        }
                    }
                )
        ) {
            val insets = windowInsets.let {
                PaddingValues(
                    start = it.calculateStartPadding(LocalLayoutDirection.current),
                    end = it.calculateEndPadding(LocalLayoutDirection.current),
                    top = it.calculateTopPadding() + if (config.searchBarPosition == SearchBarPosition.Top) searchBarHeight else 0.dp,
                    bottom = it.calculateBottomPadding() + if (config.searchBarPosition == SearchBarPosition.Bottom) searchBarHeight else 0.dp
                )
            }

            config.homeComponent.Component(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (config.showBackgroundOnHome) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f)
                        else Color.Transparent)
                    .combinedClickable(
                        enabled = config.longPress != null || config.doubleTap != null,
                        onClick = {},
                        onLongClick = if (config.longPress != null) {
                            { scope.launch { state.onLongPress() } }
                        } else null,
                        onDoubleClick = if (config.doubleTap != null) {
                            { scope.launch { state.onDoubleTap() } }
                        } else null,
                        hapticFeedbackEnabled = false,
                        indication = null,
                        interactionSource = null,
                    )
                    .homePageAnimation(state),
                if (config.searchBarStyle == SearchBarStyle.Hidden) windowInsets else insets,
                state
            )

            SecondaryPage(
                state = state,
                config = config,
                modifier = Modifier
                    .fillMaxSize(),
                insets = insets,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .searchBarAnimation(state, config, windowInsets)
            ) {
                LauncherSearchBar(
                    modifier = Modifier
                        .align(
                            if (config.searchBarPosition == SearchBarPosition.Top) Alignment.TopCenter
                            else Alignment.BottomCenter
                        ),
                    searchBarOffset = { state.currentSearchBarOffset.roundToInt() },
                    style = SearchBarStyle.Solid,
                    focused = state.isSearchBarFocused,
                    actions = searchActions,
                    level = { SearchBarLevel.Raised },
                    bottomSearchBar = config.searchBarPosition == SearchBarPosition.Bottom,
                    onFocusChange = {
                        if (it) {
                            scope.launch { state.onSearchBarTap() }
                        }
                        state.isSearchBarFocused = it
                    },
                )
            }
        }
    }
}

@Composable
private fun SecondaryPage(
    state: LauncherScaffoldState,
    config: ScaffoldConfiguration,
    modifier: Modifier = Modifier,
    insets: PaddingValues,
) {
    val components = remember(config) {
        setOfNotNull(
            config.swipeUp?.component,
            config.swipeDown?.component,
            config.swipeLeft?.component,
            config.swipeRight?.component,
            config.doubleTap?.component,
            config.longPress?.component,
            config.searchComponent,
        )
    }

    val composables = remember(config) {
        components.associateWith {
            movableContentOf<Modifier, PaddingValues, LauncherScaffoldState> { modifier, insets, state ->
                it.Component(
                    modifier = modifier,
                    insets = insets,
                    state = state
                )
            }
        }
    }

    val component = state.currentComponent

    if (component != null) {

        val mod = modifier
            .fillMaxSize()
            .secondaryPageAnimation(state, MaterialTheme.colorScheme.surfaceContainer)
        val composable = composables[component]

        composable?.invoke(mod, insets, state)
    }

    // Keep other components alive, but out of the viewport
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(x = state.size.width.toInt(), y = 0) }
    ) {
        for ((k, v) in composables) {
            if (k == component) continue
            v.invoke(modifier, insets, state)
        }
    }

}

private fun Modifier.homePageAnimation(
    state: LauncherScaffoldState,
): Modifier {
    val dir = state.currentGesture ?: return this
    val component = state.currentComponent ?: return this

    if (state.currentAnimation == ScaffoldAnimation.Rubberband) {
        return this then component.homePageModifier(
            state,
            Modifier
                .offset {
                    IntOffset(
                        x = if (dir.orientation == Orientation.Horizontal) state.currentOffset.x.toInt() else 0,
                        y = if (dir.orientation == Orientation.Vertical) state.currentOffset.y.toInt() else 0
                    )
                }
                .alpha(1f - state.currentProgress))
    }
    return this then component.homePageModifier(state, Modifier.offset {
        IntOffset(
            x = if (dir.orientation == Orientation.Horizontal) state.currentOffset.x.toInt() else 0,
            y = if (dir.orientation == Orientation.Vertical) state.currentOffset.y.toInt() else 0
        )
    })
}

private fun Modifier.secondaryPageAnimation(
    state: LauncherScaffoldState,
    backgroundColor: Color,
): Modifier {
    val dir = state.currentGesture ?: return this
    val component = state.currentComponent ?: return this

    val background =
        if (component.drawBackground) backgroundColor.copy(alpha = 0.85f * state.currentProgress) else Color.Transparent

    if (state.currentAnimation == ScaffoldAnimation.Rubberband) {
        return this then Modifier
            .background(background)
            .graphicsLayer {
                translationX =
                    if (dir.orientation == Orientation.Horizontal) state.currentOffset.x else 0f
                translationY =
                    if (dir.orientation == Orientation.Vertical) state.currentOffset.y else 0f
                if (state.isSettledOnSecondaryPage) {
                    alpha = (state.currentProgress).coerceAtMost(1f)
                    scaleX = 1f - ((1f - state.currentProgress) * 0.03f)
                    scaleY = 1f - ((1f - state.currentProgress) * 0.03f)
                } else {
                    alpha = (state.currentProgress * 2f - 1f).coerceAtLeast(0f)
                }
            }

    }
    if (state.currentAnimation == ScaffoldAnimation.ZoomIn) {
        return this then Modifier
            .background(background)
            .graphicsLayer {
                scaleX = 1f - ((1f - state.currentProgress) * 0.03f)
                scaleY = 1f - ((1f - state.currentProgress) * 0.03f)
                alpha = (state.currentProgress).coerceAtMost(1f)
            }
    }

    return this then Modifier
        .offset {
            when (state.currentGesture) {
                Gesture.SwipeUp -> IntOffset(0, state.size.height.toInt())
                Gesture.SwipeDown -> IntOffset(0, -state.size.height.toInt())
                Gesture.SwipeLeft -> IntOffset(state.size.width.toInt(), 0)
                Gesture.SwipeRight -> IntOffset(-state.size.width.toInt(), 0)
                else -> IntOffset.Zero
            }
        }
        .offset {
            IntOffset(
                x = if (state.currentGesture?.orientation == Orientation.Horizontal) state.currentOffset.x.toInt() else 0,
                y = if (state.currentGesture?.orientation == Orientation.Vertical) state.currentOffset.y.toInt() else 0
            )
        }
        .background(background)
}

private fun Modifier.searchBarAnimation(
    state: LauncherScaffoldState,
    config: ScaffoldConfiguration,
    insets: PaddingValues,
): Modifier {
    val offsetFactor = if (config.searchBarPosition == SearchBarPosition.Top) -1f else 1f

    val component = state.currentComponent
    val anim = state.currentAnimation

    val progress = if (anim == ScaffoldAnimation.Rubberband) {
        (state.currentProgress * 2f).coerceAtMost(1f)
    } else {
        state.currentProgress
    }

    val systemBarInset =
        if (config.searchBarPosition == SearchBarPosition.Top) insets.calculateTopPadding() else insets.calculateBottomPadding()

    val offset = if (config.searchBarStyle == SearchBarStyle.Hidden) {
        offsetFactor * (1 - progress).pow(2) * (128.dp + systemBarInset)
    } else {
        0.dp
    }

    /*if (state.currentAnimation == ScaffoldAnimation.Rubberband) {
        return this then component.searchBarModifier(
            state,
            Modifier.graphicsLayer {
                translationY =
                    if (dir.orientation == Orientation.Vertical) state.currentOffset.y * 0.5f else 0f
            }
        )
    }*/

    val modifier =
        if (component?.showSearchBar == false && config.searchBarStyle == SearchBarStyle.Hidden) {
            Modifier.alpha(0f)
        } else {
            Modifier.offset(y = offset)
        }

    return this then (component?.searchBarModifier(state, modifier) ?: modifier)
}