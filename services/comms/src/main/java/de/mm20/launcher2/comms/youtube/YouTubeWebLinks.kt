package de.mm20.launcher2.comms.youtube

import java.net.URI
import java.util.Locale

/** Exact Android handler domains; never register arbitrary subdomains or front ends. */
object YouTubeWebLinks {
    val videoHosts = listOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")
    val musicHosts = listOf("music.youtube.com")
    val allHosts = videoHosts + musicHosts

    /** Null is rejected; true selects Music, false selects video. */
    fun musicTarget(url: String): Boolean? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (
            scheme !in listOf("http", "https") ||
                uri.userInfo != null ||
                uri.port !in listOf(-1, 80, 443)
        )
            return null
        return when (uri.host?.lowercase(Locale.ROOT)) {
            in musicHosts -> true
            in videoHosts -> false
            else -> null
        }
    }

    fun registrationCommands(packageName: String, userId: Int): List<List<String>> {
        require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")))
        require(userId >= 0)
        val prefix = listOf("/system/bin/cmd", "package")
        val target = listOf("--user", userId.toString(), "--package", packageName)
        return listOf(
            prefix + "set-app-links-allowed" + target + "true",
            prefix + "set-app-links-user-selection" + target + "true" + allHosts,
        )
    }

    fun registrationVerified(output: String): Boolean {
        if (!output.lineSequence().any { it.trim() == "Verification link handling allowed: true" })
            return false
        var enabled = false
        val selected = mutableSetOf<String>()
        output.lineSequence().forEach { line ->
            when (line.trim()) {
                "Enabled:" -> enabled = true
                "Disabled:" -> enabled = false
                else -> if (enabled) selected += line.trim()
            }
        }
        return selected.containsAll(allHosts)
    }
}
