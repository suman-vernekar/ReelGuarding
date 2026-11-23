package com.example.reelguard.model

data class AppUsageData(
    val packageName: String,
    val secondsToday: Long = 0L,
    val reelsCountToday: Int = 0,
    val dailyTimeLimitMinutes: Int = 30,
    val dailyReelLimit: Int = 20,
    val warningThresholdMinutes: Int = 5,
    val warningThresholdReels: Int = 3,
    val isLimitEnabled: Boolean = true
) {
    val supportedApp: SupportedApp?
        get() = SupportedApp.fromPackageName(packageName)

    val appDisplayName: String
        get() = supportedApp?.displayName ?: packageName

    val shortVideoName: String
        get() = supportedApp?.shortVideoName ?: "Shorts/Reels"

    val remainingSeconds: Long
        get() = if (isLimitEnabled) ((dailyTimeLimitMinutes * 60L) - secondsToday).coerceAtLeast(0L) else Long.MAX_VALUE

    val remainingReels: Int
        get() = if (isLimitEnabled) (dailyReelLimit - reelsCountToday).coerceAtLeast(0) else Int.MAX_VALUE

    val isTimeLimitReached: Boolean
        get() = isLimitEnabled && dailyTimeLimitMinutes > 0 && secondsToday >= dailyTimeLimitMinutes * 60L

    val isReelLimitReached: Boolean
        get() = isLimitEnabled && dailyReelLimit > 0 && reelsCountToday >= dailyReelLimit

    val isLimitReached: Boolean
        get() = isLimitEnabled && (isTimeLimitReached || isReelLimitReached)

    val isTimeWarningReached: Boolean
        get() = isLimitEnabled && !isTimeLimitReached && remainingSeconds <= (warningThresholdMinutes * 60L)

    val isReelWarningReached: Boolean
        get() = isLimitEnabled && !isReelLimitReached && remainingReels <= warningThresholdReels

    val isWarningReached: Boolean
        get() = isLimitEnabled && !isLimitReached && (isTimeWarningReached || isReelWarningReached)

    val formattedUsageTime: String
        get() = formatDuration(secondsToday)

    val formattedRemainingTime: String
        get() = formatDuration(remainingSeconds)

    val formattedTimeLimit: String
        get() = formatDuration(dailyTimeLimitMinutes * 60L)

    companion object {
        fun formatDuration(totalSeconds: Long): String {
            if (totalSeconds <= 0) return "0s"
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return when {
                hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
                hours > 0 -> "${hours}h"
                minutes > 0 && seconds > 0 -> "${minutes}m ${seconds}s"
                minutes > 0 -> "${minutes}m"
                else -> "${seconds}s"
            }
        }
    }
}
