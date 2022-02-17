package de.mm20.launcher2.ui.launcher

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.setPadding
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.transition.OneShotLayoutTransition
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.databinding.ViewLauncherScaffoldBinding
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.launcher.widgets.WidgetsVM

@SuppressLint("ClickableViewAccessibility")
class LauncherScaffoldView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding = ViewLauncherScaffoldBinding.inflate(LayoutInflater.from(context), this)

    private val viewModel: LauncherScaffoldVM by (context as AppCompatActivity).viewModels()
    private val widgetsViewModel: WidgetsVM by (context as AppCompatActivity).viewModels()
    private val searchViewModel: SearchVM by (context as AppCompatActivity).viewModels()

    private val scrollViewOnTouchListener = object : OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> return true
                MotionEvent.ACTION_MOVE -> {
                    when {
                        binding.scrollView.scrollY == 0 -> {
                            if (event.historySize > 0) {
                                val dY = event.y - event.getHistoricalY(0)
                                val newTransY = 0.4f * dY + translationY
                                if (newTransY > 0 && newTransY < 48 * dp) {
                                    translationY = newTransY
                                } else if (newTransY <= 0) {
                                    translationY = 0f
                                } else {
                                    translationY = 48 * dp

                                }

                                if (translationY == 0f) return false
                            }
                        }
                        binding.scrollView.scrollY == binding.scrollContainer.height - binding.scrollView.height && viewModel.isSearchOpen.value == true -> {
                            if (event.historySize > 0) {
                                val dY = event.y - event.getHistoricalY(0)
                                val newTransY = 0.4f * dY + translationY

                                if (newTransY <= 0 && newTransY > -48 * dp) {
                                    translationY = newTransY
                                } else if (newTransY > 0) {
                                    translationY = 0f
                                } else {
                                    translationY = -48 * dp
                                }

                                if (translationY == 0f) return false
                            }
                        }
                        else -> return false
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (translationY >= 48 * dp * 0.6) viewModel.toggleSearch()
                    if (translationY <= -48 * dp) viewModel.closeSearch()
                    animate().translationY(0f).setDuration(200).start()
                    return false
                }
                else -> return false
            }
        }

    }

    init {
        context as AppCompatActivity

        context.onBackPressedDispatcher.addCallback {
            viewModel.closeSearch()
            widgetsViewModel.setEditMode(false)
            ObjectAnimator.ofInt(binding.scrollView, "scrollY", 0).setDuration(200).start()
        }

        binding.scrollView.scrollY = viewModel.scrollY
        binding.scrollView.setOnTouchListener(scrollViewOnTouchListener)

        binding.scrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY: Int ->
            viewModel.scrollY = scrollY
            when {
                /* Hide searchbar*/
                scrollY > oldScrollY && ((scrollY > 48 * dp)) -> {
                    var newTransY = binding.searchBar.translationY - scrollY + oldScrollY
                    if (newTransY < -112 * dp) {
                        newTransY = -112 * dp
                    }
                    binding.searchBar.translationY = newTransY
                }
                /* Show searchbar*/
                scrollY < oldScrollY -> {
                    var newTransY = binding.searchBar.translationY - scrollY + oldScrollY
                    if (newTransY > 0f) {
                        newTransY = 0f
                    }
                    binding.searchBar.translationY = newTransY
                }
            }
        }

        viewModel.isSearchOpen.observe(context) {
            if (it) showSearch()
            else hideSearch()
        }

        viewModel.searchBarLevel.observe(context) {
            binding.searchBar.level = it
        }

        searchViewModel.websearchResults.observe(context) {
            binding.searchContainer.setPadding(
                0,
                (if (it.isEmpty()) 48 * dp else 96 * dp).toInt(),
                0,
                0
            )
        }

        widgetsViewModel.isEditMode.observe(context) {
            OneShotLayoutTransition.run(binding.scrollContainer)
            if (it) {
                binding.scrollView.setOnTouchListener(null)
                binding.searchBar.visibility = View.INVISIBLE
                binding.editWidgetToolbar
                    .animate()
                    .translationY(0f)
                    .alpha(1f)
                    .withStartAction {
                        binding.editWidgetToolbar.visibility = View.VISIBLE
                    }
                    .start()
                binding.widgetContainer.setPadding(0, (56 * dp).toInt(), 0, 0)
                val colorSurface = TypedValue()
                context.theme.resolveAttribute(R.attr.colorSurface, colorSurface, true)
                context.window.statusBarColor = colorSurface.data
                viewModel.setStatusBarColor(colorSurface.data)
            } else {
                binding.scrollView.setOnTouchListener(scrollViewOnTouchListener)

                binding.searchBar.visibility = View.VISIBLE
                binding.editWidgetToolbar
                    .animate()
                    .translationY(-binding.editWidgetToolbar.height.toFloat())
                    .alpha(0f)
                    .withEndAction {
                        binding.editWidgetToolbar.visibility = View.GONE
                    }
                    .start()
                binding.widgetContainer.setPadding(0)
                viewModel.setStatusBarColor(0)
            }
        }
        binding.editWidgetToolbar.apply {
            navigationIcon =
                ContextCompat.getDrawable(context, R.drawable.ic_done)?.apply {
                    setTint(ContextCompat.getColor(context, R.color.icon_color))
                }
            setNavigationOnClickListener {
                widgetsViewModel.setEditMode(false)
            }
        }

        binding.searchBar.onFocus = {
            viewModel.openSearch()
        }

        viewModel.blurBackground.observe(context) { blur ->
            if (!isAtLeastApiLevel(31)) return@observe
            context.window.attributes = context.window.attributes.also {
                if (blur) {
                    it.blurBehindRadius = (32 * dp).toInt()
                    it.flags = it.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                } else {
                    it.blurBehindRadius = 0
                    it.flags = it.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                }
            }
        }

        viewModel.statusBarColor.observe(context) {
            context.window.statusBarColor = it
        }
        viewModel.darkStatusBarIcons.observe(context) {
            WindowInsetsControllerCompat(context.window, this).isAppearanceLightStatusBars = it
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (changed) {
            binding.widgetContainer.setClockWidgetHeight(bottom - top - paddingTop - paddingBottom)
        }
        super.onLayout(changed, left, top, right, bottom)
    }

    private fun hideSearch() {
        val set = AnimatorSet()
        set.duration = 300
        set.doOnEnd {
            binding.searchContainer.visibility = View.GONE
            binding.widgetContainer.animate().alpha(1f).setDuration(500).start()
            binding.widgetContainer.visibility = View.VISIBLE
        }
        set.playTogether(
            ObjectAnimator.ofFloat(binding.widgetContainer, "translationY", 0f),
            ObjectAnimator.ofInt(binding.scrollView, "scrollY", 0),
            ObjectAnimator.ofFloat(
                binding.searchContainer, "translationY", 0f,
                if (binding.scrollView.scrollY > binding.searchContainer.height / 2f) -binding.searchContainer.height.toFloat() else binding.scrollView.height.toFloat()
            )
        )
        set.doOnEnd {
            searchViewModel.search("")
        }
        set.start()
        binding.scrollView.scrollTo(0, 0)
        context.getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(
            binding.searchBar.windowToken,
            0
        )
    }

    private fun showSearch() {
        OneShotLayoutTransition.run(binding.widgetContainer)
        binding.searchContainer.visibility = View.VISIBLE
        binding.widgetContainer.animate().alpha(0f).setDuration(500).start()
        binding.widgetContainer.visibility = View.GONE
        val set = AnimatorSet()
        set.duration = 300
        set.playTogether(
            ObjectAnimator.ofFloat(
                binding.widgetContainer,
                "translationY",
                binding.scrollView.height.toFloat()
            ),
            ObjectAnimator.ofInt(binding.scrollView, "scrollY", 0),
            ObjectAnimator.ofFloat(
                binding.searchContainer,
                "translationY",
                binding.scrollView.height.toFloat(),
                0f
            )
        )
        set.start()
    }
}