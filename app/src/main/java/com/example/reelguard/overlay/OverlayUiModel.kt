package com.example.reelguard.overlay

import com.example.reelguard.model.AppUsageData

enum class OverlayStatusLevel {
    NORMAL,
    WARNING,
    LIMIT_REACHED
}

data class OverlayUiModel(
    val packageName: String,
    val appDisplayName: String,
    val videoTypeName: String,
    val sessionSeconds: Long,
    val totalSecondsToday: Long,
    val reelsCountToday: Int,
    val dailyLimitMinutes: Int,
    val dailyReelLimit: Int,
    val statusLevel: OverlayStatusLevel,
    val isLimitEnabled: Boolean = true,
    val hasPermission: Boolean = true,
    val isDismissedByUser: Boolean = false
) {
    val sessionDurationText: String
        get() = AppUsageData.formatDuration(sessionSeconds)

    val usageProgressText: String
        get() = "${AppUsageData.formatDuration(totalSecondsToday)} / ${AppUsageData.formatDuration(dailyLimitMinutes * 60L)}"

    val reelsProgressText: String
        get() = "$reelsCountToday / $dailyReelLimit $videoTypeName"

    val remainingSeconds: Long
        get() = if (isLimitEnabled && dailyLimitMinutes > 0) {
            ((dailyLimitMinutes * 60L) - totalSecondsToday).coerceAtLeast(0L)
        } else {
            0L
        }

    val remainingMinutes: Long
        get() = remainingSeconds / 60L

    val remainingText: String
        get() = AppUsageData.formatDuration(remainingSeconds)

    val remainingFormattedText: String
        get() {
            if (!hasPermission) return "Usage data unavailable"
            if (!isLimitEnabled || dailyLimitMinutes <= 0) return "Set a daily limit in ReelGuard"
            if (statusLevel == OverlayStatusLevel.LIMIT_REACHED || remainingSeconds <= 0L) {
                return "Daily limit reached"
            }
            val mins = remainingMinutes
            val secs = remainingSeconds % 60L
            return when {
                mins > 0L -> "$mins minute${if (mins != 1L) "s" else ""}"
                else -> "$secs second${if (secs != 1L) "s" else ""}"
            }
        }

    val faceEmoji: String
        get() {
            if (!hasPermission) return "😕"
            if (!isLimitEnabled || dailyLimitMinutes <= 0) return "😊"
            if (statusLevel == OverlayStatusLevel.LIMIT_REACHED || remainingSeconds <= 0L) return "😔"
            val totalAllowedSec = dailyLimitMinutes * 60L
            val pctUsed = (totalSecondsToday.toFloat() / totalAllowedSec.toFloat()) * 100f
            val remMins = remainingMinutes

            return when {
                remMins <= 2L -> "😰"                     // Very little time remains (<= 2m)
                remMins <= 5L || pctUsed >= 75f -> "😟"   // Approaching the limit (<= 5m or >= 75%)
                pctUsed >= 50f -> "😐"                     // Halfway through allowance (50-74%)
                pctUsed >= 25f -> "🙂"                     // Time is available (25-49%)
                else -> "😊"                              // Plenty of time remaining (< 25%)
            }
        }

    val titleText: String
        get() = "$faceEmoji $appDisplayName"

    val subtitleText: String
        get() = when {
            !hasPermission -> "Usage data unavailable"
            !isLimitEnabled || dailyLimitMinutes <= 0 -> "Set a daily limit in ReelGuard"
            statusLevel == OverlayStatusLevel.LIMIT_REACHED || remainingSeconds <= 0L ->
                "Daily limit reached • Take a break if you can."
            else -> "$remainingFormattedText remaining"
        }

    val fullDisplayText: String
        get() = when {
            !hasPermission ->
                "$faceEmoji $appDisplayName • 😕 Usage data unavailable"
            !isLimitEnabled || dailyLimitMinutes <= 0 ->
                "$faceEmoji $appDisplayName • 😊 Set a daily limit in ReelGuard"
            statusLevel == OverlayStatusLevel.LIMIT_REACHED || remainingSeconds <= 0L ->
                "$faceEmoji $appDisplayName • 🛑 Limit Reached • $usageProgressText"
            statusLevel == OverlayStatusLevel.WARNING ->
                "$faceEmoji $appDisplayName • ⚠️ $remainingFormattedText left • $usageProgressText"
            else ->
                "$faceEmoji $appDisplayName • $remainingFormattedText remaining • $sessionDurationText • $usageProgressText"
        }
}
