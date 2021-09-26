package de.mm20.launcher2.fragment

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import de.mm20.launcher2.R
import de.mm20.launcher2.licenses.OpenSourceLibrary

class PreferencesLicenseFragment(
    val library: OpenSourceLibrary
) : Fragment() {

    @SuppressLint("ResourceType")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_license, null, false)
        (activity as AppCompatActivity).supportActionBar?.title = library.name
        val url = library.url
        val website = view.findViewById<TextView>(R.id.website)
        website.setOnClickListener {
            val intent = CustomTabsIntent.Builder()
                    .setDefaultColorSchemeParams(CustomTabColorSchemeParams
                            .Builder()
                            .setToolbarColor(-0x9f8275)
                            .build())
                    .setShowTitle(true)
                    .build()
            intent.launchUrl(activity as AppCompatActivity, Uri.parse(url))
        }
        val description = view.findViewById<TextView>(R.id.description)
        description.text = library.description
        val licenseTitle = view.findViewById<TextView>(R.id.licenseTitle)
        val licenseText = view.findViewById<TextView>(R.id.licenseText)
        val licenseCopyright = view.findViewById<TextView>(R.id.licenseCopyright)
        licenseTitle.text = getString(library.licenseName)
        licenseCopyright.text = library.copyrightNote
        licenseText.text = resources.openRawResource(library.licenseText).reader().readText()
        return view
    }
}
