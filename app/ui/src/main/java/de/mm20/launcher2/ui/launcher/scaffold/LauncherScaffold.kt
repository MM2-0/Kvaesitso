package de.mm20.launcher2.ui.launcher.scaffold

import android.app.WallpaperManager
import android.view.animation.PathInterpolator
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.waterfall
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.SearchBarStyle
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.ui.component.SearchBarLevel
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.SharedLauncherActivity
import de.mm20.launcher2.ui.launcher.helper.WallpaperBlur
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.launcher.search.filters.KeyboardFilterBar
import de.mm20.launcher2.ui.launcher.searchbar.LauncherSearchBar
import de.mm20.launcher2.ui.theme.transparency.transparency
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.roundToInt

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
    val homeButton: ScaffoldGesture? = null,
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
     * The color that is used as background for secondary pages.
     */
    val backgroundColor: Color,
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
    val darkSearchBar: Boolean = false,
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
                    //homeButton,
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
        Gesture.HomeButton -> homeButton
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
    HomeButton(null),
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
    initialGesture: Gesture? = null,
    initialIsLocked: Boolean = false,
    initialIsSearchBarHidden: Boolean = false,
) {
    var currentOffset by mutableStateOf(
        when {
            initialGesture == null || initialGesture.orientation == null || config[initialGesture]?.animation == ScaffoldAnimation.Rubberband -> Offset.Zero
            initialGesture == Gesture.SwipeRight -> Offset(-size.width, 0f)
            initialGesture == Gesture.SwipeLeft -> Offset(size.width, 0f)
            initialGesture == Gesture.SwipeUp -> Offset(0f, -size.height)
            initialGesture == Gesture.SwipeDown -> Offset(0f, size.height)
            else -> Offset.Zero
        }
    )
        private set
    var currentZOffset by mutableFloatStateOf(
        if (initialGesture != null && initialGesture.orientation == null) 1f else 0f
    )
        private set
    var currentGesture by mutableStateOf<Gesture?>(initialGesture)
        private set

    /**
     * True if the search is animating to open after tapping the search bar or performing a tap gesture.
     */
    private var isOpeningSearch by mutableStateOf(false)
    val currentSearchBarOffset by derivedStateOf {
        val base = when {
            currentComponent?.showSearchBar == false -> homePageSearchBarOffset
            else -> homePageSearchBarOffset * (1 - currentProgress) + secondaryPageSearchBarOffset * currentProgress
        }
        base + if (currentAnimation == ScaffoldAnimation.Rubberband && !isOpeningSearch) currentOffset.y else 0f
    }
    private var homePageSearchBarOffset by mutableFloatStateOf(0f)
    private var secondaryPageSearchBarOffset by mutableFloatStateOf(0f)

    var isSearchBarFocused by mutableStateOf(config.homeComponent is SearchComponent)

    val statusBarScrim by derivedStateOf {
        !isAtTop
    }

    val navBarScrim by derivedStateOf {
        !isAtBottom
    }

    val darkStatusBarIcons by derivedStateOf {
        val isLightBackground = config.backgroundColor.luminance() > 0.5f
        when {
            statusBarScrim -> isLightBackground
            currentProgress < 0.5f && !config.homeComponent.drawBackground -> {
                config.darkStatusBarIcons
            }

            currentProgress >= 0.5f && currentComponent?.drawBackground == false -> {
                config.darkStatusBarIcons
            }

            else -> {
                isLightBackground
            }
        }
    }
    val darkNavBarIcons by derivedStateOf {
        val isLightBackground = config.backgroundColor.luminance() > 0.5f
        when {
            navBarScrim -> isLightBackground
            currentProgress < 0.5f && !config.homeComponent.drawBackground -> {
                config.darkNavBarIcons
            }

            currentProgress >= 0.5f && currentComponent?.drawBackground == false -> {
                config.darkNavBarIcons
            }

            else -> {
                isLightBackground
            }
        }
    }

    val isAtTop by derivedStateOf {
        val component = if (!isSettledOnSecondaryPage) null else currentComponent
        (component?.isAtTop?.value ?: config.homeComponent.isAtTop.value) != false
    }

    val isAtBottom by derivedStateOf {
        val component = if (!isSettledOnSecondaryPage) null else currentComponent
        (component?.isAtBottom?.value ?: config.homeComponent.isAtBottom.value) != false
    }

    val searchBarLevel by derivedStateOf {
        val component = currentComponent

        val homeLevel = if (config.searchBarPosition == SearchBarPosition.Top) {
            when (config.homeComponent.isAtTop.value) {
                true if config.homeComponent.drawBackground -> SearchBarLevel.Active
                true -> SearchBarLevel.Resting
                false -> SearchBarLevel.Raised
                null -> null
            }
        } else {
            when (config.homeComponent.isAtBottom.value) {
                true if config.homeComponent.drawBackground -> SearchBarLevel.Active
                true -> SearchBarLevel.Resting
                false -> SearchBarLevel.Raised
                null -> null
            }
        }

        val secondaryLevel = if (config.searchBarPosition == SearchBarPosition.Top) {
            when (component?.isAtTop?.value) {
                true if (component.drawBackground) -> SearchBarLevel.Active
                true -> SearchBarLevel.Resting
                false -> SearchBarLevel.Raised
                null -> null
            }
        } else {
            when (component?.isAtBottom?.value) {
                true if (component.drawBackground) -> SearchBarLevel.Active
                true -> SearchBarLevel.Resting
                false -> SearchBarLevel.Raised
                null -> null
            }
        }

        when (currentProgress) {
            0f -> homeLevel ?: SearchBarLevel.Active
            1f -> secondaryLevel ?: homeLevel ?: SearchBarLevel.Active
            else if homeLevel != null && secondaryLevel != null -> maxOf(homeLevel, secondaryLevel)
            else if homeLevel != null -> homeLevel
            else -> SearchBarLevel.Active
        }
    }

    /**
     * True if any page is open, false if on home page.
     */
    var isSettledOnSecondaryPage by mutableStateOf(initialGesture != null)
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

    private val offsetAnimatable =
        Animatable(Offset.Zero, Offset.VectorConverter)

    private val zAnimatable =
        Animatable(0f, Float.VectorConverter)

    private val searchBarAnimatable =
        Animatable(0f, Float.VectorConverter)

    var isDragged by mutableStateOf(false)
        private set

    suspend fun onDragStarted() {
        if (isLocked) return
        isDragged = true
        offsetAnimatable.stop()
        zAnimatable.stop()
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

        val delta = when {
            !isAtTop && !isAtBottom -> delta.copy(y = 0f)
            !isAtTop -> delta.copy(y = delta.y.coerceAtMost(-offset.y))
            !isAtBottom -> delta.copy(y = delta.y.coerceAtLeast(-offset.y))
            else -> delta
        }
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
                } else if (offset.x < offset.y && config.swipeDown != null && isAtTop) {
                    Gesture.SwipeDown
                } else {
                    null
                }
            }

            (offset.x < 0 && offset.y < 0) -> {
                return if (offset.x < offset.y && config.swipeLeft != null) {
                    Gesture.SwipeLeft
                } else if (offset.x > offset.y && config.swipeUp != null && isAtBottom) {
                    Gesture.SwipeUp
                } else {
                    null
                }
            }

            (offset.x >= 0 && offset.y < 0) -> {
                return if (offset.x > -offset.y && config.swipeRight != null) {
                    Gesture.SwipeRight
                } else if (offset.x < -offset.y && config.swipeUp != null && isAtBottom) {
                    Gesture.SwipeUp
                } else {
                    null
                }
            }

            (offset.x < 0 && offset.y >= 0) -> {
                return if (offset.x < -offset.y && config.swipeLeft != null) {
                    Gesture.SwipeLeft
                } else if (offset.x > -offset.y && config.swipeDown != null && isAtTop) {
                    Gesture.SwipeDown
                } else {
                    null
                }
            }
        }
        return null
    }

    /**
     * Called when the drag gesture is stopped.
     * This will perform the fling animation and snap to the next page if needed.
     * @param velocity The velocity at the end of the drag gesture.
     * @param disallowPageChange If true, the page will not change even if the velocity is over the threshold.
     */
    suspend fun onDragStopped(velocity: Velocity, disallowPageChange: Boolean = false) {
        isDragged = false
        if (isLocked) return
        val velocity = when {
            !isAtTop && !isAtBottom -> velocity.copy(y = 0f)
            !isAtTop -> velocity.copy(y = velocity.y.coerceAtMost(0f))
            !isAtBottom -> velocity.copy(y = velocity.y.coerceAtLeast(0f))
            else -> velocity
        }
        if (currentGesture == null) {
            currentOffset = Offset.Zero
        }
        val direction = currentGesture ?: return
        val offset = currentOffset
        val wasPageOpen = isSettledOnSecondaryPage

        val gesture = config[direction] ?: return

        if (gesture.animation == ScaffoldAnimation.Rubberband) {
            performRubberbandFling(direction, offset, velocity, disallowPageChange)
        } else if (gesture.animation == ScaffoldAnimation.Push) {
            performPushFling(direction, offset, velocity, disallowPageChange)
        }

        if (isSettledOnSecondaryPage != wasPageOpen) {
            if (wasPageOpen) {
                deactivateSecondaryPage(gesture.component)
            } else {
                activateSecondaryPage(gesture.component)
            }
        }

        if (!isSettledOnSecondaryPage) currentGesture = null
    }

    private suspend fun performRubberbandFling(
        direction: Gesture,
        offset: Offset,
        velocity: Velocity,
        disallowPageChange: Boolean,
    ) {
        val wasSettledOnSecondaryPage = isSettledOnSecondaryPage

        if (!disallowPageChange) {
            if (offset.x <= -rubberbandThreshold || offset.x < 0f && velocity.x < -velocityThreshold) {
                isSettledOnSecondaryPage = !isSettledOnSecondaryPage
            } else if (offset.x >= rubberbandThreshold || offset.x > 0f && velocity.x > velocityThreshold) {
                isSettledOnSecondaryPage = !isSettledOnSecondaryPage
            } else if (offset.y <= -rubberbandThreshold || offset.y < 0f && velocity.y < -velocityThreshold) {
                isSettledOnSecondaryPage = !isSettledOnSecondaryPage
            } else if (offset.y >= rubberbandThreshold || offset.y > 0f && velocity.y > velocityThreshold) {
                isSettledOnSecondaryPage = !isSettledOnSecondaryPage
            }
        }

        if (wasSettledOnSecondaryPage != isSettledOnSecondaryPage) {
            onHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            if (isSettledOnSecondaryPage) {
                prepareSecondaryPage()
            } else {
                prepareHomePage()
            }
        }

        offsetAnimatable.snapTo(currentOffset)
        offsetAnimatable.animateTo(
            Offset.Zero,
            initialVelocity = Offset(velocity.x, velocity.y),
        ) {
            currentOffset = this.value
        }
    }

    private suspend fun performPushFling(
        direction: Gesture,
        offset: Offset,
        velocity: Velocity,
        disallowPageChange: Boolean,
    ) {
        val wasOverThreshold =
            currentOffset.x.absoluteValue > size.width * 0.5f ||
                    currentOffset.y.absoluteValue > size.height * 0.5f

        val wasSettledOnSecondaryPage = isSettledOnSecondaryPage

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
            if (!disallowPageChange) {
                if (offset.y > threshold && velocity.y > -velocityThreshold || velocity.y > velocityThreshold) {
                    Offset(0f, upperPage)
                } else {
                    Offset(0f, lowerPage)
                }
            } else {
                if (offset.y > threshold) Offset(0f, upperPage) else Offset(0f, lowerPage)
            }
        } else {
            if (!disallowPageChange) {
                if (offset.x > threshold && velocity.x > -velocityThreshold || velocity.x > velocityThreshold) {
                    Offset(upperPage, 0f)
                } else {
                    Offset(lowerPage, 0f)
                }
            } else {
                if (offset.x > threshold) Offset(upperPage, 0f) else Offset(lowerPage, 0f)
            }
        }

        isSettledOnSecondaryPage = targetOffset != Offset.Zero

        val isOverThreshold =
            targetOffset.x.absoluteValue > size.width * 0.5f ||
                    targetOffset.y.absoluteValue > size.height * 0.5f

        if (wasOverThreshold != isOverThreshold) {
            onHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }

        if (wasSettledOnSecondaryPage != isSettledOnSecondaryPage) {
            if (isSettledOnSecondaryPage) {
                prepareSecondaryPage()
            } else {
                prepareHomePage()
            }
        }

        offsetAnimatable.snapTo(currentOffset)
        offsetAnimatable.animateTo(
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

    suspend fun onHomeButtonPress() {
        performTapGesture(Gesture.HomeButton)

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
     */
    fun onComponentScroll(delta: Float) {
        if (isSearchBarHidden || config.fixedSearchBar) return
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
        if (!isSettledOnSecondaryPage) return
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
            zAnimatable.snapTo(currentZOffset)
            zAnimatable.animateTo(1f, animationSpec = tween(100)) {
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

            offsetAnimatable.snapTo(currentOffset)
            offsetAnimatable.animateTo(targetOffset) {
                currentOffset = this.value
            }
        }
    }

    /**
     * End the predictive back gesture and return to the home page.
     * @param fast use a faster animation
     */
    suspend fun onPredictiveBackEnd() {
        navigateBack()
    }

    suspend fun navigateBack(fast: Boolean = false) {
        val gesture = currentGesture ?: return
        val component = currentComponent ?: return

        unlock()
        isSettledOnSecondaryPage = false

        prepareHomePage()

        if (gesture.orientation == null) {
            zAnimatable.snapTo(currentZOffset)
            zAnimatable.animateTo(0f, animationSpec = tween(100)) {
                currentZOffset = this.value
            }
        } else {
            offsetAnimatable.snapTo(currentOffset)
            offsetAnimatable.animateTo(
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
        lock()
        currentGesture = gesture

        zAnimatable.snapTo(0f)
        zAnimatable.animateTo(1f, animationSpec = tween(300)) {
            currentZOffset = this.value
        }

        isSettledOnSecondaryPage = true

        prepareSecondaryPage()
        activateSecondaryPage(component)
    }

    private suspend fun prepareSecondaryPage() {
        config.homeComponent.onPreDismiss(this)
        currentComponent?.onPreActivate(this)
    }

    private suspend fun prepareHomePage() {
        currentComponent?.onPreDismiss(this)
        config.homeComponent.onPreActivate(this)
    }

    /**
     * Unmounts the secondary page component and mounts the home page component.
     * Must be called after the animation is completed.
     */
    private suspend fun deactivateSecondaryPage(component: ScaffoldComponent) {
        config.homeComponent.onActivate(this)
        component.onDismiss(this)
        currentGesture = null
        secondaryPageSearchBarOffset = 0f
    }

    /**
     * Mount the secondary page component and dismiss it again if not permanent.
     * Must be called after the animation is completed.
     */
    private suspend fun activateSecondaryPage(component: ScaffoldComponent) {
        component.onActivate(this)

        if (!component.permanent) {
            delay(component.resetDelay)
            isSettledOnSecondaryPage = false
            currentOffset = Offset.Zero
            component.onPreDismiss(this)
            component.onDismiss(this)
            currentGesture = null
            unlock()
        } else {
            config.homeComponent.onDismiss(this)
            homePageSearchBarOffset = 0f
        }
    }

    private suspend fun openSearch() {
        if (currentComponent != null && currentComponent !is SearchComponent) {
            navigateBack(fast = true)
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
        isOpeningSearch = true
        navigateToPage(gesture)
        isOpeningSearch = false
    }

    suspend fun navigateToPage(gesture: Gesture) {
        currentGesture = gesture
        val anim = config[gesture]?.animation ?: return

        prepareSecondaryPage()

        if (anim == ScaffoldAnimation.Rubberband) {
            val targetOffset = when (gesture) {
                Gesture.SwipeLeft -> Offset(-rubberbandThreshold, 0f)
                Gesture.SwipeRight -> Offset(rubberbandThreshold, 0f)
                Gesture.SwipeUp -> Offset(0f, -rubberbandThreshold)
                Gesture.SwipeDown -> Offset(0f, rubberbandThreshold)
                else -> Offset.Zero
            }

            offsetAnimatable.snapTo(currentOffset)
            offsetAnimatable.animateTo(targetOffset) {
                currentOffset = this.value
            }
            isSettledOnSecondaryPage = true
            offsetAnimatable.animateTo(Offset.Zero) {
                currentOffset = this.value
            }
        } else {
            val targetOffset = when (gesture) {
                Gesture.SwipeLeft -> Offset(-size.width, 0f)
                Gesture.SwipeRight -> Offset(size.width, 0f)
                Gesture.SwipeUp -> Offset(0f, -size.height)
                Gesture.SwipeDown -> Offset(0f, size.height)
                else -> Offset.Zero
            }
            offsetAnimatable.snapTo(currentOffset)
            offsetAnimatable.animateTo(targetOffset) {
                currentOffset = this.value
            }
            isSettledOnSecondaryPage = true
        }
        activateSecondaryPage(config[gesture]!!.component)
    }

    suspend fun reset() {
        isSearchBarFocused = false
        currentOffset = Offset.Zero
        unlock()
        isSettledOnSecondaryPage = false

        currentComponent?.let {
            it.onPreDismiss(this)
            config.homeComponent.onPreActivate(this)
            deactivateSecondaryPage(it)
        }
    }

    /**
     * If true, all gestures are ignored.
     */
    var isLocked by mutableStateOf(initialIsLocked)
        private set
    var isSearchBarHidden by mutableStateOf(initialIsSearchBarHidden)
        private set

    /**
     * Lock the scaffold to the current page, disable all gestures and animations.
     * Optionally hide the search bar.
     */
    suspend fun lock(hideSearchBar: Boolean = false) {
        isLocked = true
        if (hideSearchBar) {
            isSearchBarHidden = true
            searchBarAnimatable.snapTo(currentSearchBarOffset)
            searchBarAnimatable.animateTo(
                if (config.searchBarPosition == SearchBarPosition.Bottom) maxSearchBarOffset else -maxSearchBarOffset,
                tween(500)
            ) {
                if (isSettledOnSecondaryPage) {
                    secondaryPageSearchBarOffset = this.value
                } else {
                    homePageSearchBarOffset = this.value
                }
            }
        }
    }

    suspend fun unlock() {
        isLocked = false
        if (isSearchBarHidden) {
            isSearchBarHidden = false
            resetSearchBarOffset()
        }
    }

    suspend fun resetSearchBarOffset() {
        searchBarAnimatable.snapTo(currentSearchBarOffset)
        searchBarAnimatable.animateTo(
            0f,
            tween(500)
        ) {
            if (isSettledOnSecondaryPage) {
                secondaryPageSearchBarOffset = this.value
            } else {
                homePageSearchBarOffset = this.value
            }
        }
    }
}

@Composable
internal fun LauncherScaffold(
    modifier: Modifier,
    config: ScaffoldConfiguration,
) {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalActivity.current as AppCompatActivity
    val view = LocalView.current

    val wallpaperManager = remember(activity) { WallpaperManager.getInstance(activity) }

    val density = LocalDensity.current
    val systemBarInsets = WindowInsets.displayCutout
        .union(WindowInsets.waterfall)
        .let { if (config.showStatusBar) it.union(WindowInsets.statusBars) else it }
        .let { if (config.showNavBar) it.union(WindowInsets.navigationBars) else it }

    val searchVM = viewModel<SearchVM>()
    val searchActions = searchVM.searchActionResults
    val highlightedResult by searchVM.bestMatch
    val filters by searchVM.filters
    val filterBar by searchVM.filterBar.collectAsState(false)
    val filterBarItems by searchVM.filterBarItems.collectAsState(emptyList())
    val launchOnEnter by searchVM.launchOnEnter.collectAsState(false)

    val hazeState = rememberHazeState(blurEnabled = isAtLeastApiLevel(33))

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
            rememberSaveable(
                widthPx, heightPx, touchSlop, rubberbandThreshold, minFlingVelocity, config,
                saver = listSaver(
                    save = {
                        listOf(
                            it.currentGesture,
                            it.isSearchBarHidden,
                            it.isLocked,
                        )
                    },
                    restore = {
                        LauncherScaffoldState(
                            config = config,
                            size = Size(widthPx, heightPx),
                            touchSlop = touchSlop,
                            rubberbandThreshold = rubberbandThreshold,
                            velocityThreshold = minFlingVelocity,
                            maxSearchBarOffset = maxSearchBarOffset,
                            onHapticFeedback = {
                                hapticFeedback.performHapticFeedback(it)
                            },
                            initialGesture = it[0] as Gesture?,
                            initialIsSearchBarHidden = it[1] as Boolean,
                            initialIsLocked = it[2] as Boolean,
                        )
                    }
                )
            ) {
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

        LaunchedEffect(state.isAtTop, state.isAtBottom) {
            if (state.currentProgress > 0f && state.currentProgress < 1f) {
                return@LaunchedEffect
            }
            when (state.currentComponent?.reverseScrolling) {
                true -> if (state.isAtBottom) state.resetSearchBarOffset()
                false -> if (state.isAtTop) state.resetSearchBarOffset()
                else -> {}
            }
        }

        val searchBarHeight by animateDpAsState(
            if (state.isSearchBarHidden) 0.dp
            else if (searchActions.isEmpty()) 56.dp
            else 104.dp
        )

        val isFilterBarVisible =
            (state.currentProgress == 1f && state.currentComponent is SearchComponent ||
                    state.currentProgress == 0f && config.homeComponent is SearchComponent) &&
                    filterBar && WindowInsets.isImeVisible


        val imeCurrent = WindowInsets.ime.getBottom(LocalDensity.current).toFloat()
        val imeSource = WindowInsets.imeAnimationSource.getBottom(LocalDensity.current).toFloat()
        val imeTarget = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current).toFloat()
        val imeProgress = (imeCurrent /
                (imeSource - imeTarget).absoluteValue.coerceAtLeast(imeCurrent))
            .coerceIn(0f, 1f)

        val filterBarHeight by animateDpAsState(if (isFilterBarVisible) imeProgress * 50.dp else 0.dp)

        LaunchedEffect(state) {
            config.homeComponent.onPreActivate(state)
            config.homeComponent.onActivate(state)

            val activity = (activity as? SharedLauncherActivity) ?: return@LaunchedEffect

            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    if (activity.pauseTime > 0L && System.currentTimeMillis() - activity.pauseTime < 50L && activity.isNewIntent) {
                        if (!state.isLocked) {
                            if (state.currentProgress > 0f) {
                                state.onPredictiveBackEnd()
                            } else {
                                state.onHomeButtonPress()
                            }
                        } else {
                            activity.onBackPressedDispatcher.onBackPressed()
                        }
                    } else if (activity.pauseTime > 0L && System.currentTimeMillis() - activity.pauseTime > 5000L) {
                        if (!state.isLocked) {
                            state.reset()
                            searchVM.reset()
                        }
                    }
                    awaitCancellation()
                } finally {
                    activity.pauseTime = System.currentTimeMillis()
                    activity.pauseOnHome = !state.isSettledOnSecondaryPage
                }
            }
        }


        LaunchedEffect(
            config, activity, view,
            state.darkStatusBarIcons,
            state.darkNavBarIcons,
        ) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val insetsController = WindowInsetsControllerCompat(activity.window, view)
                insetsController.isAppearanceLightStatusBars = state.darkStatusBarIcons
                insetsController.isAppearanceLightNavigationBars = state.darkNavBarIcons
                if (config.showStatusBar) {
                    insetsController.show(WindowInsetsCompat.Type.statusBars())
                } else {
                    insetsController.hide(WindowInsetsCompat.Type.statusBars())
                }
                if (config.showNavBar) {
                    insetsController.show(WindowInsetsCompat.Type.navigationBars())
                } else {
                    insetsController.hide(WindowInsetsCompat.Type.navigationBars())
                }
            }
        }

        if (config.wallpaperBlurRadius > 0.dp) {
            val wallpaperBlur by animateIntAsState(
                if (state.currentProgress >= 0.5f && (state.currentComponent?.drawBackground
                        ?: config.homeComponent.drawBackground)
                    || state.currentProgress < 0.5f && config.homeComponent.drawBackground
                ) {
                    8.dp.toPixels().toInt()
                } else {
                    0
                }
            )
            WallpaperBlur { wallpaperBlur }
        }

        if (!config.finishOnBack || state.currentProgress > 0) {
            PredictiveBackHandler {
                try {
                    state.lock()
                    it.collect {
                        state.onPredictiveBack(it.progress)
                    }
                    scope.launch { state.onPredictiveBackEnd() }
                } catch (_: CancellationException) {
                    scope.launch { state.onPredictiveBackCancel() }
                } finally {
                    state.unlock()
                }
            }
        }

        val nestedScrollConnection = remember(state) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero

                    if (state.currentProgress != 0f && state.currentProgress != 1f) {
                        state.onDrag(available)
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
                        state.onDrag(available)
                        return available
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    return super.onPreFling(available)
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity
                ): Velocity {

                    if (state.currentProgress > 0f && !state.isSettledOnSecondaryPage) {
                        state.onDragStopped(available)
                        return available
                    } else if (state.currentProgress < 1f && state.isSettledOnSecondaryPage) {
                        state.onDragStopped(available)
                        return available
                    } else {
                        state.onDragStopped(available, disallowPageChange = true)
                        return available
                    }
                }
            }
        }

        var draggableOrientation by remember {
            mutableStateOf(state.currentGesture?.orientation)
        }

        LaunchedEffect(state.isSettledOnSecondaryPage) {
            if (!state.isSettledOnSecondaryPage) {
                draggableOrientation = null
            } else {
                draggableOrientation = state.currentGesture?.orientation
            }
        }


        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .nestedScroll(nestedScrollConnection)
                .draggable2D(
                    state = rememberDraggable2DState {
                        state.onDrag(it)
                    },
                    enabled = draggableOrientation == null,
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
                .draggable(
                    state = rememberDraggableState {
                        state.onDrag(Offset(0f, it))
                    },
                    orientation = Orientation.Vertical,
                    enabled = draggableOrientation == Orientation.Vertical,
                    onDragStarted = {
                        scope.launch {
                            state.onDragStarted()
                        }
                    },
                    onDragStopped = { velocity ->
                        scope.launch {
                            state.onDragStopped(Velocity(0f, velocity))
                        }
                    },
                )
                .draggable(
                    state = rememberDraggableState {
                        state.onDrag(Offset(it, 0f))
                    },
                    orientation = Orientation.Horizontal,
                    enabled = draggableOrientation == Orientation.Horizontal,
                    onDragStarted = {
                        scope.launch {
                            state.onDragStarted()
                        }
                    },
                    onDragStopped = { velocity ->
                        scope.launch {
                            state.onDragStopped(Velocity(velocity, 0f))
                        }
                    }
                )
        ) {
            val searchBarInsets = WindowInsets(
                top = if (config.searchBarPosition == SearchBarPosition.Top) searchBarHeight + 8.dp else 8.dp,
                bottom = if (config.searchBarPosition == SearchBarPosition.Bottom) searchBarHeight + 8.dp else 8.dp
            )

            val filterBarInsets = WindowInsets(
                bottom = filterBarHeight
            )

            CompositionLocalProvider(
                LocalScaffoldPage provides ScaffoldPage.Home,
            ) {
                config.homeComponent.Component(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(wallpaperManager, config.doubleTap) {
                            detectTapGestures(
                                onDoubleTap = config.doubleTap?.let {
                                    { scope.launch { state.onDoubleTap() } }
                                },
                                onLongPress = config.longPress?.let {
                                    { scope.launch { state.onLongPress() } }
                                },
                                onTap = {
                                    wallpaperManager.sendWallpaperCommand(
                                        view.windowToken,
                                        WallpaperManager.COMMAND_TAP,
                                        it.x.toInt(),
                                        it.y.toInt(),
                                        0,
                                        null
                                    )
                                }
                            )
                        }
                        .homePageAnimation(
                            state,
                            if (config.homeComponent.drawBackground) {
                                config.backgroundColor.copy(alpha = MaterialTheme.transparency.background)
                            } else {
                                Color.Transparent
                            }
                        ),
                    insets = systemBarInsets
                        .let { if (config.homeComponent.hasIme) it.union(WindowInsets.ime) else it }
                        .let {
                            if (config.searchBarStyle == SearchBarStyle.Hidden) it else it.add(
                                searchBarInsets
                            )
                        }
                        .asPaddingValues(),
                    state
                )
            }

            SecondaryPage(
                state = state,
                config = config,
                modifier = Modifier
                    .fillMaxSize(),
                insets = systemBarInsets
                    .let { if (state.currentComponent?.hasIme == true) it.union(WindowInsets.ime) else it }
                    .add(searchBarInsets).add(filterBarInsets)
                    .asPaddingValues(),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .searchBarAnimation(
                        state,
                        config,
                        systemBarInsets
                            .union(WindowInsets.ime)
                            .add(filterBarInsets)
                            .asPaddingValues()
                    )
            ) {
                LauncherSearchBar(
                    modifier = Modifier
                        .widthIn(max = 916.dp)
                        .align(
                            if (config.searchBarPosition == SearchBarPosition.Top) Alignment.TopCenter
                            else Alignment.BottomCenter
                        ),
                    searchBarOffset = { state.currentSearchBarOffset.roundToInt() },
                    style = config.searchBarStyle,
                    focused = state.isSearchBarFocused,
                    actions = searchActions,
                    level = { state.searchBarLevel },
                    bottomSearchBar = config.searchBarPosition == SearchBarPosition.Bottom,
                    onFocusChange = {
                        if (it) {
                            scope.launch { state.onSearchBarTap() }
                        }
                        state.isSearchBarFocused = it
                    },
                    onKeyboardActionGo = if (launchOnEnter) {
                        { searchVM.launchBestMatchOrAction(activity) }
                    } else null,
                    highlightedAction = highlightedResult as? SearchAction,
                    darkColors = config.darkSearchBar,
                    isSearchOpen = state.currentComponent is SearchComponent && state.isSettledOnSecondaryPage ||
                            config.homeComponent is SearchComponent && !state.isSettledOnSecondaryPage,
                )
            }
            if (isFilterBarVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(y = (1f - imeProgress) * 50.dp)
                        .alpha(imeProgress),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    KeyboardFilterBar(
                        filters = filters,
                        onFiltersChange = { searchVM.setFilters(it) },
                        items = filterBarItems
                    )
                }
            }
        }


        AnimatedVisibility(
            state.statusBarScrim,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .hazeEffect(hazeState) {
                        blurRadius = 4.dp
                        backgroundColor = config.backgroundColor
                    }
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MaterialTheme.transparency.background)
                    )
                    .statusBarsPadding()
            )
        }
        AnimatedVisibility(
            state.navBarScrim,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .hazeEffect(hazeState) {
                        blurRadius = 4.dp
                        backgroundColor = config.backgroundColor
                    }
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MaterialTheme.transparency.background)
                    )
                    .navigationBarsPadding()
            )
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
            .secondaryPageAnimation(
                state,
                config.backgroundColor.copy(alpha = MaterialTheme.transparency.background),
            )
        val composable = composables[component]

        CompositionLocalProvider(
            LocalScaffoldPage provides ScaffoldPage.Secondary
        ) {
            composable?.invoke(mod, insets, state)
        }
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
    backgroundColor: Color,
): Modifier {
    val dir = state.currentGesture ?: return this.background(backgroundColor)
    val component = state.currentComponent ?: return this.background(backgroundColor)

    if (state.currentAnimation == ScaffoldAnimation.Rubberband) {
        return this then component.homePageModifier(
            state,
            Modifier
                .background(backgroundColor)
                .graphicsLayer {
                    translationX =
                        if (dir.orientation == Orientation.Horizontal) state.currentOffset.x else 0f
                    translationY =
                        if (dir.orientation == Orientation.Vertical) state.currentOffset.y else 0f
                    if (!state.isSettledOnSecondaryPage) {
                        alpha = (1f - state.currentProgress).coerceAtMost(1f)
                        scaleX = 1f - (state.currentProgress * 0.03f)
                        scaleY = 1f - (state.currentProgress * 0.03f)
                    } else {
                        alpha = ((1f - state.currentProgress) * 2f - 1f).coerceAtLeast(0f)
                    }
                }
        )
    }

    if (state.currentAnimation == ScaffoldAnimation.ZoomIn) {
        return this then Modifier
            .graphicsLayer {
                scaleX = 1f - (state.currentProgress * 0.03f)
                scaleY = 1f - (state.currentProgress * 0.03f)
                alpha = (1f - state.currentProgress).coerceIn(0f, 1f)
            }
            .background(backgroundColor)
    }

    return this then component.homePageModifier(
        state,
        Modifier
            .background(backgroundColor)
            .absoluteOffset {
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
        if (component.drawBackground) backgroundColor.copy(alpha = backgroundColor.alpha * state.currentProgress) else Color.Transparent

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
        .absoluteOffset {
            when (state.currentGesture) {
                Gesture.SwipeUp -> IntOffset(0, state.size.height.toInt())
                Gesture.SwipeDown -> IntOffset(0, -state.size.height.toInt())
                Gesture.SwipeLeft -> IntOffset(state.size.width.toInt(), 0)
                Gesture.SwipeRight -> IntOffset(-state.size.width.toInt(), 0)
                else -> IntOffset.Zero
            }
        }
        .absoluteOffset {
            IntOffset(
                x = if (state.currentGesture?.orientation == Orientation.Horizontal) state.currentOffset.x.toInt() else 0,
                y = if (state.currentGesture?.orientation == Orientation.Vertical) state.currentOffset.y.toInt() else 0
            )
        }
        .composed {
            val shape = MaterialTheme.shapes.extraLarge.let {
                if (state.currentProgress < 0.95f) {
                    it
                } else {
                    val density = LocalDensity.current
                    val p = 1f - ((state.currentProgress - 0.95f) * 20f).coerceIn(0f, 1f)
                    if (it is CutCornerShape) {
                        CutCornerShape(
                            topStart = it.topStart.toPx(state.size, density) * p,
                            topEnd = it.topEnd.toPx(state.size, density) * p,
                            bottomEnd = it.bottomEnd.toPx(state.size, density) * p,
                            bottomStart = it.bottomStart.toPx(state.size, density) * p,
                        )
                    } else {
                        RoundedCornerShape(
                            topStart = it.topStart.toPx(state.size, density) * p,
                            topEnd = it.topEnd.toPx(state.size, density) * p,
                            bottomEnd = it.bottomEnd.toPx(state.size, density) * p,
                            bottomStart = it.bottomStart.toPx(state.size, density) * p,
                        )
                    }
                }
            }

            Modifier
                .background(background, shape)
                .clip(shape)
        }
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

    val modifier =
        if (component?.showSearchBar == false && config.searchBarStyle == SearchBarStyle.Hidden) {
            Modifier.alpha(0f)
        } else {
            Modifier.offset(y = offset)
        }

    return this then (component?.searchBarModifier(state, modifier)
        ?: modifier) then Modifier.padding(insets)
}