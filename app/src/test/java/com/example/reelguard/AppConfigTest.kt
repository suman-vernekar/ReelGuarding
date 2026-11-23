package com.example.reelguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {

    @Test
    fun trackedPackages_containsExpectedSocialMediaPackages() {
        val expected = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.facebook.katana"
        )
        assertEquals(expected, AppConfig.trackedPackages)
    }

    @Test
    fun defaultLimits_areReasonablePositiveValues() {
        assertTrue(AppConfig.DEFAULT_DAILY_LIMIT_MINUTES > 0)
        assertTrue(AppConfig.DEFAULT_DAILY_REEL_LIMIT > 0)
        assertEquals(30, AppConfig.DEFAULT_DAILY_LIMIT_MINUTES)
        assertEquals(20, AppConfig.DEFAULT_DAILY_REEL_LIMIT)
    }

    @Test
    fun prefsName_isNotEmpty() {
        assertTrue(AppConfig.PREFS.isNotBlank())
    }
}
