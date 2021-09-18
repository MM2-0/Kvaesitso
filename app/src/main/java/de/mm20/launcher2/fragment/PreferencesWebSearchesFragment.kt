package de.mm20.launcher2.fragment

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.scale
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.color.colorChooser
import com.afollestad.materialdialogs.customview.customView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import de.mm20.launcher2.R
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.search.SearchViewModel
import de.mm20.launcher2.search.WebsearchViewModel
import de.mm20.launcher2.search.data.Websearch
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference

class PreferencesWebSearchesFragment : PreferenceFragmentCompat() {

    private lateinit var rootView: View

    private var sheetIcon: WeakReference<ImageView>? = null

    private val viewModel by lazy {
        ViewModelProvider(context as AppCompatActivity)[WebsearchViewModel::class.java]
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(activity)
        val searches = viewModel.allWebsearches
        searches.observe(context as AppCompatActivity, Observer {
            updatePreferenceScreen(it)
        })
    }

    private fun updatePreferenceScreen(searches: List<Websearch>) {
        preferenceScreen.removeAll()

        for (search in searches) {
            val pref = Preference(context)
            pref.title = search.label
            if (search.icon == null) {
                val drawable = resources.getDrawable(R.drawable.ic_search, requireActivity().theme).mutate()
                drawable.setTintMode(PorterDuff.Mode.SRC_ATOP)
                drawable.setTint(search.color)
                pref.icon = drawable
            } else {
                Glide.with(requireContext())
                        .asDrawable()
                        .load(search.icon)
                        .into(object : SimpleTarget<Drawable>() {
                            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                                pref.icon = resource
                            }
                        })
            }
            pref.setOnPreferenceClickListener {
                editSearch(search)
                true
            }
            preferenceScreen.addPreference(pref)
        }


        val newPref = Preference(activity)
        newPref.setTitle(R.string.preference_websearch_new)
        newPref.setIcon(R.drawable.ic_preference_websearch_new)
        newPref.setOnPreferenceClickListener {
            editSearch(null)
            true
        }
        preferenceScreen.addPreference(newPref)
    }

    private fun editSearch(search: Websearch?) {

        val websearch = search ?: Websearch("", "", 0xFF555555.toInt(), null, null)

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_websearch, null)
        val nameEdit = dialogView.findViewById<EditText>(R.id.websearchName)
        nameEdit.setText(websearch.label)
        val urlEdit = dialogView.findViewById<EditText>(R.id.websearchUrl)
        urlEdit.setText(websearch.urlTemplate)
        val iconView = dialogView.findViewById<ImageView>(R.id.websearchIcon)
        iconView.apply {
            if (websearch.icon == null) {
                setImageResource(R.drawable.ic_search)
                imageTintList = ColorStateList.valueOf(websearch.color)
            } else {
                Glide.with(this)
                        .load(websearch.icon)
                        .into(this)
            }
            sheetIcon = WeakReference(this)
        }

        val sheet = MaterialDialog(requireContext(), BottomSheet())
                .cornerRadius(8f)
                .customView(view = dialogView)

        val radius = 8 * dialogView.dp
        dialogView.background = GradientDrawable().apply {
            cornerRadii = floatArrayOf(
                    radius, radius, // top left
                    radius, radius, // top right
                    0f, 0f, // bottom left
                    0f, 0f // bottom right
            )
        }

        var newColor = websearch.color
        var newIcon: String? = websearch.icon


        sheet.noAutoDismiss()
                .positiveButton(android.R.string.ok) {
            val newUrl = urlEdit.text.toString()
            val newName = nameEdit.text.toString()
            if (!newUrl.contains("\${1}")) {
                urlEdit.error = getString(R.string.websearch_dialog_url_error)
                return@positiveButton
            }
            File(requireContext().cacheDir, "websearch-tmp").takeIf { it.exists() }?.let {
                websearch.icon?.let { File(it).takeIf { it.exists() }?.delete() }
                val newFile = File(requireContext().filesDir, "websearch-${System.currentTimeMillis()}")
                it.copyTo(newFile, true)
                it.delete()
                newIcon = newFile.absolutePath
            }
            if (newIcon == null) {
                websearch.icon?.let { File(it).takeIf { it.exists() }?.delete() }
            }
            websearch.urlTemplate = newUrl
            websearch.label = newName
            websearch.icon = newIcon
            websearch.color = newColor
            viewModel.insertWebsearch(websearch)
            sheet.dismiss()
        }

        sheet.negativeButton(android.R.string.cancel) {
            sheet.cancel()
        }

        @Suppress("DEPRECATION")
        sheet.neutralButton(R.string.menu_delete) {
            sheet.dismiss()
            websearch.icon?.let { File(it).takeIf { it.exists() }?.delete() }
            viewModel.deleteWebsearch(websearch)
        }

        sheet.setOnCancelListener {
            File(requireContext().cacheDir, "websearch-tmp").takeIf { it.exists() }?.delete()
        }

        dialogView.findViewById<View>(R.id.websearchIcon).setOnClickListener {
            MaterialDialog(requireContext()).show {
                @Suppress("DEPRECATION")
                neutralButton(R.string.custom_icon) {
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.type = "image/*"
                    try {
                        startActivityForResult(intent, 24)
                    } catch (e: ActivityNotFoundException) {
                    }
                    dismiss()
                }
                title(R.string.websearch_dialog_choose_icon_color)
                colorChooser(
                        colors = context.resources.getIntArray(R.array.color_chooser_presets),
                        allowCustomArgb = true,
                        showAlphaSelector = false
                ) { _, color ->
                    iconView.setImageResource(R.drawable.ic_search)
                    iconView.imageTintList = ColorStateList.valueOf(color)
                    newColor = color
                    newIcon = null
                    File(requireContext().cacheDir, "websearch-tmp").takeIf { it.exists() }?.delete()
                    dismiss()
                }
            }
        }
        sheet.show()
    }

    override fun onResume() {
        super.onResume()
        (activity as AppCompatActivity).supportActionBar
                ?.setTitle(R.string.preference_search_edit_websearch)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val dataUri = data?.data
        if (requestCode == 24 && resultCode == Activity.RESULT_OK && dataUri != null) {
            val stream = requireActivity().contentResolver.openInputStream(dataUri)
            val icon = BitmapFactory.decodeStream(stream)
            val scaledIcon = icon.scale((32 * requireContext().dp).toInt(), (32 * requireContext().dp).toInt())
            val out = FileOutputStream(File(requireContext().cacheDir, "websearch-tmp"))
            scaledIcon.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.close()
            sheetIcon?.get()?.apply {
                imageTintList = null
                setImageBitmap(scaledIcon)
            }
        }
    }
}