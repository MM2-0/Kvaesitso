/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.moderation.notifications

import java.awt.AWTException
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon

/**
 * Cross-platform AWT-based OS notification bridge. Uses the platform system
 * tray to display transient balloon/notification messages.
 *
 * Platform behavior:
 * - **Windows**: renders as a toast in the Notification Center
 * - **Linux (GNOME/KDE)**: routes through the desktop notification daemon
 *   (libnotify) if a system tray is available; otherwise silently drops
 * - **macOS**: displays as a top-right corner notification when a proper
 *   application bundle is used; unsigned `java -jar` builds may silently
 *   drop toasts. Requires `jpackage` output or `runDistributable` for
 *   reliable delivery.
 *
 * This class installs a single hidden tray icon at first use and reuses it
 * for every subsequent notification. Callers should use one shared instance
 * per app.
 */
class AwtTrayNotifier(
    private val appLabel: String = "Amethyst",
    private val iconBytes: () -> ByteArray? = { null },
) {
    private val supported: Boolean = SystemTray.isSupported()

    @Volatile
    private var trayIcon: TrayIcon? = null

    val isSupported: Boolean get() = supported

    /**
     * Runs when the user clicks a message the tray showed (Windows, some Linux trays) or the
     * tray icon itself. AWT does not say which message, so the caller remembers the last one.
     */
    @Volatile
    var onAction: (() -> Unit)? = null

    fun send(
        title: String,
        message: String,
        type: MessageType = MessageType.INFO,
    ): Result<Unit> {
        if (!supported) return Result.failure(IllegalStateException("System tray not supported"))
        return try {
            val icon = ensureTrayIcon() ?: return Result.failure(IllegalStateException("Failed to install tray icon"))
            icon.displayMessage(title, message, type.toAwt())
            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: AWTException) {
            Result.failure(e)
        }
    }

    fun release() {
        val icon = trayIcon ?: return
        try {
            SystemTray.getSystemTray().remove(icon)
        } catch (_: Exception) {
            // Best-effort cleanup.
        }
        trayIcon = null
    }

    private fun ensureTrayIcon(): TrayIcon? {
        trayIcon?.let { return it }
        return try {
            val tray = SystemTray.getSystemTray()
            val bytes = iconBytes()
            val image =
                if (bytes != null) {
                    Toolkit.getDefaultToolkit().createImage(bytes)
                } else {
                    // 1x1 transparent PNG — enough to satisfy AWT's non-null
                    // requirement without adding a visible icon in trays that
                    // render everything (Windows). macOS + most Linux daemons
                    // ignore the icon body when displayMessage is called.
                    Toolkit.getDefaultToolkit().createImage(EMPTY_ICON_PNG)
                }
            val icon =
                TrayIcon(image, appLabel).apply {
                    isImageAutoSize = true
                    addActionListener { onAction?.invoke() }
                }
            tray.add(icon)
            trayIcon = icon
            icon
        } catch (_: UnsupportedOperationException) {
            null
        } catch (_: AWTException) {
            null
        }
    }

    enum class MessageType {
        INFO,
        WARNING,
        ERROR,
        NONE,
        ;

        fun toAwt(): TrayIcon.MessageType =
            when (this) {
                INFO -> TrayIcon.MessageType.INFO
                WARNING -> TrayIcon.MessageType.WARNING
                ERROR -> TrayIcon.MessageType.ERROR
                NONE -> TrayIcon.MessageType.NONE
            }
    }

    private companion object {
        // 1x1 transparent PNG (67 bytes).
        @JvmStatic
        val EMPTY_ICON_PNG: ByteArray =
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
                0x00,
                0x00,
                0x00,
                0x0D,
                0x49,
                0x48,
                0x44,
                0x52,
                0x00,
                0x00,
                0x00,
                0x01,
                0x00,
                0x00,
                0x00,
                0x01,
                0x08,
                0x06,
                0x00,
                0x00,
                0x00,
                0x1F,
                0x15,
                0xC4.toByte(),
                0x89.toByte(),
                0x00,
                0x00,
                0x00,
                0x0D,
                0x49,
                0x44,
                0x41,
                0x54,
                0x78,
                0x9C.toByte(),
                0x62,
                0x00,
                0x01,
                0x00,
                0x00,
                0x05,
                0x00,
                0x01,
                0x0D,
                0x0A,
                0x2D,
                0xB4.toByte(),
                0x00,
                0x00,
                0x00,
                0x00,
                0x49,
                0x45,
                0x4E,
                0x44,
                0xAE.toByte(),
                0x42,
                0x60,
                0x82.toByte(),
            )
    }
}

/** OS the JVM is running on. Used by the UI to surface platform-specific caveats. */
enum class HostOs { MAC, WINDOWS, LINUX, UNKNOWN }

fun detectHostOs(): HostOs {
    val name = System.getProperty("os.name")?.lowercase() ?: return HostOs.UNKNOWN
    return when {
        name.contains("mac") || name.contains("darwin") -> HostOs.MAC
        name.contains("win") -> HostOs.WINDOWS
        name.contains("nux") || name.contains("nix") -> HostOs.LINUX
        else -> HostOs.UNKNOWN
    }
}
