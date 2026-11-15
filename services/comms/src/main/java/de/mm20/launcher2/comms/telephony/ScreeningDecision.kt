package de.mm20.launcher2.comms.telephony

import java.util.concurrent.atomic.AtomicBoolean

/** The decision, deadline and service cleanup may race; Telecom receives one response. */
internal class ScreeningDecision(private val respond: (Boolean) -> Unit) {
    private val answered = AtomicBoolean()

    fun complete(block: Boolean) {
        if (answered.compareAndSet(false, true)) respond(block)
    }
}
