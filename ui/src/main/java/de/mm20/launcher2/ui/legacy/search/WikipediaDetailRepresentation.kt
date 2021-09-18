package de.mm20.launcher2.ui.legacy.search

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.transition.Scene
import com.bumptech.glide.Glide
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.search.data.Wikipedia
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.searchable.SearchableView
import de.mm20.launcher2.ui.legacy.view.FavoriteToolbarAction
import de.mm20.launcher2.ui.legacy.view.ToolbarAction
import de.mm20.launcher2.ui.legacy.view.ToolbarView

class WikipediaDetailRepresentation : Representation {
    override fun getScene(rootView: SearchableView, searchable: Searchable, previousRepresentation: Int?): Scene {
        val wikipedia = searchable as Wikipedia
        val scene = Scene.getSceneForLayout(rootView, R.layout.view_wikipedia_detail, rootView.context)
        scene.setEnterAction {
            with(rootView) {
                findViewById<TextView>(R.id.wikipediaTitle).text = wikipedia.label
                findViewById<TextView>(R.id.wikipediaText).text = HtmlCompat.fromHtml(wikipedia.text, HtmlCompat.FROM_HTML_MODE_LEGACY)
                findViewById<ImageView>(R.id.wikipediaImage).also {
                    if (wikipedia.image.isNullOrBlank()) {
                        it.visibility = View.GONE
                        it.setImageDrawable(null)
                    } else {
                        if (wikipedia.image?.endsWith(".png") == true) {
                            it.scaleType = ImageView.ScaleType.CENTER_INSIDE
                        } else {
                            it.scaleType = ImageView.ScaleType.CENTER_CROP
                        }
                        it.visibility = View.VISIBLE
                        Glide.with(context).load(wikipedia.image).into(it)
                    }
                }
                val toolbar = findViewById<ToolbarView>(R.id.wikipediaToolbar)
                setupMenu(rootView, toolbar, wikipedia)
            }
        }
        return scene
    }

    private fun setupMenu(rootView: SearchableView, toolbar: ToolbarView, wikipedia: Wikipedia) {
        val context = rootView.context
        if (rootView.hasBack()) {
            val backAction = ToolbarAction(R.drawable.ic_arrow_back, context.getString(R.string.menu_back))
            backAction.clickAction = {
                rootView.back()
            }
            toolbar.addAction(backAction, ToolbarView.PLACEMENT_START)
        }
        val favAction = FavoriteToolbarAction(context, wikipedia)
        toolbar.addAction(favAction, ToolbarView.PLACEMENT_END)

        val shareAction = ToolbarAction(R.drawable.ic_share, context.getString(R.string.menu_share))
        shareAction.clickAction = {
            share(context, wikipedia)
        }
        toolbar.addAction(shareAction, ToolbarView.PLACEMENT_END)
    }

    private fun share(context: Context, wikipedia: Wikipedia) {
        val text = HtmlCompat.fromHtml(wikipedia.text, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.putExtra(Intent.EXTRA_TEXT, "${wikipedia.label}\n\n" +
                "${text.substring(0, 200)}…\n\n" +
                "${context.getString(R.string.wikipedia_url)}/wiki?curid=${wikipedia.id}")
        shareIntent.type = "text/plain"
        context.startActivity(Intent.createChooser(shareIntent, null))
    }
}