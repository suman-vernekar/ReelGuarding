package com.example.reelguard.overlay

import com.example.reelguard.model.AppUsageData
import com.example.reelguard.model.SupportedApp

class OverlayStateController {

    var isOverlayEnabled: Boolean = true
        private set

    var currentPackage: String? = null
        private set

    var sessionStartMs: Long = 0L
        private set

    var isDismissedForSession: Boolean = false
        private set

    fun setOverlayEnabled(enabled: Boolean) {
        isOverlayEnabled = enabled
    }

    fun onPackageForegrounded(packageName: String, nowMs: Long) {
        if (currentPackage != packageName) {
            currentPackage = packageName
            sessionStartMs = nowMs
            isDismissedForSession = false
        }
    }

    fun onPackageBackgrounded() {
        currentPackage = null
        sessionStartMs = 0L
        isDismissedForSession = false
    }

    fun dismissCurrentSession() {
        isDismissedForSession = true
    }

    /**
     * Determines whether the floating overlay should be shown.
     * When limit is reached, the warning state is persistent and cannot be dismissed through the UI.
     */
    fun shouldDisplayOverlay(isLimitReached: Boolean = false): Boolean {
        if (!isOverlayEnabled || currentPackage == null) return false
        if (isLimitReached) return true
        return !isDismissedForSession
    }

    fun computeUiModel(
        usage: AppUsageData,
        nowMs: Long,
        hasPermission: Boolean = true
    ): OverlayUiModel? {
        val isLimitReached = usage.isLimitReached ||
            (usage.isLimitEnabled && usage.dailyTimeLimitMinutes > 0 && usage.secondsToday >= usage.dailyTimeLimitMinutes * 60L)

        if (!shouldDisplayOverlay(isLimitReached)) return null

        val sessionSeconds = if (sessionStartMs > 0L) {
            ((nowMs - sessionStartMs) / 1000L).coerceAtLeast(0L)
        } else {
            0L
        }

        val supportedApp = SupportedApp.fromPackageName(usage.packageName)
        val appName = supportedApp?.displayName ?: usage.packageName
        val videoType = supportedApp?.shortVideoName ?: "Reels"

        val statusLevel = when {
            isLimitReached -> OverlayStatusLevel.LIMIT_REACHED
            usage.isWarningReached -> OverlayStatusLevel.WARNING
            else -> OverlayStatusLevel.NORMAL
        }

        return OverlayUiModel(
            packageName = usage.packageName,
            appDisplayName = appName,
            videoTypeName = videoType,
            sessionSeconds = sessionSeconds,
            totalSecondsToday = usage.secondsToday,
            reelsCountToday = usage.reelsCountToday,
            dailyLimitMinutes = usage.dailyTimeLimitMinutes,
            dailyReelLimit = usage.dailyReelLimit,
            statusLevel = statusLevel,
            isLimitEnabled = usage.isLimitEnabled,
            hasPermission = hasPermission,
            isDismissedByUser = isDismissedForSession
        )
    }
}
