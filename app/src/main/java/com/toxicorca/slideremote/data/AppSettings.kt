package com.toxicorca.slideremote.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the Next / Previous buttons move. */
enum class StepMode {
    /** Jump to current ± 1 by number. Skips animations, but the slide count never drifts. */
    JUMP,

    /** Send the arrow keys. Steps through animations, so tracking can drift on animated slides. */
    ARROW
}

data class Settings(
    val keyDelayMs: Int = 18,
    val numpadDigits: Boolean = false,
    val stepMode: StepMode = StepMode.JUMP,
    val volumeKeys: Boolean = true,
    val haptics: Boolean = true,
    val autoReconnect: Boolean = true,
    val showOpenDelayMs: Int = 900,
    /** Ask before sending slide keys while the app thinks the slideshow isn't running. */
    val confirmBeforeShow: Boolean = true,
    val lastDeviceAddress: String? = null,
)

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()

    val value: Settings get() = _state.value

    @Synchronized
    fun update(transform: (Settings) -> Settings) {
        val next = transform(_state.value)
        write(next)
        _state.value = next
    }

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            keyDelayMs = prefs.getInt("keyDelayMs", d.keyDelayMs),
            numpadDigits = prefs.getBoolean("numpadDigits", d.numpadDigits),
            stepMode = runCatching {
                StepMode.valueOf(prefs.getString("stepMode", d.stepMode.name) ?: d.stepMode.name)
            }.getOrDefault(d.stepMode),
            volumeKeys = prefs.getBoolean("volumeKeys", d.volumeKeys),
            haptics = prefs.getBoolean("haptics", d.haptics),
            autoReconnect = prefs.getBoolean("autoReconnect", d.autoReconnect),
            showOpenDelayMs = prefs.getInt("showOpenDelayMs", d.showOpenDelayMs),
            confirmBeforeShow = prefs.getBoolean("confirmBeforeShow", d.confirmBeforeShow),
            lastDeviceAddress = prefs.getString("lastDeviceAddress", null),
        )
    }

    private fun write(s: Settings) {
        prefs.edit()
            .putInt("keyDelayMs", s.keyDelayMs)
            .putBoolean("numpadDigits", s.numpadDigits)
            .putString("stepMode", s.stepMode.name)
            .putBoolean("volumeKeys", s.volumeKeys)
            .putBoolean("haptics", s.haptics)
            .putBoolean("autoReconnect", s.autoReconnect)
            .putInt("showOpenDelayMs", s.showOpenDelayMs)
            .putBoolean("confirmBeforeShow", s.confirmBeforeShow)
            .putString("lastDeviceAddress", s.lastDeviceAddress)
            .apply()
    }
}
