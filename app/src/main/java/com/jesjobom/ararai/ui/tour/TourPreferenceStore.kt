package com.jesjobom.ararai.ui.tour

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal enum class ScreenTour(
    val storageId: String,
    val version: Int = 1,
) {
    Chat("chat", version = 2),
    VoiceChat("voice_chat"),
    ModelManagement("model_management"),
    AssistantConfiguration("assistant_configuration"),
}

internal interface TourPreferenceStore {
    val revision: StateFlow<Int>

    fun isTerminal(tourId: String, version: Int): Boolean

    fun markTerminal(tourId: String, version: Int)

    fun restoreAll()
}

internal fun TourPreferenceStore.isTerminal(tour: ScreenTour): Boolean = isTerminal(tour.storageId, tour.version)

internal fun TourPreferenceStore.markTerminal(tour: ScreenTour) {
    markTerminal(tour.storageId, tour.version)
}

internal class InMemoryTourPreferenceStore(
    initialTerminalKeys: Set<String> = emptySet(),
) : TourPreferenceStore {
    private val terminalKeys = initialTerminalKeys.toMutableSet()
    private val mutableRevision = MutableStateFlow(0)

    override val revision: StateFlow<Int> = mutableRevision

    override fun isTerminal(tourId: String, version: Int): Boolean = terminalKey(tourId, version) in terminalKeys

    override fun markTerminal(tourId: String, version: Int) {
        if (terminalKeys.add(terminalKey(tourId, version))) mutableRevision.value += 1
    }

    override fun restoreAll() {
        if (terminalKeys.isEmpty()) return
        terminalKeys.clear()
        mutableRevision.value += 1
    }
}

internal class SharedPreferencesTourPreferenceStore(
    context: Context,
) : TourPreferenceStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableRevision = MutableStateFlow(0)

    override val revision: StateFlow<Int> = mutableRevision

    override fun isTerminal(tourId: String, version: Int): Boolean = terminalKey(tourId, version) in terminalKeys()

    override fun markTerminal(tourId: String, version: Int) {
        val updated = terminalKeys() + terminalKey(tourId, version)
        if (updated == terminalKeys()) return
        preferences.edit().putStringSet(KEY_TERMINAL_TOURS, updated).apply()
        mutableRevision.value += 1
    }

    override fun restoreAll() {
        if (terminalKeys().isEmpty()) return
        preferences.edit().remove(KEY_TERMINAL_TOURS).apply()
        mutableRevision.value += 1
    }

    private fun terminalKeys(): Set<String> = preferences.getStringSet(KEY_TERMINAL_TOURS, emptySet()).orEmpty().toSet()

    private companion object {
        const val PREFERENCES_NAME = "screen_tour_preferences"
        const val KEY_TERMINAL_TOURS = "terminal_tours"
    }
}

private fun terminalKey(tourId: String, version: Int): String = "$tourId:$version"
