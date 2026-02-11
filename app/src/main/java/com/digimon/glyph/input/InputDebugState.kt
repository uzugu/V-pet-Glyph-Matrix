package com.digimon.glyph.input

import android.content.Context

/**
 * Shared debug snapshot for live input diagnostics between service and activity.
 */
object InputDebugState {

    private const val PREFS = "input_debug_state"

    data class Snapshot(
        val timestampMs: Long,
        val mode: String,
        val pitchDeg: Float,
        val rollDeg: Float,
        val linearX: Float,
        val linearY: Float,
        val linearZ: Float,
        val filteredX: Float,
        val filteredY: Float,
        val filteredZ: Float,
        val pendingAxis: String,
        val pendingDir: Int,
        val pendingAgeMs: Long,
        val lastTriggerButton: String,
        val lastTriggerAtMs: Long,
        val buttonAActive: Boolean,
        val buttonALatchedByB: Boolean,
        val buttonBActive: Boolean,
        val buttonCActive: Boolean,
        val buttonCLatchedByB: Boolean,
        val glyphPhysicalDown: Boolean
    )

    fun write(context: Context, snapshot: Snapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong("timestampMs", snapshot.timestampMs)
            .putString("mode", snapshot.mode)
            .putFloat("pitchDeg", snapshot.pitchDeg)
            .putFloat("rollDeg", snapshot.rollDeg)
            .putFloat("linearX", snapshot.linearX)
            .putFloat("linearY", snapshot.linearY)
            .putFloat("linearZ", snapshot.linearZ)
            .putFloat("filteredX", snapshot.filteredX)
            .putFloat("filteredY", snapshot.filteredY)
            .putFloat("filteredZ", snapshot.filteredZ)
            .putString("pendingAxis", snapshot.pendingAxis)
            .putInt("pendingDir", snapshot.pendingDir)
            .putLong("pendingAgeMs", snapshot.pendingAgeMs)
            .putString("lastTriggerButton", snapshot.lastTriggerButton)
            .putLong("lastTriggerAtMs", snapshot.lastTriggerAtMs)
            .putBoolean("buttonAActive", snapshot.buttonAActive)
            .putBoolean("buttonALatchedByB", snapshot.buttonALatchedByB)
            .putBoolean("buttonBActive", snapshot.buttonBActive)
            .putBoolean("buttonCActive", snapshot.buttonCActive)
            .putBoolean("buttonCLatchedByB", snapshot.buttonCLatchedByB)
            .putBoolean("glyphPhysicalDown", snapshot.glyphPhysicalDown)
            .apply()
    }

    fun read(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            timestampMs = prefs.getLong("timestampMs", 0L),
            mode = prefs.getString("mode", "flick") ?: "flick",
            pitchDeg = prefs.getFloat("pitchDeg", 0f),
            rollDeg = prefs.getFloat("rollDeg", 0f),
            linearX = prefs.getFloat("linearX", 0f),
            linearY = prefs.getFloat("linearY", 0f),
            linearZ = prefs.getFloat("linearZ", 0f),
            filteredX = prefs.getFloat("filteredX", 0f),
            filteredY = prefs.getFloat("filteredY", 0f),
            filteredZ = prefs.getFloat("filteredZ", 0f),
            pendingAxis = prefs.getString("pendingAxis", "NONE") ?: "NONE",
            pendingDir = prefs.getInt("pendingDir", 0),
            pendingAgeMs = prefs.getLong("pendingAgeMs", 0L),
            lastTriggerButton = prefs.getString("lastTriggerButton", "NONE") ?: "NONE",
            lastTriggerAtMs = prefs.getLong("lastTriggerAtMs", 0L),
            buttonAActive = prefs.getBoolean("buttonAActive", false),
            buttonALatchedByB = prefs.getBoolean("buttonALatchedByB", false),
            buttonBActive = prefs.getBoolean("buttonBActive", false),
            buttonCActive = prefs.getBoolean("buttonCActive", false),
            buttonCLatchedByB = prefs.getBoolean("buttonCLatchedByB", false),
            glyphPhysicalDown = prefs.getBoolean("glyphPhysicalDown", false)
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
