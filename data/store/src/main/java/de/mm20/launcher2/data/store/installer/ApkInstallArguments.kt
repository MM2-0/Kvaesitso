package de.mm20.launcher2.data.store.installer

/** Only fixed flags and validated numbers are interpolated into the root command. */
internal fun apkInstallArguments(
    bytes: Long,
    userId: Int,
    preserveEnabled: Boolean,
    allowDowngrade: Boolean = false,
): List<String> {
    require(bytes > 0 && userId >= 0)
    return buildList {
        addAll(listOf("pm", "install", "-r", "--user", userId.toString()))
        if (allowDowngrade) add("-d")
        if (preserveEnabled) add("--skip-enable")
        addAll(listOf("-S", bytes.toString(), "-"))
    }
}

internal fun unsupportedPreserveFlag(output: String) =
    output.contains("Unknown option", ignoreCase = true) && output.contains("--skip-enable")
