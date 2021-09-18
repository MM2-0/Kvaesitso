package de.mm20.launcher2.ui.legacy.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.setPadding
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.favorites.FavoritesViewModel
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.search.data.Searchable

class ToolbarView : LinearLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    private val slots = context.resources.getInteger(R.integer.config_toolbarSlots)

    private var leftOverflowIcon: ImageView? = null
    private var rightOverflowIcon: ImageView? = null

    private val leftActions = mutableListOf<ToolbarAction>()
    private val rightActions = mutableListOf<ToolbarAction>()

    var iconStyle = R.style.LauncherTheme_IconStyle

    init {
        orientation = HORIZONTAL
        clipChildren = false
        clipToPadding = false

        val spacer = View(context)
        spacer.layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        addView(spacer)
    }

    fun addAction(action: ToolbarAction, placement: Int) {
        val useOverflowMenu = (leftActions.size >= slots && placement == PLACEMENT_START) ||
                (rightActions.size >= slots && placement == PLACEMENT_END)

        if (useOverflowMenu) {
            if (placement == PLACEMENT_START) {
                if (leftOverflowIcon == null) {
                    val overflowMenuIcon = ImageView(context, null, R.attr.iconStyle)
                    overflowMenuIcon.isClickable = true
                    overflowMenuIcon.isFocusable = true
                    overflowMenuIcon.setPadding((12 * dp).toInt())
                    overflowMenuIcon.layoutParams = LayoutParams((48 * dp).toInt(), (48 * dp).toInt())
                    overflowMenuIcon.setImageResource(R.drawable.ic_more_vert)
                    removeViewAt(leftActions.size - 1)
                    addView(overflowMenuIcon, leftActions.size - 1)
                    leftOverflowIcon = overflowMenuIcon
                }
                leftActions.add(action)
                val popup = PopupMenu(context, leftOverflowIcon!!)
                for (i in slots - 1 until leftActions.size) {
                    if (leftActions[i].subActions.isNotEmpty()) {
                        val submenu = popup.menu.addSubMenu(leftActions[i].title)
                        for ((j, sa) in leftActions[i].subActions.withIndex()) {
                            submenu.add(i, j, 0, sa.title)
                        }
                    } else {
                        popup.menu.add(i, 0, 0, leftActions[i].title)
                    }
                }
                popup.setOnMenuItemClickListener {
                    if (leftActions[it.groupId].subActions.isEmpty()) {
                        leftActions[it.groupId].clickAction?.invoke()
                    } else {
                        leftActions[it.groupId].subActions[it.itemId].clickAction.invoke()
                    }
                    true
                }
                leftOverflowIcon?.setOnClickListener {
                    popup.show()
                }
            } else {
                if (rightOverflowIcon == null) {
                    val overflowMenuIcon = ImageView(context, null, R.attr.iconStyle)
                    overflowMenuIcon.isClickable = true
                    overflowMenuIcon.isFocusable = true
                    overflowMenuIcon.setPadding((12 * dp).toInt())
                    overflowMenuIcon.layoutParams = LayoutParams((48 * dp).toInt(), (48 * dp).toInt())
                    overflowMenuIcon.setImageResource(R.drawable.ic_more_vert)
                    removeViewAt(childCount - 1)
                    addView(overflowMenuIcon)
                    rightOverflowIcon = overflowMenuIcon
                }
                rightActions.add(action)
                val popup = PopupMenu(context, rightOverflowIcon!!)
                for (i in slots - 1 until rightActions.size) {
                    if (rightActions[i].subActions.isNotEmpty()) {
                        val submenu = popup.menu.addSubMenu(i, -1, 0, rightActions[i].title)
                        for ((j, sa) in rightActions[i].subActions.withIndex()) {
                            submenu.add(i, j, 0, sa.title)
                        }
                    } else {
                        val item = popup.menu.add(i, -1, 0, rightActions[i].title)
                        rightActions[i].titleChanged = {
                            item.title = rightActions[i].title
                        }
                    }
                }
                popup.setOnMenuItemClickListener {
                    if (rightActions[it.groupId].subActions.isEmpty()) {
                        rightActions[it.groupId].clickAction?.invoke()
                    } else if (it.itemId != -1) {
                        rightActions[it.groupId].subActions[it.itemId].clickAction.invoke()
                    }
                    true
                }
                rightOverflowIcon?.setOnClickListener {
                    popup.show()
                }
            }
        } else {
            val imageView = getIconView(action)
            if (placement == PLACEMENT_START) {
                addView(imageView, leftActions.size)
                leftActions.add(action)
            } else {
                addView(imageView)
                rightActions.add(action)
            }
        }

    }

    private fun getIconView(action: ToolbarAction): ImageView {
        val imageView = ImageView(context, null, 0, iconStyle)
        imageView.setImageResource(action.icon)
        imageView.isClickable = true
        imageView.isFocusable = true
        action.iconChanged = {
            imageView.setImageResource(action.icon)
        }
        action.titleChanged = {
            TooltipCompat.setTooltipText(imageView, action.title)
        }
        TooltipCompat.setTooltipText(imageView, action.title)

        val submenu = if (action.subActions.isEmpty()) null else PopupMenu(context, imageView).apply {
            for ((i, subAction) in action.subActions.withIndex()) {
                menu.add(0, i, 0, subAction.title)
            }
            setOnMenuItemClickListener {
                action.subActions[it.itemId].clickAction.invoke()
                true
            }
        }

        imageView.setOnClickListener { _ ->
            if (submenu != null) {
                submenu.show()
            } else {
                action.clickAction?.invoke()
            }
        }
        imageView.setPadding((12 * dp).toInt())
        imageView.layoutParams = LayoutParams((48 * dp).toInt(), (48 * dp).toInt())

        return imageView
    }

    fun clear() {
        removeAllViews()
        leftActions.clear()
        rightActions.clear()
        leftOverflowIcon = null
        rightOverflowIcon = null
        val spacer = View(context)
        spacer.layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        addView(spacer)
    }

    companion object {
        val PLACEMENT_START = 0
        val PLACEMENT_END = 1
    }
}

open class ToolbarAction(icon: Int, title: String) {

    @DrawableRes
    var icon: Int = icon
        set(@DrawableRes value) {
            field = value
            iconChanged?.invoke()
        }
    var title = title
        set(value) {
            field = value
            titleChanged?.invoke()
        }

    var subActions: MutableList<ToolbarSubaction> = mutableListOf()

    var clickAction: (() -> Unit)? = null

    internal var iconChanged: (() -> Unit)? = null
    internal var titleChanged: (() -> Unit)? = null
}

open class ToolbarSubaction(val title: String, var clickAction: (() -> Unit)) {

}

class FavoriteToolbarAction(val context: Context, val item: Searchable)
    : ToolbarAction(
        R.drawable.ic_star_outline,
        context.getString(R.string.favorites_menu_pin)
) {

    private val viewModel = ViewModelProvider(context as AppCompatActivity)[FavoritesViewModel::class.java]
    private val isPinned = viewModel.isPinned(item)

    init {
        isPinned.observe(context as AppCompatActivity, Observer {
            it ?: return@Observer
            if (it) {
                title = context.getString(R.string.favorites_menu_unpin)
                icon = R.drawable.ic_star_solid
            } else {
                title = context.getString(R.string.favorites_menu_pin)
                icon = R.drawable.ic_star_outline
            }
        })
        clickAction = {
            if (isPinned.value == true) {
                viewModel.unpinItem(item)
            } else {
                viewModel.pinItem(item)
            }
        }
    }
}

class VisibilityToolbarAction(val context: Context, val item: Searchable)
    : ToolbarAction(
        R.drawable.ic_visibility,
        context.getString(R.string.menu_hide)
) {

    private val viewModel = ViewModelProvider(context as AppCompatActivity)[FavoritesViewModel::class.java]
    private val isHidden = viewModel.isHidden(item)

    init {
        isHidden.observe(context as AppCompatActivity, Observer {
            if (it) {
                title = context.getString(R.string.menu_unhide)
                icon = R.drawable.ic_visibility
            } else {
                title = context.getString(R.string.menu_hide)
                icon = R.drawable.ic_visibility_off
            }
        })
        clickAction = {
            if (isHidden.value == true) {
                viewModel.unhideItem(item)
            } else {
                viewModel.hideItem(item)
            }
        }
    }
}