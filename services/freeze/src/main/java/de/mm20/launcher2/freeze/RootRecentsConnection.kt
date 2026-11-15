package de.mm20.launcher2.freeze

import android.content.Context
import org.json.JSONArray
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Owns one privileged helper for Shizuku or root; EOF closes its listener. */
internal class RootRecentsConnection(context: Context, userId: Int, useShizuku: Boolean = false, onEvent: (Int, RecentsTask?) -> Unit) {
    private val process = if (useShizuku) de.mm20.launcher2.ktx.ShizukuProcesses.start(
        arrayOf("/system/bin/env", "CLASSPATH=${context.applicationInfo.sourceDir}",
            "/system/bin/app_process", "/system/bin", RootRecentsBridge::class.java.name, userId.toString()),
        retryOnSpawnFailure = false,
    ) else Runtime.getRuntime().exec(arrayOf("su", "-c",
        "CLASSPATH='${context.applicationInfo.sourceDir.replace("'", "'\\''")}' /system/bin/app_process /system/bin de.mm20.launcher2.freeze.RootRecentsBridge $userId"))
    private val replies = LinkedBlockingQueue<String>(4)
    private val ready = java.util.concurrent.CountDownLatch(1)
    private val writer = process.outputStream.bufferedWriter()
    @Volatile var alive = true
        private set
    private val reader = Thread({
        try {
            process.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                when {
                    line == "READY" -> ready.countDown()
                    line == "C" -> onEvent(-1, null)
                    line.startsWith("R ") -> {
                        val parts = line.split(' ')
                        val id = parts.getOrNull(1)?.toIntOrNull()
                        val task = if (parts.size == 4 && id != null) parts[3].toIntOrNull()?.let { RecentsTask(id, parts[2], it) } else null
                        if (id != null) onEvent(id, task)
                    }
                    line.startsWith("S ") -> replies.offer(line.substring(2))
                }
            } }
        } catch (_: java.io.IOException) {
            // Permission loss/process shutdown closes the pipe; never crash the launcher reader thread.
        } catch (_: RuntimeException) {
            // Malformed or OEM-specific callback data invalidates this observation session.
        } finally { alive = false; ready.countDown(); replies.offer("null") }
    }, "TelosRootRecents").apply { isDaemon = true; start() }
    init {
        // Drain diagnostics without logging contact/task data or allowing the pipe to block.
        Thread({ runCatching { process.errorStream.use { stream -> val buffer = ByteArray(1024); while (stream.read(buffer) >= 0) Unit } } },
            "TelosRootRecentsErrors").apply { isDaemon = true; start() }
    }
    fun awaitReady(): Boolean = ready.await(8, TimeUnit.SECONDS) && alive
    @Synchronized fun snapshot(): List<RecentsTask>? = runCatching {
        if (!alive) return null
        replies.clear()
        writer.write("snapshot\n"); writer.flush()
        val text = replies.poll(5, TimeUnit.SECONDS) ?: return null
        if (text == "null") return null
        val array = JSONArray(text)
        (0 until array.length()).map { i -> array.getJSONObject(i).let { RecentsTask(it.getInt("id"), it.getString("package"), it.getInt("user")) } }
    }.getOrNull()
    fun close() {
        alive = false
        runCatching { writer.close() }
        process.destroy()
        reader.interrupt()
    }
}
