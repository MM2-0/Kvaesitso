package de.mm20.launcher2.comms.telephony

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import de.mm20.launcher2.comms.repository.ContactDirectoryRepository
import de.mm20.launcher2.comms.repository.SpamRepository
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Shared block decision for CallScreeningService and InCallService. Root Call Blocker is
 * proprietary; this is original Telos logic covering the same user-facing rules (list, prefix,
 * regex, hidden, unknown, international, outgoing) with or without root.
 */
object CallBlockEngine : KoinComponent {
    private val context: Context by inject()
    private val commsSettings: CommsSettings by inject()
    private val spamRepository: SpamRepository by inject()
    private val contacts: ContactDirectoryRepository by inject()

    suspend fun shouldBlock(
        number: String,
        presentation: Int,
        incoming: Boolean,
    ): Boolean {
        if (PhoneNumberUtils.isEmergencyNumber(number)) return false
        val settings = commsSettings.snapshot.first()
        val hidden =
            number.isBlank() ||
                presentation == TelecomManager.PRESENTATION_RESTRICTED ||
                presentation == TelecomManager.PRESENTATION_UNKNOWN
        if (hidden && settings.blockHiddenNumbers) return true
        if (!incoming && !settings.blockOutgoingCalls) return false
        if (
            settings.enableSpamBlocking &&
                number.isNotBlank() &&
                spamRepository.isPersonallyBlocked(number)
        )
            return true
        if (
            settings.systemSpamBlocking &&
                number.isNotBlank() &&
                spamRepository.isSystemBlocked(number)
        )
            return true
        if (
            incoming &&
                settings.blockUnknownNumbers &&
                number.isNotBlank() &&
                !contacts.containsNumber(number)
        )
            return true
        if (settings.blockInternational && isInternational(number, settings.homeCountry))
            return true
        return false
    }

    suspend fun rejectIfNeeded(context: Context, call: Call): Boolean {
        val details = call.details ?: return false
        val incoming =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                details.callDirection != Call.Details.DIRECTION_OUTGOING
            } else true
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val block =
            kotlinx.coroutines.withTimeoutOrNull(3500) {
                shouldBlock(number, details.handlePresentation, incoming)
            } ?: false
        if (!block) return false
        // Target this call only; TelecomManager.endCall() can end a different active call.
        runCatching { call.reject(false, "") }.onFailure { runCatching { call.disconnect() } }
        return true
    }

    private fun isInternational(number: String, homeCountry: String): Boolean {
        if (number.none { it.isDigit() }) return false
        val home = HomeCountry.resolve(context, homeCountry)
        return HomeCountry.isInternational(number, home)
    }
}
