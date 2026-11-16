package de.mm20.launcher2.comms.stremio

import android.content.Context

/** Named local library scopes. Not Stremio family accounts; no passwords. */
class StremioLocalProfiles(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun names(): List<String> = prefs.getString(KEY_NAMES, "")
        .orEmpty().split('\u001f').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun active(): String? = prefs.getString(KEY_ACTIVE, null)?.takeIf { it.isNotBlank() }

    fun add(name: String): String? {
        val clean = sanitize(name) ?: return null
        val next = (names() + clean).distinct()
        prefs.edit().putString(KEY_NAMES, next.joinToString("\u001f")).apply()
        return clean
    }

    fun setActive(name: String?) {
        val clean = name?.let { sanitize(it) }
        val ed = prefs.edit()
        if (clean == null) ed.remove(KEY_ACTIVE) else ed.putString(KEY_ACTIVE, clean)
        ed.apply()
    }

    companion object {
        const val PREFS = "stremio_local_profiles"
        const val KEY_NAMES = "names"
        const val KEY_ACTIVE = "active"
        const val ACCOUNT_PREFIX = "profile:"

        fun sanitize(name: String): String? {
            val t = name.trim().replace('\u001f', ' ').take(40)
            if (t.isEmpty() || t.contains(':') || t.equals("anonymous", true)) return null
            return t
        }

        fun accountKey(name: String?): String? = name?.let { sanitize(it) }?.let { ACCOUNT_PREFIX + it.lowercase() }
    }
}
