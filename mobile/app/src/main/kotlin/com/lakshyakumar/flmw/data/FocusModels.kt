package com.lakshyakumar.flmw.data

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

@Serializable
data class FocusSchedule(
    val id: String,
    val label: String,
    val hour: Int,
    val minute: Int,
    val durationMinutes: Int,
    val daysOfWeek: List<Int>, // 1 = Mon ... 7 = Sun
    val enabled: Boolean = true
)

@Serializable
data class FocusState(
    val active: Boolean = false,
    val indefinite: Boolean = false,
    val startedAtMillis: Long? = null,
    val endsAtMillis: Long? = null,
    val totalDurationMillis: Long = 0L,
    val focusedMinutesToday: Int = 0,
    val strictFriction: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val blockedSites: List<String> = listOf(
        "x.com",
        "reddit.com",
        "youtube.com",
        "instagram.com",
        "news.ycombinator.com"
    ),
    val blockedPackages: List<String> = listOf(
        "com.instagram.android",
        "com.twitter.android",
        "com.reddit.frontpage",
        "com.google.android.youtube"
    ),
    val schedules: List<FocusSchedule> = listOf(
        FocusSchedule(
            id = "sch-morning",
            label = "Weekday morning deep work",
            hour = 9,
            minute = 0,
            durationMinutes = 180,
            daysOfWeek = listOf(1, 2, 3, 4, 5),
            enabled = true
        ),
        FocusSchedule(
            id = "sch-afternoon",
            label = "Afternoon build block",
            hour = 14,
            minute = 0,
            durationMinutes = 120,
            daysOfWeek = listOf(1, 2, 3, 4, 5),
            enabled = true
        )
    )
) {
    fun isEffectivelyActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!active) return false
        if (indefinite) return true
        val end = endsAtMillis ?: return false
        return nowMillis < end
    }
}

data class InstalledAppItem(
    val packageName: String,
    val label: String
)
