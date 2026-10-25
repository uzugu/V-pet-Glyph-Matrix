package com.digimon.glyph.input

import com.digimon.glyph.emulator.EmulatorCore

/** Invalidates input across state replacement, including work waiting in another queue. */
class InputSession {
    private val instance = java.util.UUID.randomUUID().toString()
    private var generation = 0L
    private var blocked = false

    @Synchronized fun capture(): Long? = if (blocked) null else generation
    @Synchronized fun accepts(ticket: Long?): Boolean = !blocked && ticket != null && ticket == generation
    @Synchronized fun isBlocked(): Boolean = blocked
    @Synchronized fun wireEpoch(): String = "$instance:$generation"
    @Synchronized fun acceptsWire(epoch: String): Boolean = !blocked && epoch == wireEpoch()
    @Synchronized fun beginReplacement(): Long? {
        if (blocked) return null
        blocked = true
        return ++generation
    }
    @Synchronized fun finishReplacement(token: Long, beforeResume: () -> Unit = {}): Boolean {
        if (!blocked || token != generation) return false
        beforeResume()
        ++generation
        blocked = false
        return true
    }
    @Synchronized fun reset() { ++generation; blocked = false }

    fun enqueue(submit: ((EmulatorCore) -> Unit) -> Boolean, ticket: Long? = capture(),
                action: (EmulatorCore) -> Unit): Boolean {
        if (!accepts(ticket)) return false
        return submit { core -> if (accepts(ticket)) action(core) }
    }
}
