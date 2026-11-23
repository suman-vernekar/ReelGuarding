package com.example.reelguard.model

import com.example.reelguard.AppConfig

enum class TriggerPolicy {
    WHICHEVER_FIRST,
    TIME_ONLY,
    REELS_ONLY,
    BOTH_REQUIRED
}

data class AppLimitConfig(
    val packageName: String,
    val dailyTimeLimitMinutes: Int = AppConfig.DEFAULT_DAILY_LIMIT_MINUTES,
    val dailyReelLimit: Int = AppConfig.DEFAULT_DAILY_REEL_LIMIT,
    val warningThresholdMinutes: Int = 5,
    val warningThresholdReels: Int = 3,
    val isLimitEnabled: Boolean = true,
    val isOverlayEnabled: Boolean = true,
    val isBreakReminderEnabled: Boolean = true,
    val triggerPolicy: TriggerPolicy = TriggerPolicy.WHICHEVER_FIRST
) {
    fun isTimeLimitReached(seconds: Long): Boolean =
        seconds >= dailyTimeLimitMinutes * 60L

    fun isReelLimitReached(reels: Int): Boolean =
        reels >= dailyReelLimit

    fun isTimeWarningReached(seconds: Long): Boolean {
        val remaining = ((dailyTimeLimitMinutes * 60L) - seconds).coerceAtLeast(0L)
        return !isTimeLimitReached(seconds) && remaining <= (warningThresholdMinutes * 60L)
    }

    fun isReelWarningReached(reels: Int): Boolean {
        val remaining = (dailyReelLimit - reels).coerceAtLeast(0)
        return !isReelLimitReached(reels) && remaining <= warningThresholdReels
    }

    fun isLimitReached(seconds: Long, reels: Int): Boolean {
        if (!isLimitEnabled) return false
        val timeReached = isTimeLimitReached(seconds)
        val reelReached = isReelLimitReached(reels)
        return when (triggerPolicy) {
            TriggerPolicy.WHICHEVER_FIRST -> timeReached || reelReached
            TriggerPolicy.TIME_ONLY -> timeReached
            TriggerPolicy.REELS_ONLY -> reelReached
            TriggerPolicy.BOTH_REQUIRED -> timeReached && reelReached
        }
    }

    fun isWarningReached(seconds: Long, reels: Int): Boolean {
        if (!isLimitEnabled) return false
        if (isLimitReached(seconds, reels)) return false
        val timeWarning = isTimeWarningReached(seconds)
        val reelWarning = isReelWarningReached(reels)
        return when (triggerPolicy) {
            TriggerPolicy.WHICHEVER_FIRST -> timeWarning || reelWarning
            TriggerPolicy.TIME_ONLY -> timeWarning
            TriggerPolicy.REELS_ONLY -> reelWarning
            TriggerPolicy.BOTH_REQUIRED -> timeWarning && reelWarning
        }
    }
}
