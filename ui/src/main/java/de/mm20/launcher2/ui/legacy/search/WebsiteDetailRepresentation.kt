package de.mm20.launcher2.ui.legacy.search

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.transition.Scene
import com.bumptech.glide.Glide
import de.mm20.launcher2.badges.BadgeProvider
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.ktx.lifecycleScope
import de.mm20.launcher2.legacy.helper.ActivityStarter
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.search.data.Website
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.searchable.SearchableView
import de.mm20.launcher2.ui.legacy.view.FavoriteToolbarAction
import de.mm20.launcher2.ui.legacy.view.LauncherIconView
import de.mm20.launcher2.ui.legacy.view.ToolbarAction
import de.mm20.launcher2.ui.legacy.view.ToolbarView
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class WebsiteDetailRepresentation : Representation {
    override fun getScene(rootView: SearchableView, searchable: Searchable, previousRepresentation: Int?): Scene {
        val website = searchable as Website
        val context = rootView.context as AppCompatActivity
        val scene = Scene.getSceneForLayout(rootView, R.layout.view_website_detail, rootView.context)
        scene.setEnterAction {
            with(rootView) {
                if (!hasBack()) {
                    scene.sceneRoot.elevation = 0f
                    scene.sceneRoot.setBackgroundColor(0)
                    scene.sceneRoot.setOnClickListener {
                        ActivityStarter.start(context, rootView, website)
                    }
                }
                val label = findViewById<TextView>(R.id.websiteTitle)
                label.text = website.label
                findViewById<TextView>(R.id.websiteDescription).text = website.description
                val websiteImage = findViewById<ImageView>(R.id.websiteImage)
                val websiteFavIcon = findViewById<LauncherIconView>(R.id.websiteFavIcon)
                when {
                    website.image.isNotBlank() -> {
                        websiteImage.visibility = View.VISIBLE
                        websiteFavIcon.visibility = FrameLayout.GONE
                        Glide.with(context).load(website.image).into(websiteImage)
                        websiteImage.transitionName = "icon"
                        label.transitionName = "label"
                        websiteFavIcon.transitionName = null
                    }
                    website.favicon.isNotBlank() -> {
                        websiteFavIcon.visibility = View.VISIBLE
                        websiteImage.visibility = FrameLayout.GONE
                        websiteImage.transitionName = null
                        label.transitionName = null
                        websiteFavIcon.transitionName = "icon"
                        websiteFavIcon.apply {
                            badge = BadgeProvider.getInstance(context).getLiveBadge(website.badgeKey)
                            shape = LauncherIconView.getDefaultShape(context)
                            icon = IconRepository.getInstance(context).getIconIfCached(website)
                            lifecycleScope.launch {
                                IconRepository.getInstance(context).getIcon(website, (84 * rootView.dp).toInt()).collect {
                                    icon = it
                                }
                            }
                        }
                    }
                    else -> {
                        websiteFavIcon.visibility = View.GONE
                        websiteImage.visibility = FrameLayout.GONE
                        websiteImage.transitionName = null
                        websiteFavIcon.transitionName = null
                        label.transitionName = null
                    }
                }
                val toolbar = findViewById<ToolbarView>(R.id.websiteToolbar)
                setupMenu(rootView, toolbar, website)
            }
        }
        return scene
    }

    private fun setupMenu(rootView: SearchableView, toolbar: ToolbarView, searchable: Website) {
        val context = rootView.context
        toolbar.clear()

        if (rootView.hasBack()) {
            val backAction = ToolbarAction(R.drawable.ic_arrow_back, context.getString(R.string.menu_back))
            backAction.clickAction = {
                rootView.back()
            }
            toolbar.addAction(backAction, ToolbarView.PLACEMENT_START)
        }
        val favAction = FavoriteToolbarAction(context, searchable)
        toolbar.addAction(favAction, ToolbarView.PLACEMENT_END)

        val shareAction = ToolbarAction(R.drawable.ic_share, context.getString(R.string.menu_share))
        shareAction.clickAction = {
            share(context, searchable)
        }
        toolbar.addAction(shareAction, ToolbarView.PLACEMENT_END)
    }

    private fun share(context: Context, website: Website) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.putExtra(Intent.EXTRA_TEXT, "${website.label}\n\n${website.description}\n\n${website.url}")
        shareIntent.type = "text/plain"
        context.startActivity(Intent.createChooser(shareIntent, null))
    }
}