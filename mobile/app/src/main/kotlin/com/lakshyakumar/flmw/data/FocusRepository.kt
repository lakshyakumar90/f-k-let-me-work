package com.lakshyakumar.flmw.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

private val Context.flmwDataStore: DataStore<Preferences> by preferencesDataStore(name = "flmw_prefs")

class FocusRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val stateKey = stringPreferencesKey("focus_state_json")

    val focusState: StateFlow<FocusState> = context.flmwDataStore.data
        .map { prefs ->
            val raw = prefs[stateKey]
            if (raw.isNullOrBlank()) {
                FocusState()
            } else {
                runCatching { json.decodeFromString<FocusState>(raw) }.getOrDefault(FocusState())
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = FocusState()
        )

    private suspend fun updateState(transform: (FocusState) -> FocusState) {
        context.flmwDataStore.edit { prefs ->
            val current = prefs[stateKey]?.let {
                runCatching { json.decodeFromString<FocusState>(it) }.getOrNull()
            } ?: FocusState()
            val updated = transform(current)
            prefs[stateKey] = json.encodeToString(FocusState.serializer(), updated)
        }
    }

    suspend fun startFocus(durationMinutes: Int?) {
        val now = System.currentTimeMillis()
        updateState { state ->
            if (durationMinutes == null || durationMinutes <= 0) {
                state.copy(
                    active = true,
                    indefinite = true,
                    startedAtMillis = now,
                    endsAtMillis = null,
                    totalDurationMillis = 0L
                )
            } else {
                val totalMs = durationMinutes * 60_000L
                state.copy(
                    active = true,
                    indefinite = false,
                    startedAtMillis = now,
                    endsAtMillis = now + totalMs,
                    totalDurationMillis = totalMs
                )
            }
        }
    }

    suspend fun extendFocus(extraMinutes: Int) {
        val addMs = extraMinutes * 60_000L
        updateState { state ->
            if (!state.active || state.indefinite || state.endsAtMillis == null) return@updateState state
            state.copy(
                endsAtMillis = state.endsAtMillis + addMs,
                totalDurationMillis = state.totalDurationMillis + addMs
            )
        }
    }

    suspend fun stopFocus() {
        val now = System.currentTimeMillis()
        updateState { state ->
            val elapsedMin = if (state.active && state.startedAtMillis != null) {
                ((now - state.startedAtMillis) / 60_000L).toInt().coerceAtLeast(1)
            } else {
                0
            }
            state.copy(
                active = false,
                indefinite = false,
                startedAtMillis = null,
                endsAtMillis = null,
                totalDurationMillis = 0L,
                focusedMinutesToday = state.focusedMinutesToday + elapsedMin
            )
        }
    }

    suspend fun setStrictFriction(enabled: Boolean) {
        updateState { it.copy(strictFriction = enabled) }
    }

    suspend fun toggleTheme(systemDark: Boolean) {
        updateState { state ->
            val next = when (state.themeMode) {
                ThemeMode.SYSTEM -> if (systemDark) ThemeMode.LIGHT else ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
            }
            state.copy(themeMode = next)
        }
    }

    suspend fun addSite(rawDomain: String) {
        val clean = normalizeDomain(rawDomain)
        if (clean.isBlank() || !clean.contains('.')) return
        updateState { state ->
            if (state.blockedSites.contains(clean)) state
            else state.copy(blockedSites = listOf(clean) + state.blockedSites)
        }
    }

    suspend fun removeSite(domain: String) {
        updateState { state ->
            state.copy(blockedSites = state.blockedSites.filterNot { it == domain })
        }
    }

    suspend fun togglePackage(packageName: String) {
        updateState { state ->
            val exists = state.blockedPackages.contains(packageName)
            val next = if (exists) {
                state.blockedPackages - packageName
            } else {
                listOf(packageName) + state.blockedPackages
            }
            state.copy(blockedPackages = next)
        }
    }

    suspend fun addSchedule(label: String, hour: Int, minute: Int, durationMinutes: Int, days: List<Int>) {
        if (durationMinutes <= 0 || days.isEmpty()) return
        val newItem = FocusSchedule(
            id = "sch-${System.currentTimeMillis()}",
            label = label.ifBlank { "Focus window" },
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            durationMinutes = durationMinutes,
            daysOfWeek = days.sorted(),
            enabled = true
        )
        updateState { state ->
            state.copy(schedules = listOf(newItem) + state.schedules)
        }
    }

    suspend fun toggleSchedule(id: String) {
        updateState { state ->
            state.copy(
                schedules = state.schedules.map {
                    if (it.id == id) it.copy(enabled = !it.enabled) else it
                }
            )
        }
    }

    suspend fun removeSchedule(id: String) {
        updateState { state ->
            state.copy(schedules = state.schedules.filterNot { it.id == id })
        }
    }

    companion object {
        fun normalizeDomain(raw: String): String {
            return raw.trim()
                .lowercase()
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .substringBefore('/')
                .filter { it.isLetterOrDigit() || it == '.' || it == '-' }
        }
    }
}
