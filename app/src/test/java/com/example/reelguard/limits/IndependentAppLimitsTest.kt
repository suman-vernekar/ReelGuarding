package com.example.reelguard.limits

import com.example.reelguard.model.AppLimitConfig
import com.example.reelguard.model.AppUsageData
import com.example.reelguard.model.SupportedApp
import com.example.reelguard.overlay.OverlayStatusLevel
import com.example.reelguard.overlay.OverlayUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndependentAppLimitsTest {

    @Test
    fun independentLimits_canBeConfiguredSeparately() {
        val instaConfig = AppLimitConfig(
            packageName = SupportedApp.INSTAGRAM.packageName,
            dailyTimeLimitMinutes = 30,
            isLimitEnabled = true
        )
        val ytConfig = AppLimitConfig(
            packageName = SupportedApp.YOUTUBE.packageName,
            dailyTimeLimitMinutes = 60,
            isLimitEnabled = true
        )
        val fbConfig = AppLimitConfig(
            packageName = SupportedApp.FACEBOOK.packageName,
            dailyTimeLimitMinutes = 45,
            isLimitEnabled = true
        )

        assertEquals(30, instaConfig.dailyTimeLimitMinutes)
        assertEquals(60, ytConfig.dailyTimeLimitMinutes)
        assertEquals(45, fbConfig.dailyTimeLimitMinutes)

        // Remaining times with 15 mins (900s) consumed
        val instaUsage = AppUsageData(
            packageName = SupportedApp.INSTAGRAM.packageName,
            secondsToday = 900L,
            dailyTimeLimitMinutes = instaConfig.dailyTimeLimitMinutes,
            isLimitEnabled = instaConfig.isLimitEnabled
        )
        val ytUsage = AppUsageData(
            packageName = SupportedApp.YOUTUBE.packageName,
            secondsToday = 900L,
            dailyTimeLimitMinutes = ytConfig.dailyTimeLimitMinutes,
            isLimitEnabled = ytConfig.isLimitEnabled
        )
        val fbUsage = AppUsageData(
            packageName = SupportedApp.FACEBOOK.packageName,
            secondsToday = 900L,
            dailyTimeLimitMinutes = fbConfig.dailyTimeLimitMinutes,
            isLimitEnabled = fbConfig.isLimitEnabled
        )

        assertEquals(900L, instaUsage.remainingSeconds) // 1800 - 900 = 900s (15m left)
        assertEquals(2700L, ytUsage.remainingSeconds)  // 3600 - 900 = 2700s (45m left)
        assertEquals(1800L, fbUsage.remainingSeconds)  // 2700 - 900 = 1800s (30m left)
    }

    @Test
    fun independentLimits_modifyingOneDoesNotAffectOthers() {
        var instaLimit = 30
        val ytLimit = 60
        val fbLimit = 45

        // Modify Instagram limit only
        instaLimit = 15

        assertEquals(15, instaLimit)
        assertEquals(60, ytLimit)
        assertEquals(45, fbLimit)
    }

    @Test
    fun independentLimits_disablingOneDoesNotAffectOthers() {
        val instaConfig = AppLimitConfig(
            packageName = SupportedApp.INSTAGRAM.packageName,
            dailyTimeLimitMinutes = 30,
            isLimitEnabled = false // Disabled
        )
        val ytConfig = AppLimitConfig(
            packageName = SupportedApp.YOUTUBE.packageName,
            dailyTimeLimitMinutes = 60,
            isLimitEnabled = true // Enabled
        )

        assertFalse(instaConfig.isLimitEnabled)
        assertTrue(ytConfig.isLimitEnabled)

        // When Instagram consumed 3600s (> 30 min limit), but limit is disabled
        assertFalse(instaConfig.isLimitReached(seconds = 3600L, reels = 50))
        assertFalse(instaConfig.isWarningReached(seconds = 1600L, reels = 18))

        // When YouTube consumed 3600s (= 60 min limit), limit is enabled so it triggers
        assertTrue(ytConfig.isLimitReached(seconds = 3600L, reels = 10))
    }

    @Test
    fun appUsageData_respectsLimitEnabledFlag() {
        val disabledUsage = AppUsageData(
            packageName = SupportedApp.INSTAGRAM.packageName,
            secondsToday = 2000L,
            dailyTimeLimitMinutes = 30,
            isLimitEnabled = false
        )
        assertFalse(disabledUsage.isLimitReached)
        assertFalse(disabledUsage.isTimeLimitReached)
        assertFalse(disabledUsage.isWarningReached)

        val enabledUsage = AppUsageData(
            packageName = SupportedApp.INSTAGRAM.packageName,
            secondsToday = 2000L,
            dailyTimeLimitMinutes = 30,
            isLimitEnabled = true
        )
        assertTrue(enabledUsage.isLimitReached)
        assertTrue(enabledUsage.isTimeLimitReached)
    }

    @Test
    fun noReelsOrShortsInOverlayDisplayText() {
        val normalModel = OverlayUiModel(
            packageName = "com.instagram.android",
            appDisplayName = "Instagram",
            videoTypeName = "Reels",
            sessionSeconds = 45L,
            totalSecondsToday = 600L,
            reelsCountToday = 5,
            dailyLimitMinutes = 30,
            dailyReelLimit = 20,
            statusLevel = OverlayStatusLevel.NORMAL
        )
        assertFalse(normalModel.fullDisplayText.contains("Reel"))
        assertFalse(normalModel.fullDisplayText.contains("Short"))
        assertTrue(normalModel.fullDisplayText.contains("Instagram"))
        assertTrue(normalModel.fullDisplayText.contains("45s"))

        val warningModel = normalModel.copy(statusLevel = OverlayStatusLevel.WARNING)
        assertFalse(warningModel.fullDisplayText.contains("Reel"))
        assertFalse(warningModel.fullDisplayText.contains("Short"))
        assertTrue(warningModel.fullDisplayText.contains("⚠️"))

        val reachedModel = normalModel.copy(statusLevel = OverlayStatusLevel.LIMIT_REACHED)
        assertFalse(reachedModel.fullDisplayText.contains("Reel"))
        assertFalse(reachedModel.fullDisplayText.contains("Short"))
        assertTrue(reachedModel.fullDisplayText.contains("🛑 Limit Reached"))
    }
}
