package com.example.reelguard.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUsageDataTest {

    @Test
    fun defaultCalculations_withZeroUsage() {
        val usage = AppUsageData(
            packageName = "com.instagram.android",
            secondsToday = 0L,
            reelsCountToday = 0,
            dailyTimeLimitMinutes = 30,
            dailyReelLimit = 20,
            warningThresholdMinutes = 5,
            warningThresholdReels = 3
        )

        assertEquals(1800L, usage.remainingSeconds)
        assertEquals(20, usage.remainingReels)
        assertFalse(usage.isTimeLimitReached)
        assertFalse(usage.isReelLimitReached)
        assertFalse(usage.isLimitReached)
        assertFalse(usage.isWarningReached)
    }

    @Test
    fun timeLimitReached_whenSecondsExceedLimit() {
        val usage = AppUsageData(
            packageName = "com.instagram.android",
            secondsToday = 1800L, // 30 mins
            reelsCountToday = 5,
            dailyTimeLimitMinutes = 30,
            dailyReelLimit = 20
        )

        assertTrue(usage.isTimeLimitReached)
        assertFalse(usage.isReelLimitReached)
        assertTrue(usage.isLimitReached)
        assertEquals(0L, usage.remainingSeconds)
        // Since limit is reached, warning should be false
        assertFalse(usage.isWarningReached)
    }

    @Test
    fun reelLimitReached_whenReelCountExceedsLimit() {
        val usage = AppUsageData(
            packageName = "com.google.android.youtube",
            secondsToday = 600L, // 10 mins
            reelsCountToday = 20,
            dailyTimeLimitMinutes = 30,
            dailyReelLimit = 20
        )

        assertFalse(usage.isTimeLimitReached)
        assertTrue(usage.isReelLimitReached)
        assertTrue(usage.isLimitReached)
        assertEquals(0, usage.remainingReels)
        assertFalse(usage.isWarningReached)
    }

    @Test
    fun warningState_whenTimeWithinThreshold() {
        // Limit 30m (1800s), warning at 5m (300s). At 26m (1560s), remaining is 4m (240s <= 300s)
        val usage = AppUsageData(
            packageName = "com.instagram.android",
            secondsToday = 1560L,
            reelsCountToday = 5,
            dailyTimeLimitMinutes = 30,
            dailyReelLimit = 20,
            warningThresholdMinutes = 5,
            warningThresholdReels = 3
        )

        assertFalse(usage.isLimitReached)
        assertTrue(usage.isTimeWarningReached)
        assertFalse(usage.isReelWarningReached)
        assertTrue(usage.isWarningReached)
    }

    @Test
    fun warningState_whenReelsWithinThreshold() {
        // Limit 20, warning at 3 reels. At 18 reels, remaining is 2 <= 3
        val usage = AppUsageData(
            packageName = "com.facebook.katana",
            secondsToday = 600L,
            reelsCountToday = 18,
            dailyTimeLimitMinutes = 30,
            dailyReelLimit = 20,
            warningThresholdMinutes = 5,
            warningThresholdReels = 3
        )

        assertFalse(usage.isLimitReached)
        assertFalse(usage.isTimeWarningReached)
        assertTrue(usage.isReelWarningReached)
        assertTrue(usage.isWarningReached)
    }

    @Test
    fun formatDuration_producesExpectedStrings() {
        assertEquals("0s", AppUsageData.formatDuration(0L))
        assertEquals("45s", AppUsageData.formatDuration(45L))
        assertEquals("5m", AppUsageData.formatDuration(300L))
        assertEquals("5m 30s", AppUsageData.formatDuration(330L))
        assertEquals("1h", AppUsageData.formatDuration(3600L))
        assertEquals("1h 15m", AppUsageData.formatDuration(4500L))
    }

    @Test
    fun supportedAppMetadata_bindsCorrectly() {
        val insta = AppUsageData(packageName = "com.instagram.android")
        assertEquals("Instagram", insta.appDisplayName)
        assertEquals("Reels", insta.shortVideoName)

        val yt = AppUsageData(packageName = "com.google.android.youtube")
        assertEquals("YouTube", yt.appDisplayName)
        assertEquals("Shorts", yt.shortVideoName)
    }
}
