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

import io.github.kdroidfilter.nucleus.notification.AuthorizationOption
import io.github.kdroidfilter.nucleus.notification.AuthorizationStatus
import io.github.kdroidfilter.nucleus.notification.DeliveredNotification
import io.github.kdroidfilter.nucleus.notification.NotificationCenter
import io.github.kdroidfilter.nucleus.notification.NotificationCenterDelegate
import io.github.kdroidfilter.nucleus.notification.NotificationContent
import io.github.kdroidfilter.nucleus.notification.NotificationRequest
import io.github.kdroidfilter.nucleus.notification.NotificationResponse
import io.github.kdroidfilter.nucleus.notification.NotificationSound
import io.github.kdroidfilter.nucleus.notification.PresentationOption
import io.github.kdroidfilter.nucleus.notification.linux.LinuxNotificationCenter
import io.github.kdroidfilter.nucleus.notification.linux.LinuxNotificationListener
import io.github.kdroidfilter.nucleus.notification.windows.ActivationType
import io.github.kdroidfilter.nucleus.notification.windows.AdaptiveText
import io.github.kdroidfilter.nucleus.notification.windows.DismissalReason
import io.github.kdroidfilter.nucleus.notification.windows.ToastBindingGeneric
import io.github.kdroidfilter.nucleus.notification.windows.ToastContent
import io.github.kdroidfilter.nucleus.notification.windows.ToastNotificationListener
import io.github.kdroidfilter.nucleus.notification.windows.ToastVisual
import io.github.kdroidfilter.nucleus.notification.windows.WindowsNotificationCenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume
import io.github.kdroidfilter.nucleus.notification.linux.Notification as LinuxNotification
import io.github.kdroidfilter.nucleus.notification.linux.NotificationAction as LinuxNotificationAction

/**
 * Native-backed [NotificationDispatcher] using Nucleus (kdroidfilter/Nucleus).
 *
 * - **macOS**: routes through `UNUserNotificationCenter` via a Swift JNI shim
 *   bundled inside `nucleus.notification-macos`. Requires an `.app` bundle at
 *   runtime — see [PermissionState.BundleRequired].
 * - **Windows**: routes through WinRT Toast via `nucleus.notification-windows`.
 *   Requires an AUMID; call [init] before any [send].
 * - **Linux**: routes through freedesktop D-Bus via `nucleus.notification-linux`.
 *   Works with any notification daemon.
 *
 * If the matching native library fails to load or the app is unbundled on
 * macOS, [send] falls back to [AwtTrayNotifier].
 *
 * A click on a posted notification reaches [onClick], on whatever thread the OS
 * reports it, with the [NotificationSpec.target] and [NotificationSpec.account] it
 * was sent with:
 * - **macOS**: the notification-center delegate's response, matched by request
 *   identifier (Nucleus hands back the identifier, not the `userInfo`).
 * - **Windows**: the toast's activation, matched by its `launch` argument.
 * - **Linux**: the server's `ActionInvoked` for the "default" action, matched by
 *   the notification id `Notify` returned.
 * - **AWT tray**: the tray icon's action event, which names no message, so it
 *   opens the last one the tray showed.
 */
class NucleusNotificationDispatcher(
    private val bundleId: String,
    private val appLabel: String,
    private val fallback: AwtTrayNotifier,
    @Suppress("unused") private val scope: CoroutineScope,
    /** The label of the "open" action Linux servers may show; most never draw it, the click is what counts. */
    private val openActionLabel: suspend () -> String = { appLabel },
    private val onClick: (NotificationClick) -> Unit = {},
) : NotificationDispatcher {
    private val host: HostOs = detectHostOs()

    /** What each notification this run posted opens, by the key its platform reports a click with. */
    private val posted = PostedNotifications<String>()

    /** The same for Linux, whose server numbers its notifications. */
    private val postedLinux = PostedNotifications<Int>()

    /**
     * The last message the tray showed, opened once: the tray's listener also fires on a plain
     * click on its icon, which must not replay a message from hours ago.
     */
    private val lastFallback = PostedNotifications<Unit>(capacity = 1)

    private companion object {
        const val PERMISSION_REQUEST_TIMEOUT_MS = 90_000L
        const val SEND_ACK_TIMEOUT_MS = 10_000L

        /** What macOS reports when the user dismisses rather than opens (only for categories that ask for it). */
        const val MAC_DISMISS_ACTION = "com.apple.UNNotificationDismissActionIdentifier"

        /** Marks a Windows toast's launch argument as one of ours. */
        const val WINDOWS_LAUNCH_PREFIX = "amethyst-notification:"

        const val TIMEOUT_ERROR_MESSAGE =
            "macOS never answered the permission request. The permission banner may have " +
                "auto-dismissed — click again and watch the top-right of the screen for the " +
                "Amethyst banner, then click Allow."
    }

    // Nucleus module availability probed once at construction. Native lib load
    // is triggered by the first class access — wrap in Throwable catch so a
    // missing JAR or unsatisfied link doesn't crash the app.
    private val _nativeAvailable = MutableStateFlow(probeNative())
    override val nativeAvailable: StateFlow<Boolean> = _nativeAvailable.asStateFlow()

    private val _permission = MutableStateFlow<PermissionState>(initialPermissionState())
    override val permission: StateFlow<PermissionState> = _permission.asStateFlow()

    /**
     * Human-readable error from the most recent [requestPermission] call, or
     * null if the last request succeeded / none has run yet. macOS can refuse
     * the authorization request outright (e.g. UNErrorDomain
     * "Notifications are not allowed for this application" when the system
     * blocks the app before any prompt), which is NOT the same as the user
     * clicking "Don't Allow" — the recovery for each is different, and the
     * settings UI needs the raw OS message to show actionable guidance.
     * A timeout marker is also written here when the OS never invokes the
     * completion handler (the permission banner can be auto-dismissed on
     * recent macOS versions without firing the callback, which previously
     * left the request coroutine suspended forever).
     */
    private val _lastRequestError = MutableStateFlow<String?>(null)
    override val lastRequestError: StateFlow<String?> = _lastRequestError.asStateFlow()

    @Volatile
    private var winInitialized: Boolean = false

    init {
        // Refresh macOS permission from the OS in case the user already granted
        // in a previous session. On unbundled runs, Nucleus reports isAvailable
        // = false and we won't touch the OS API.
        if (host == HostOs.MAC && _nativeAvailable.value) {
            try {
                refreshMacPermissionState()
            } catch (_: Throwable) {
                // Bundle-invalid or callback plumbing failure. Leave state as
                // BundleRequired / NotRequested. User can still trigger the
                // request from settings.
            }
            try {
                installMacDelegate()
            } catch (_: Throwable) {
                // Without the delegate clicks only bring the app forward (the OS does that).
            }
        }
        // The tray names no message on a click: it opens the last one it showed.
        fallback.onAction = { onClick(NotificationClick.of(lastFallback.take(Unit))) }
    }

    private fun initialPermissionState(): PermissionState =
        when (host) {
            HostOs.MAC -> if (_nativeAvailable.value) PermissionState.NotRequested else PermissionState.BundleRequired
            HostOs.WINDOWS, HostOs.LINUX ->
                if (_nativeAvailable.value) PermissionState.NotApplicable else PermissionState.Denied
            HostOs.UNKNOWN -> PermissionState.NotApplicable
        }

    override suspend fun requestPermission(): PermissionState {
        if (host != HostOs.MAC) {
            _permission.value =
                if (_nativeAvailable.value) PermissionState.NotApplicable else PermissionState.Denied
            return _permission.value
        }
        if (!_nativeAvailable.value) {
            _permission.value = PermissionState.BundleRequired
            return _permission.value
        }
        val outcome =
            withContext(Dispatchers.IO) {
                // Hard timeout: recent macOS versions present the permission
                // request as an auto-dismissing banner; if the user misses it
                // the completion handler may never fire, which would
                // otherwise leave this coroutine (and the settings UI's
                // "Requesting…" spinner) suspended forever.
                withTimeoutOrNull(PERMISSION_REQUEST_TIMEOUT_MS) {
                    suspendCancellableCoroutine<Pair<Boolean, String?>> { cont ->
                        try {
                            NotificationCenter
                                .requestAuthorization(
                                    options =
                                        setOf(
                                            AuthorizationOption.ALERT,
                                            AuthorizationOption.SOUND,
                                            AuthorizationOption.BADGE,
                                        ),
                                    callback = { granted, error -> cont.resume(granted to error) },
                                )
                        } catch (t: Throwable) {
                            cont.resume(false to (t.message ?: t::class.simpleName))
                        }
                    }
                }
            }
        if (outcome == null) {
            _lastRequestError.value = TIMEOUT_ERROR_MESSAGE
            // Leave _permission at its current value: the OS never answered,
            // so we genuinely don't know the state. A follow-up
            // refreshPermission() (window focus / settings re-entry) will
            // reconcile it if the user did grant via the banner.
            return _permission.value
        }
        val (granted, osError) = outcome
        _lastRequestError.value = if (granted) null else osError
        _permission.value =
            if (granted) PermissionState.Granted else PermissionState.Denied
        return _permission.value
    }

    override suspend fun refreshPermission(): PermissionState {
        if (host != HostOs.MAC || !_nativeAvailable.value) {
            return _permission.value
        }
        // getNotificationSettings is callback-based; bridge to suspend + timeout.
        val newState =
            withContext(Dispatchers.IO) {
                try {
                    suspendCancellableCoroutine<PermissionState> { cont ->
                        try {
                            NotificationCenter.getNotificationSettings { settings ->
                                val mapped =
                                    when (settings.authorizationStatus) {
                                        AuthorizationStatus.AUTHORIZED,
                                        AuthorizationStatus.PROVISIONAL,
                                        AuthorizationStatus.EPHEMERAL,
                                        -> PermissionState.Granted
                                        AuthorizationStatus.DENIED -> PermissionState.Denied
                                        AuthorizationStatus.NOT_DETERMINED -> PermissionState.NotRequested
                                    }
                                if (cont.isActive) cont.resume(mapped)
                            }
                        } catch (t: Throwable) {
                            if (cont.isActive) cont.resume(PermissionState.BundleRequired)
                        }
                    }
                } catch (_: Throwable) {
                    currentCoroutineContext().ensureActive()
                    _permission.value
                }
            }
        _permission.value = newState
        return newState
    }

    override suspend fun send(spec: NotificationSpec): SendResult {
        if (!_nativeAvailable.value) {
            return runFallback(spec, reason = "native pipeline unavailable")
        }
        if (host == HostOs.MAC && _permission.value !is PermissionState.Granted) {
            return SendResult.Suppressed("macOS notifications not authorized")
        }
        return try {
            withContext(Dispatchers.IO) {
                when (host) {
                    HostOs.MAC -> sendMac(spec)
                    HostOs.WINDOWS -> sendWindows(spec)
                    HostOs.LINUX -> sendLinux(spec)
                    HostOs.UNKNOWN -> runFallback(spec, reason = "unknown platform")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            SendResult.Failed(e)
        }
    }

    override fun release() {
        try {
            fallback.release()
        } catch (_: Throwable) {
        }
    }

    // ---------- macOS ----------

    private fun refreshMacPermissionState() {
        NotificationCenter.getNotificationSettings { settings ->
            _permission.value =
                when (settings.authorizationStatus) {
                    AuthorizationStatus.AUTHORIZED,
                    AuthorizationStatus.PROVISIONAL,
                    AuthorizationStatus.EPHEMERAL,
                    -> PermissionState.Granted
                    AuthorizationStatus.DENIED -> PermissionState.Denied
                    AuthorizationStatus.NOT_DETERMINED -> PermissionState.NotRequested
                }
        }
    }

    /**
     * Receives the user's response to a notification. Setting a delegate also makes macOS ask
     * [NotificationCenterDelegate.willPresent] before showing one while the app is frontmost
     * (without one it shows none then): every sender here already decided the user should see it
     * (a print that ended under a hidden window, a reply while another app has the focus), so all
     * of them show.
     */
    private fun installMacDelegate() {
        NotificationCenter.setDelegate(
            object : NotificationCenterDelegate {
                override fun willPresent(notification: DeliveredNotification): Set<PresentationOption> = setOf(PresentationOption.BANNER, PresentationOption.LIST, PresentationOption.SOUND)

                override fun didReceive(response: NotificationResponse) {
                    val spec = posted.take(response.notification.identifier)
                    if (response.actionIdentifier == MAC_DISMISS_ACTION) return
                    onClick(NotificationClick.of(spec))
                }
            },
        )
    }

    private suspend fun sendMac(spec: NotificationSpec): SendResult {
        val identifier = UUID.randomUUID().toString()
        val content =
            NotificationContent(
                title = spec.title,
                body = spec.body,
                sound = NotificationSound.Default,
                userInfo =
                    buildMap {
                        spec.deepLinkNoteId?.let { put("noteId", it) }
                        put("kind", spec.kind.name)
                    },
                threadIdentifier = spec.threadId.orEmpty(),
            )
        val request =
            NotificationRequest(
                identifier = identifier,
                content = content,
            )
        posted.put(identifier, spec)
        // Nucleus delivers add() asynchronously; the optional callback carries
        // the OS error string (null on success). Without it, a rejected
        // notification (e.g. unauthorized at send time) is silently swallowed
        // and the caller logs a phantom "Delivered". The Result wrapper
        // disambiguates "callback fired with null error" from "callback never
        // fired" (timeout — treat as delivered, since UNUserNotificationCenter
        // still posts the request in that case on some macOS versions).
        val addOutcome =
            withTimeoutOrNull(SEND_ACK_TIMEOUT_MS) {
                suspendCancellableCoroutine<Result<String?>> { cont ->
                    try {
                        NotificationCenter
                            .add(request) { error -> cont.resume(Result.success(error)) }
                    } catch (t: Throwable) {
                        cont.resume(Result.success(t.message ?: t::class.simpleName))
                    }
                }
            }
        val addError = addOutcome?.getOrNull()
        return when {
            addOutcome == null -> SendResult.Delivered // no ack from OS; request was queued
            addError.isNullOrBlank() -> SendResult.Delivered
            else -> {
                posted.take(identifier)
                SendResult.Failed(IllegalStateException("UNUserNotificationCenter.add failed: $addError"))
            }
        }
    }

    // ---------- Windows ----------

    private fun ensureWindowsInitialized() {
        if (winInitialized) return
        try {
            WindowsNotificationCenter.initialize(aumid = bundleId, appName = appLabel)
            WindowsNotificationCenter.addListener(
                object : ToastNotificationListener {
                    override fun onActivated(
                        tag: String,
                        group: String,
                        arguments: String,
                        userInputs: Map<String, String>,
                    ) {
                        if (!arguments.startsWith(WINDOWS_LAUNCH_PREFIX)) return
                        onClick(NotificationClick.of(posted.take(arguments.removePrefix(WINDOWS_LAUNCH_PREFIX))))
                    }

                    override fun onDismissed(
                        tag: String,
                        group: String,
                        reason: DismissalReason,
                    ) {}

                    override fun onFailed(
                        tag: String,
                        group: String,
                        errorCode: Int,
                    ) {}
                },
            )
            winInitialized = true
        } catch (_: Throwable) {
            // Best-effort; show will surface any hard failure.
        }
    }

    /**
     * The toast `showSimple` builds (title, then the body when there is one), plus a `launch`
     * argument: the toast's activation hands it back, and it says which notification was clicked.
     */
    private fun sendWindows(spec: NotificationSpec): SendResult {
        ensureWindowsInitialized()
        val key = UUID.randomUUID().toString()
        val lines = listOf(spec.title, spec.body).filter { it.isNotEmpty() }
        val content =
            ToastContent(
                visual = ToastVisual(ToastBindingGeneric(children = lines.map { AdaptiveText(text = it) })),
                launch = WINDOWS_LAUNCH_PREFIX + key,
                activationType = ActivationType.FOREGROUND,
            )
        posted.put(key, spec)
        WindowsNotificationCenter.show(content = content, group = spec.threadId.orEmpty())
        return SendResult.Delivered
    }

    // ---------- Linux ----------

    /** Listens for the server's signals once: a click invokes the "default" action every notification carries. */
    private val linuxListener: Unit by lazy {
        LinuxNotificationCenter.addListener(
            object : LinuxNotificationListener {
                override fun onActionInvoked(
                    notificationId: Int,
                    actionKey: String,
                ) {
                    if (actionKey != LinuxNotificationAction.DEFAULT_KEY) return
                    onClick(NotificationClick.of(postedLinux.take(notificationId)))
                }
                // A close is not forgotten here: some servers report one as the banner times out and
                // still offer the notification in their history, where a click must open it.
            },
        )
    }

    private suspend fun sendLinux(spec: NotificationSpec): SendResult {
        linuxListener
        val notification =
            LinuxNotification(
                appName = appLabel,
                summary = spec.title,
                body = spec.body,
                // "default" is the click on the notification itself, not a button.
                actions = listOf(LinuxNotificationAction(LinuxNotificationAction.DEFAULT_KEY, openActionLabel())),
            )
        val id = LinuxNotificationCenter.notify(notification)
        if (id <= 0) return SendResult.Failed(IllegalStateException("libnotify returned id=0"))
        postedLinux.put(id, spec)
        return SendResult.Delivered
    }

    // ---------- Fallback ----------

    private fun runFallback(
        spec: NotificationSpec,
        reason: String,
    ): SendResult {
        val result = fallback.send(spec.title, spec.body)
        if (result.isSuccess) lastFallback.put(Unit, spec)
        return result.fold(
            onSuccess = { SendResult.DeliveredViaFallback },
            onFailure = { SendResult.Failed(IllegalStateException("$reason; AWT fallback also failed: ${it.message}", it)) },
        )
    }

    // ---------- Native probe ----------

    private fun probeNative(): Boolean =
        try {
            when (host) {
                HostOs.MAC ->
                    NotificationCenter.isAvailable
                HostOs.WINDOWS ->
                    WindowsNotificationCenter.isAvailable
                HostOs.LINUX ->
                    LinuxNotificationCenter.isAvailable
                HostOs.UNKNOWN -> false
            }
        } catch (_: Throwable) {
            // ClassNotFoundError, NoClassDefFoundError, UnsatisfiedLinkError, etc.
            false
        }
}
