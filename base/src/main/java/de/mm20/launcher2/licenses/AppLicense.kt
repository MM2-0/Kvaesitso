package de.mm20.launcher2.licenses

import android.content.Context
import de.mm20.launcher2.base.R

object AppLicense {
    fun get(context: Context): OpenSourceLibrary {
        return OpenSourceLibrary(
            name = context.getString(R.string.app_name),
            description = context.getString(R.string.preference_about_license_summary),
            copyrightNote = "Copyright (C) 2021 MM2-0",
            licenseName = R.string.gpl3_name,
            licenseText = R.raw.license_gpl_3,
            url = "https://github.com/Kvaesitso"
        )
    }
}
