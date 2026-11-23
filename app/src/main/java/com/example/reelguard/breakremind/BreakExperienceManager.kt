package com.example.reelguard.breakremind

import android.content.Context
import android.content.Intent
import com.example.reelguard.model.SupportedApp

data class BreakSessionState(
    val packageName: String,
    val isSnoozed: Boolean = false,
    val snoozeUntilMs: Long = 0L,
    val isOverriddenForDay: Boolean = false
) {
    fun isSnoozeActive(nowMs: Long): Boolean =
        isSnoozed && nowMs < snoozeUntilMs
}

class BreakExperienceManager {

    private val sessionStates = mutableMapOf<String, BreakSessionState>()

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_VIDEO_TYPE = "extra_video_type"
        const val EXTRA_TIME_USED_SECONDS = "extra_time_used_seconds"
        const val EXTRA_REELS_COUNT = "extra_reels_count"
        const val EXTRA_TIME_LIMIT_MINUTES = "extra_time_limit_minutes"
        const val EXTRA_REEL_LIMIT = "extra_reel_limit"

        const val DEFAULT_SNOOZE_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    }

    fun isEligibleForBreakPrompt(packageName: String, nowMs: Long): Boolean {
        val state = sessionStates[packageName] ?: return true
        if (state.isOverriddenForDay) return false
        if (state.isSnoozeActive(nowMs)) return false
        return true
    }

    fun applySnooze(packageName: String, nowMs: Long, durationMs: Long = DEFAULT_SNOOZE_DURATION_MS) {
        sessionStates[packageName] = BreakSessionState(
            packageName = packageName,
            isSnoozed = true,
            snoozeUntilMs = nowMs + durationMs,
            isOverriddenForDay = false
        )
    }

    fun applyDailyOverride(packageName: String) {
        sessionStates[packageName] = BreakSessionState(
            packageName = packageName,
            isSnoozed = false,
            snoozeUntilMs = 0L,
            isOverriddenForDay = true
        )
    }

    fun resetPackage(packageName: String) {
        sessionStates.remove(packageName)
    }

    fun resetAll() {
        sessionStates.clear()
    }

    fun createBreakIntent(
        context: Context,
        packageName: String,
        timeUsedSeconds: Long,
        reelsCount: Int,
        timeLimitMinutes: Int,
        reelLimit: Int
    ): Intent {
        val supported = SupportedApp.fromPackageName(packageName)
        val appName = supported?.displayName ?: packageName
        val videoType = supported?.shortVideoName ?: "Reels/Shorts"

        return Intent(context, BreakReminderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_APP_NAME, appName)
            putExtra(EXTRA_VIDEO_TYPE, videoType)
            putExtra(EXTRA_TIME_USED_SECONDS, timeUsedSeconds)
            putExtra(EXTRA_REELS_COUNT, reelsCount)
            putExtra(EXTRA_TIME_LIMIT_MINUTES, timeLimitMinutes)
            putExtra(EXTRA_REEL_LIMIT, reelLimit)
        }
    }
}
