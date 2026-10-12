package com.digimon.glyph.owl

/** Versioned Messenger contract shared with Omalauncher; no ROM or save data crosses it. */
object OwlProtocol {
    const val VERSION = 1
    const val ACTION_BIND = "com.digimon.glyph.OWL_BIND"
    const val SUBSCRIBE = 1
    const val UNSUBSCRIBE = 2
    const val BUTTON = 3
    const val ACK = 4
    const val FRAME = 10
    const val ERROR = 11
}
