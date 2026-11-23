package com.example.reelguard.util

import com.example.reelguard.model.AppUsageData

/**
 * Model representing friendly wellbeing feedback with face emojis.
 */
data class EmojiWellbeingStatus(
    val emoji: String,
    val title: String,
    val message: String,
    val percentage: Float,
    val colorHex: String,
    val badgeBgHex: String
)

/**
 * Helper to determine wellbeing face emoji status based on exact consumption thresholds.
 *
 * Daily time limit thresholds:
 * - 0–24%:   😊 Great start        "You're building healthy digital habits."
 * - 25–49%:  🙂 Nicely balanced    "You're keeping your screen time in check."
 * - 50–74%:  😐 Stay mindful       "You've used a good part of your daily allowance."
 * - 75–89%:  😟 Getting close      "You're approaching your daily limit. Consider a break."
 * - 90–99%:  😰 Almost there       "Just a little time remains before your limit."
 * - 100%+:   😔 Limit reached      "You've reached your daily goal. Consider taking a break."
 *
 * Edge cases:
 * - No limit set (limit <= 0):      😊 Set your daily goal
 * - Data unavailable (no perm):     😐 Usage data unavailable
 */
object WellbeingEmojiHelper {

    fun getTimeWellbeingStatus(
        totalSecondsUsed: Long,
        dailyLimitMinutes: Int,
        hasPermission: Boolean
    ): EmojiWellbeingStatus {
        if (!hasPermission) {
            return EmojiWellbeingStatus(
                emoji = "😐",
                title = "Usage data unavailable",
                message = "Grant usage access to view your daily screen time and balance.",
                percentage = 0f,
                colorHex = "#64748B",
                badgeBgHex = "#F1F5F9"
            )
        }

        if (dailyLimitMinutes <= 0) {
            return EmojiWellbeingStatus(
                emoji = "😊",
                title = "Set your daily goal",
                message = "Configure a daily screen time limit to start tracking your digital balance.",
                percentage = 0f,
                colorHex = "#4F46E5",
                badgeBgHex = "#EEF2FF"
            )
        }

        val limitSeconds = dailyLimitMinutes * 60L
        val percentage = (totalSecondsUsed.toFloat() / limitSeconds.toFloat()) * 100f

        return when {
            percentage < 25f -> EmojiWellbeingStatus(
                emoji = "😊",
                title = "Great start",
                message = "You're building healthy digital habits.",
                percentage = percentage,
                colorHex = "#10B981",
                badgeBgHex = "#ECFDF5"
            )
            percentage < 50f -> EmojiWellbeingStatus(
                emoji = "🙂",
                title = "Nicely balanced",
                message = "You're keeping your screen time in check.",
                percentage = percentage,
                colorHex = "#0284C7",
                badgeBgHex = "#F0F9FF"
            )
            percentage < 75f -> EmojiWellbeingStatus(
                emoji = "😐",
                title = "Stay mindful",
                message = "You've used a good part of your daily allowance.",
                percentage = percentage,
                colorHex = "#6366F1",
                badgeBgHex = "#EEF2FF"
            )
            percentage < 90f -> EmojiWellbeingStatus(
                emoji = "😟",
                title = "Getting close",
                message = "You're approaching your daily limit. Consider a break.",
                percentage = percentage,
                colorHex = "#F59E0B",
                badgeBgHex = "#FFFBEB"
            )
            percentage < 100f -> EmojiWellbeingStatus(
                emoji = "😰",
                title = "Almost there",
                message = "Just a little time remains before your limit.",
                percentage = percentage,
                colorHex = "#EA580C",
                badgeBgHex = "#FFF7ED"
            )
            else -> EmojiWellbeingStatus(
                emoji = "😔",
                title = "Limit reached",
                message = "You've reached your daily goal. Consider taking a break.",
                percentage = percentage,
                colorHex = "#EF4444",
                badgeBgHex = "#FEF2F2"
            )
        }
    }

    /**
     * Determines reel count status emoji for individual platform cards.
     * Emojis:
     * - 😊 Within a comfortable range (< 50%)
     * - 🙂 Progressing toward limit (50-74%)
     * - 😐 More than halfway through limit (75-89%)
     * - 😟 Approaching limit (90-99%)
     * - 😔 Limit reached or exceeded (>= 100%)
     * - 😕 Count unavailable or no limit
     */
    fun getReelCountStatus(
        reelsCountToday: Int,
        dailyReelLimit: Int
    ): EmojiWellbeingStatus {
        if (dailyReelLimit <= 0) {
            return EmojiWellbeingStatus(
                emoji = "😕",
                title = "No limit set",
                message = "Set a video limit to track your pace.",
                percentage = 0f,
                colorHex = "#64748B",
                badgeBgHex = "#F1F5F9"
            )
        }

        val percentage = (reelsCountToday.toFloat() / dailyReelLimit.toFloat()) * 100f

        return when {
            percentage < 50f -> EmojiWellbeingStatus(
                emoji = "😊",
                title = "Comfortable",
                message = "Within a comfortable viewing range.",
                percentage = percentage,
                colorHex = "#10B981",
                badgeBgHex = "#ECFDF5"
            )
            percentage < 75f -> EmojiWellbeingStatus(
                emoji = "🙂",
                title = "Progressing",
                message = "Progressing toward your limit.",
                percentage = percentage,
                colorHex = "#0284C7",
                badgeBgHex = "#F0F9FF"
            )
            percentage < 90f -> EmojiWellbeingStatus(
                emoji = "😐",
                title = "Halfway",
                message = "More than halfway through your limit.",
                percentage = percentage,
                colorHex = "#6366F1",
                badgeBgHex = "#EEF2FF"
            )
            percentage < 100f -> EmojiWellbeingStatus(
                emoji = "😟",
                title = "Approaching",
                message = "Approaching your daily video limit.",
                percentage = percentage,
                colorHex = "#F59E0B",
                badgeBgHex = "#FFFBEB"
            )
            else -> EmojiWellbeingStatus(
                emoji = "😔",
                title = "Limit reached",
                message = "Daily video limit reached.",
                percentage = percentage,
                colorHex = "#EF4444",
                badgeBgHex = "#FEF2F2"
            )
        }
    }
}
